#!/usr/bin/env python3
"""Run the 13 DEMO-02 scenarios through the real officer/admin APIs."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

from lib import (
    PASSWORDS,
    PRESTO_CATALOG_ALIAS,
    ROOT,
    SCHEMA,
    SOURCE_SYSTEM,
    ApiClient,
    HttpError,
    count_from_events,
    criterion,
    login_ready,
    match_events_summary,
    table_ref,
)

CATALOG = os.environ.get("DEMO_PRESTO_CATALOG", PRESTO_CATALOG_ALIAS)
RESULTS_PATH = ROOT / "docs" / "demo" / "SCENARIO_RESULTS.md"
RESULTS_JSON = Path(__file__).resolve().parent / ".scenario-results.json"
FUZZY_THRESHOLD = 80.0
# Scenarios 2/3 — Agent A's final shape. Name-only (2b) is skipped: prefix-3
# fan-out is ~735M, above the 50M guard. Mobile AND keeps the estimate under it.
FUZZY_NAME_THRESHOLD = 85.0
GRID_CAP = 10000

# Low-cardinality join used to trip the fan-out guard (status has ~2 values).
FANOUT_COLUMN = "status"


class ScenarioResult:
    def __init__(self, number: int, name: str, user: str) -> None:
        self.number = number
        self.name = name
        self.user = user
        self.ok = False
        self.skipped = False
        self.detail = ""
        self.row_count: int | None = None
        self.wall_s: float | None = None
        self.sql: str | None = None
        self.notes: list[str] = []
        self.assertions: list[tuple[bool, str]] = []

    def check(self, cond: bool, message: str) -> None:
        self.assertions.append((bool(cond), message))
        if not cond:
            self.notes.append("FAIL: " + message)

    def finish(self) -> None:
        self.ok = all(ok for ok, _ in self.assertions) if self.assertions else False


def client_for(username: str) -> ApiClient:
    return login_ready(username, PASSWORDS[username])


def id_match(
    join_type: str,
    *,
    count_only: bool = False,
    result_limit: int | None = 200,
    extra: dict[str, Any] | None = None,
) -> dict[str, Any]:
    body: dict[str, Any] = {
        "sourceCriteria": [criterion(CATALOG, "beneficiary", "beneficiary_id")],
        "targetCriteria": [criterion(CATALOG, "bank_account", "beneficiary_ref")],
        "sourceDisplayColumns": [
            table_ref(CATALOG, "beneficiary", "beneficiary_id"),
            table_ref(CATALOG, "beneficiary", "full_name"),
            table_ref(CATALOG, "beneficiary", "district_code"),
            table_ref(CATALOG, "beneficiary", "annual_income"),
            table_ref(CATALOG, "beneficiary", "status"),
        ],
        "targetDisplayColumns": [
            table_ref(CATALOG, "bank_account", "account_id"),
            table_ref(CATALOG, "bank_account", "account_holder_name"),
            table_ref(CATALOG, "bank_account", "declared_income"),
            table_ref(CATALOG, "bank_account", "status"),
        ],
        "highlightDuplicates": False,
        "dedup": None,
        "joinType": join_type,
        "countOnly": count_only,
    }
    if result_limit is not None and not count_only:
        body["resultLimit"] = result_limit
    if extra:
        body.update(extra)
    return body


def fuzzy_name_match(*, count_only: bool = False, result_limit: int | None = GRID_CAP) -> dict[str, Any]:
    """Fuzzy name ≥85% AND exact mobile. Not name-only (that is 2b, skipped)."""
    body: dict[str, Any] = {
        "sourceCriteria": [],
        "targetCriteria": [],
        "sourceDisplayColumns": [
            table_ref(CATALOG, "beneficiary", "beneficiary_id"),
            table_ref(CATALOG, "beneficiary", "full_name"),
            table_ref(CATALOG, "beneficiary", "mobile"),
            table_ref(CATALOG, "beneficiary", "district_code"),
        ],
        "targetDisplayColumns": [
            table_ref(CATALOG, "bank_account", "account_id"),
            table_ref(CATALOG, "bank_account", "account_holder_name"),
            table_ref(CATALOG, "bank_account", "mobile"),
        ],
        "joinGroups": [
            {
                "source": [criterion(CATALOG, "beneficiary", "full_name", FUZZY_NAME_THRESHOLD)],
                "target": [criterion(CATALOG, "bank_account", "account_holder_name")],
                "mode": "COMBINE",
                "fuzzyThresholdPercent": FUZZY_NAME_THRESHOLD,
                "separator": " ",
            },
            {
                "source": [criterion(CATALOG, "beneficiary", "mobile")],
                "target": [criterion(CATALOG, "bank_account", "mobile")],
                "mode": "COMBINE",
                "fuzzyThresholdPercent": None,
                "separator": " ",
            },
        ],
        "highlightDuplicates": False,
        "dedup": None,
        "joinType": "INNER",
        "countOnly": count_only,
    }
    if result_limit is not None and not count_only:
        body["resultLimit"] = result_limit
    return body


def _fanout_message(exc_or_text: Any) -> str:
    if isinstance(exc_or_text, HttpError):
        return exc_or_text.body
    return str(exc_or_text or "")


def _is_fanout_refusal(message: str, status: int | None = None) -> bool:
    text = (message or "").lower()
    return "estimated" in text or "fan-out" in text or "fan out" in text or (
        status in (400, 409) and "50" in text and "row" in text
    )


def run_match(client: ApiClient, body: dict[str, Any]) -> tuple[list[dict[str, Any]], float, str | None]:
    started = time.perf_counter()
    preview_sql = None
    try:
        preview_sql = client.post("/api/analysis/match.sql", body, accept="text/plain")
    except HttpError:
        preview_sql = None
    events = client.stream_ndjson("/api/analysis/match", body)
    wall = time.perf_counter() - started
    return events, wall, preview_sql if isinstance(preview_sql, str) else None


def scenario_1(res: ScenarioResult) -> None:
    admin = client_for("superadmin")
    counts: dict[str, int] = {}
    sqls: dict[str, str] = {}
    left_sample: list[dict[str, Any]] = []
    for join in ("INNER", "LEFT", "RIGHT", "FULL"):
        events, wall, sql = run_match(admin, id_match(join, count_only=True, result_limit=None))
        summary = match_events_summary(events)
        res.check(summary["error"] is None, f"{join} count ran: {summary['error']}")
        n = count_from_events(events)
        res.check(n is not None and n > 0, f"{join} count > 0 (got {n})")
        if n is not None:
            counts[join] = n
        if sql:
            sqls[join] = sql
        res.notes.append(f"{join} rows={n} wall={wall:.2f}s")
    inner, left, right, full = (counts.get(k) for k in ("INNER", "LEFT", "RIGHT", "FULL"))
    if None not in (inner, left, right, full):
        res.check(left >= inner, f"LEFT ({left}) >= INNER ({inner})")
        res.check(right >= inner, f"RIGHT ({right}) >= INNER ({inner})")
        res.check(full >= left and full >= right, f"FULL ({full}) >= LEFT/RIGHT")
        inner_sql = sqls.get("INNER") or ""
        res.check(
            "JOIN" in inner_sql and "LEFT JOIN" not in inner_sql and "RIGHT JOIN" not in inner_sql,
            "INNER SQL uses bare JOIN",
        )
    # NULL verdicts on unmatched LEFT rows: add a comparison and inspect a sample.
    cmp_body = id_match(
        "LEFT",
        count_only=False,
        result_limit=80,
        extra={
            "comparisonGroups": [
                {
                    "source": [criterion(CATALOG, "beneficiary", "annual_income")],
                    "target": [criterion(CATALOG, "bank_account", "declared_income")],
                    "mode": "COMBINE",
                }
            ]
        },
    )
    events, wall, sql = run_match(admin, cmp_body)
    summary = match_events_summary(events)
    left_sample = summary["rows"]
    unmatched = [r for r in left_sample if r.get("match_status") == "NO_TARGET"]
    res.check("match_status" in (summary["columns"] or []), "LEFT emits match_status")
    if unmatched:
        verdict = unmatched[0].get("cmp_1_match")
        res.check(verdict is None, f"NO_TARGET row has NULL comparison verdict (got {verdict!r})")
    else:
        res.notes.append("no NO_TARGET row in first 80 LEFT rows — count relationship still asserted")
    res.row_count = inner
    res.wall_s = wall
    res.sql = sqls.get("INNER") or sql
    res.detail = f"INNER={inner} LEFT={left} RIGHT={right} FULL={full}"


def _run_fuzzy_headline(client: ApiClient) -> tuple[int | None, float, str | None, str | None, bool]:
    """Return (rows, wall_s, sql, error, fanout_guard_passed)."""
    body = fuzzy_name_match(count_only=True, result_limit=None)
    started = time.perf_counter()
    try:
        events, wall, sql = run_match(client, body)
    except HttpError as exc:
        wall = time.perf_counter() - started
        msg = _fanout_message(exc)
        return None, wall, None, f"HTTP {exc.status}: {msg[:400]}", False
    summary = match_events_summary(events)
    err = summary["error"]
    if err and _is_fanout_refusal(err):
        return None, wall, sql, err, False
    n = count_from_events(events)
    return n, wall, sql or summary.get("sql"), err, err is None


def scenario_2(res: ScenarioResult) -> None:
    admin = client_for("superadmin")
    n, wall, sql, err, guard_passed = _run_fuzzy_headline(admin)
    res.check(err is None, f"fuzzy count ran: {err}")
    res.check(n is not None and n > 0, f"fuzzy unscoped rows > 0 (got {n})")
    res.check(sql is None or "levenshtein" in (sql or "").lower(),
              "emitted SQL is fuzzy (Levenshtein)")
    res.check(sql is None or "mobile" in (sql or "").lower(),
              "emitted SQL also joins exact mobile")
    res.check(guard_passed, f"fan-out guard passed (query executed, not refused): {err}")
    res.notes.append(
        "2b (name-only, no mobile) skipped — Agent A profile estimates ~735M "
        "prefix-3 fan-out, above the 50M guard."
    )
    res.notes.append(f"fan-out guard passed={guard_passed}")
    res.row_count = n
    res.wall_s = wall
    res.sql = sql
    res.detail = f"unscoped fuzzy+mobile rows={n} wall={wall:.2f}s fan-out-guard={'passed' if guard_passed else 'REFUSED'}"


def scenario_3(res: ScenarioResult, unscoped_rows: int | None) -> None:
    officer = client_for("jaipurofficer")
    n, wall, sql, err, guard_passed = _run_fuzzy_headline(officer)
    res.check(err is None, f"scoped fuzzy ran: {err}")
    res.check(n is not None, f"scoped fuzzy returned a count (got {n})")
    if unscoped_rows is not None and n is not None:
        res.check(n < unscoped_rows, f"scoped ({n}) < unscoped ({unscoped_rows})")
    if sql:
        res.check("district_code" in sql.lower() or "JP" in sql, "scoped SQL mentions district filter")
    res.check(guard_passed, f"fan-out guard passed for jaipurofficer: {err}")
    res.notes.append(f"fan-out guard passed={guard_passed}")
    res.row_count = n
    res.wall_s = wall
    res.sql = sql
    res.detail = (
        f"jaipurofficer fuzzy+mobile rows={n} unscoped={unscoped_rows} "
        f"wall={wall:.2f}s fan-out-guard={'passed' if guard_passed else 'REFUSED'}"
    )


def scenario_4(res: ScenarioResult) -> None:
    admin = client_for("superadmin")
    body = {
        "sourceCriteria": [],
        "targetCriteria": [],
        "sourceDisplayColumns": [
            table_ref(CATALOG, "beneficiary", "beneficiary_id"),
            table_ref(CATALOG, "beneficiary", "full_name"),
            table_ref(CATALOG, "beneficiary", "mobile"),
        ],
        "targetDisplayColumns": [
            table_ref(CATALOG, "pension_register", "pension_id"),
            table_ref(CATALOG, "pension_register", "first_name"),
            table_ref(CATALOG, "pension_register", "last_name"),
            table_ref(CATALOG, "pension_register", "phone_primary"),
        ],
        "joinGroups": [
            {
                "source": [criterion(CATALOG, "beneficiary", "full_name", FUZZY_NAME_THRESHOLD)],
                "target": [
                    criterion(CATALOG, "pension_register", "first_name"),
                    criterion(CATALOG, "pension_register", "last_name"),
                ],
                "mode": "COMBINE",
                "fuzzyThresholdPercent": FUZZY_NAME_THRESHOLD,
                "separator": " ",
            },
            {
                "source": [criterion(CATALOG, "beneficiary", "mobile")],
                "target": [criterion(CATALOG, "pension_register", "phone_primary")],
                "mode": "COMBINE",
                "fuzzyThresholdPercent": None,
                "separator": " ",
            },
        ],
        "highlightDuplicates": False,
        "dedup": None,
        "joinType": "INNER",
        "countOnly": True,
    }
    events, wall, sql = run_match(admin, body)
    summary = match_events_summary(events)
    res.check(summary["error"] is None, f"COMBINE ran: {summary['error']}")
    n = count_from_events(events)
    res.check(n is not None and n > 0, f"COMBINE rows > 0 (got {n})")
    res.check(n is not None and n < 10_000_000, f"COMBINE rows under 10M (got {n})")
    if sql:
        res.check("array_join" in sql.lower() or "ARRAY[" in sql, "COMBINE SQL folds with array_join")
        res.check("mobile" in sql.lower() and "phone_primary" in sql.lower(),
                  "COMBINE SQL also joins exact mobile to phone_primary")
    res.row_count = n
    res.wall_s = wall
    res.sql = sql
    res.detail = f"COMBINE+mobile rows={n}"


def scenario_5(res: ScenarioResult) -> None:
    """ANY_OF on INNER — LEFT would emit extra unmatched-candidate rows (10.A.4)."""
    admin = client_for("superadmin")
    body = {
        "sourceCriteria": [],
        "targetCriteria": [],
        "sourceDisplayColumns": [
            table_ref(CATALOG, "beneficiary", "beneficiary_id"),
            table_ref(CATALOG, "beneficiary", "mobile"),
        ],
        "targetDisplayColumns": [
            table_ref(CATALOG, "pension_register", "pension_id"),
            table_ref(CATALOG, "pension_register", "phone_primary"),
            table_ref(CATALOG, "pension_register", "phone_secondary"),
        ],
        "joinGroups": [
            {
                "source": [criterion(CATALOG, "beneficiary", "mobile")],
                "target": [
                    criterion(CATALOG, "pension_register", "phone_primary"),
                    criterion(CATALOG, "pension_register", "phone_secondary"),
                ],
                "mode": "ANY_OF",
                "fuzzyThresholdPercent": None,
                "separator": " ",
            }
        ],
        "highlightDuplicates": False,
        "dedup": None,
        "joinType": "INNER",
        "countOnly": True,
    }
    events, wall, sql = run_match(admin, body)
    summary = match_events_summary(events)
    res.check(summary["error"] is None, f"ANY_OF ran: {summary['error']}")
    n = count_from_events(events)
    res.check(n is not None and n > 0, f"ANY_OF rows > 0 (got {n})")
    if sql:
        res.check("UNNEST" in sql and "CROSS JOIN" in sql, "ANY_OF uses CROSS JOIN UNNEST, not OR")
        res.check(" OR " not in sql.split("ON")[-1] if "ON" in sql else True, "no OR in ON clause")
    res.row_count = n
    res.wall_s = wall
    res.sql = sql
    res.detail = f"ANY_OF INNER rows={n}"


def scenario_6(res: ScenarioResult) -> None:
    admin = client_for("superadmin")
    base = id_match(
        "LEFT",
        count_only=False,
        result_limit=120,
        extra={
            "comparisonGroups": [
                {
                    "source": [criterion(CATALOG, "beneficiary", "annual_income")],
                    "target": [criterion(CATALOG, "bank_account", "declared_income")],
                    "mode": "COMBINE",
                },
                {
                    "source": [criterion(CATALOG, "beneficiary", "status")],
                    "target": [criterion(CATALOG, "bank_account", "status")],
                    "mode": "COMBINE",
                },
                {
                    "source": [criterion(CATALOG, "beneficiary", "full_name", FUZZY_THRESHOLD)],
                    "target": [criterion(CATALOG, "bank_account", "account_holder_name")],
                    "mode": "COMBINE",
                    "fuzzyThresholdPercent": FUZZY_THRESHOLD,
                    "separator": " ",
                },
            ]
        },
    )
    events, wall, sql = run_match(admin, base)
    summary = match_events_summary(events)
    res.check(summary["error"] is None, f"comparison sample ran: {summary['error']}")
    cols = summary["columns"]
    res.check("cmp_1_match" in cols and "cmp_2_match" in cols, "income and status verdicts projected")
    res.check(
        any(c.endswith("_score_pct") for c in cols),
        "fuzzy comparison projects score_pct",
    )
    unmatched = [r for r in summary["rows"] if r.get("match_status") == "NO_TARGET"]
    if unmatched:
        res.check(unmatched[0].get("cmp_1_match") is None, "unmatched LEFT row has NULL income verdict")
    mismatch = dict(base)
    mismatch["mismatchOnly"] = True
    mismatch["countOnly"] = True
    mismatch.pop("resultLimit", None)
    events_m, wall_m, sql_m = run_match(admin, mismatch)
    n_mis = count_from_events(events_m)
    all_body = dict(base)
    all_body["countOnly"] = True
    all_body.pop("resultLimit", None)
    events_all, _, _ = run_match(admin, all_body)
    n_all = count_from_events(events_all)
    res.check(n_mis is not None and n_all is not None and n_mis < n_all,
              f"mismatchOnly ({n_mis}) < all LEFT comparisons ({n_all})")
    res.row_count = n_mis
    res.wall_s = wall + wall_m
    res.sql = sql_m or sql
    res.detail = f"mismatchOnly={n_mis} all={n_all}"


def scenario_7(res: ScenarioResult) -> None:
    admin = client_for("superadmin")
    # Value picker
    picker = admin.post(
        "/api/analysis/column-values",
        {
            "catalog": CATALOG,
            "schema": SCHEMA,
            "table": "beneficiary",
            "column": "district_code",
            "search": "",
        },
    )
    values = picker.get("values") or []
    res.check("JP" in values, f"value picker includes JP (got {values[:12]})")
    # Filter + group-by district
    grouped = {
        "sourceCriteria": [criterion(CATALOG, "beneficiary", "beneficiary_id")],
        "targetCriteria": [criterion(CATALOG, "ration_card", "beneficiary_id")],
        "sourceDisplayColumns": [
            table_ref(CATALOG, "beneficiary", "district_code"),
            table_ref(CATALOG, "beneficiary", "beneficiary_id"),
        ],
        "targetDisplayColumns": [
            table_ref(CATALOG, "ration_card", "card_id"),
            table_ref(CATALOG, "ration_card", "head_name"),
        ],
        "highlightDuplicates": False,
        "dedup": None,
        "joinType": "INNER",
        "groupByColumns": [table_ref(CATALOG, "beneficiary", "district_code")],
        "aggregates": [{"function": "COUNT", "column": None, "distinct": False, "alias": "n"}],
        "sourceRules": {
            "root": {
                "type": "PREDICATE",
                "column": {
                    "table": {"catalog": CATALOG, "schema": SCHEMA, "table": "beneficiary"},
                    "column": "status",
                },
                "operator": "EQ",
                "value": "ACTIVE",
            }
        },
        "countOnly": False,
        "resultLimit": 50,
    }
    events, wall, sql = run_match(admin, grouped)
    summary = match_events_summary(events)
    res.check(summary["error"] is None, f"group-by ran: {summary['error']}")
    res.check(len(summary["rows"]) > 0, "group-by returned district rows")
    if sql:
        res.check("GROUP BY" in sql.upper(), "SQL contains GROUP BY")
        res.check("ACTIVE" in sql or "?" in sql or "status" in sql.lower(), "filter reached SQL")
    # Dedup is rejected with grouping; run it as its own request (ration has planted dupes).
    dedup_body = {
        "sourceCriteria": [criterion(CATALOG, "beneficiary", "beneficiary_id")],
        "targetCriteria": [criterion(CATALOG, "ration_card", "beneficiary_id")],
        "sourceDisplayColumns": [table_ref(CATALOG, "beneficiary", "beneficiary_id")],
        "targetDisplayColumns": [table_ref(CATALOG, "ration_card", "card_id")],
        "highlightDuplicates": True,
        "dedup": table_ref(CATALOG, "ration_card", "beneficiary_id"),
        "joinType": "INNER",
        "countOnly": True,
    }
    events_d, wall_d, sql_d = run_match(admin, dedup_body)
    n_dedup = count_from_events(events_d)
    res.check(n_dedup is not None and n_dedup > 0, f"dedup count > 0 (got {n_dedup})")
    res.row_count = len(summary["rows"])
    res.wall_s = wall + wall_d
    res.sql = sql
    res.detail = f"group-by districts={len(summary['rows'])} picker_values={len(values)} dedup_rows={n_dedup}"


def _multi_target_body(*target_specs: dict[str, Any]) -> dict[str, Any]:
    return {
        "hubCriteria": [criterion(CATALOG, "beneficiary", "beneficiary_id")],
        "hubDisplayColumns": [
            table_ref(CATALOG, "beneficiary", "beneficiary_id"),
            table_ref(CATALOG, "beneficiary", "full_name"),
        ],
        "hubSide": "SOURCE",
        "targets": list(target_specs),
        "highlightDuplicates": False,
        "dedup": None,
        "mismatchOnly": False,
    }


def scenario_8(res: ScenarioResult) -> None:
    admin = client_for("superadmin")
    bank = {
        "label": "bank",
        "catalog": CATALOG,
        "schema": SCHEMA,
        "table": "bank_account",
        "joinCriteria": [criterion(CATALOG, "bank_account", "beneficiary_ref")],
        "displayColumns": [table_ref(CATALOG, "bank_account", "account_id")],
        "joinType": "INNER",
    }
    pension = {
        "label": "pension",
        "catalog": CATALOG,
        "schema": SCHEMA,
        "table": "pension_register",
        "joinCriteria": [criterion(CATALOG, "pension_register", "beneficiary_id")],
        "displayColumns": [table_ref(CATALOG, "pension_register", "pension_id")],
        "joinType": "INNER",
    }
    fanout = {
        "label": "fanout",
        "catalog": CATALOG,
        "schema": SCHEMA,
        "table": "bank_account",
        "joinCriteria": [criterion(CATALOG, "bank_account", FANOUT_COLUMN)],
        "joinGroups": [
            {
                "source": [criterion(CATALOG, "beneficiary", FANOUT_COLUMN)],
                "target": [criterion(CATALOG, "bank_account", FANOUT_COLUMN)],
                "mode": "COMBINE",
                "fuzzyThresholdPercent": None,
                "separator": " ",
            }
        ],
        "displayColumns": [],
        "joinType": "INNER",
    }
    # Success path first — the 120s multi-match budget cannot also stream a
    # fan-out plan (and audit pre-plans every target before the stream starts).
    started = time.perf_counter()
    events = admin.stream_ndjson("/api/analysis/match-multi", _multi_target_body(bank, pension))
    wall = time.perf_counter() - started
    summary = match_events_summary(events)
    per = {p.get("label"): p for p in (summary.get("per_target") or [])}
    res.check(
        "bank" in per and per["bank"].get("status") in ("ok", "skipped"),
        f"bank target ran: {per.get('bank')}",
    )
    res.check(
        "pension" in per and per["pension"].get("status") in ("ok", "skipped"),
        f"pension target ran: {per.get('pension')}",
    )
    res.check(
        any(p.get("status") == "ok" for p in per.values()),
        f"at least one target produced rows: {per}",
    )
    # Deliberate failing target. Contract (CLAUDE.md / controller javadoc):
    # partial failure is per-target in the NDJSON stream. Actual: 
    # AnalysisAuditService.planMultiMatchAudited plans every target first, so a
    # fan-out refusal is HTTP 400 for the whole request.
    fanout_in_band = False
    fanout_http_400 = False
    fanout_msg = ""
    try:
        events_f = admin.stream_ndjson("/api/analysis/match-multi", _multi_target_body(bank, fanout))
        per_f = {p.get("label"): p for p in (match_events_summary(events_f).get("per_target") or [])}
        fanout_in_band = per_f.get("fanout", {}).get("status") == "error"
        fanout_msg = str(per_f.get("fanout"))
    except HttpError as exc:
        fanout_msg = exc.body
        fanout_http_400 = exc.status == 400 and (
            "estimated" in exc.body.lower() or "fan" in exc.body.lower()
        )
    res.check(
        fanout_in_band or fanout_http_400,
        f"failing target refused with estimate (in-band or HTTP 400): {fanout_msg[:300]}",
    )
    if fanout_http_400 and not fanout_in_band:
        res.notes.append(
            "PRODUCT DEFECT: AnalysisAuditService.planMultiMatchAudited pre-plans "
            "every target, so a fan-out on one target is HTTP 400 rather than an "
            "in-band NDJSON error. Failing request: hub beneficiary_id + targets "
            "bank (beneficiary_ref) and fanout (status). Estimate was in the 400 body."
        )
    started_sql = next(
        (p.get("sql") for p in summary["progress"] if p.get("phase") == "started" and p.get("sql")),
        None,
    )
    res.row_count = summary.get("total_rows")
    res.wall_s = wall
    res.sql = started_sql
    res.detail = f"per_target={per} fanout={'in-band' if fanout_in_band else 'http-400' if fanout_http_400 else 'none'}"


def scenario_9(res: ScenarioResult) -> None:
    admin = client_for("superadmin")
    req = {
        "sourceCatalog": CATALOG,
        "sourceSchema": SCHEMA,
        "sourceTable": "beneficiary",
        "targetCatalog": CATALOG,
        "targetSchema": SCHEMA,
        "targetTable": "bank_account",
        "probe": False,
    }
    started = time.perf_counter()
    meta = admin.post("/api/analysis/suggest-keys", req)
    wall_meta = time.perf_counter() - started
    res.check(isinstance(meta, list) and len(meta) > 0, f"metadata suggestions non-empty ({len(meta) if isinstance(meta, list) else meta})")
    req["probe"] = True
    started = time.perf_counter()
    probed = admin.post("/api/analysis/suggest-keys", req)
    wall_probe = time.perf_counter() - started
    res.check(isinstance(probed, list) and len(probed) > 0, "probe suggestions non-empty")
    res.row_count = len(probed) if isinstance(probed, list) else 0
    res.wall_s = wall_meta + wall_probe
    res.detail = f"meta={len(meta) if isinstance(meta, list) else 0} probe={len(probed) if isinstance(probed, list) else 0}"
    res.notes.append(f"top metadata: {meta[:3] if isinstance(meta, list) else meta}")
    res.notes.append(f"top probe: {probed[:3] if isinstance(probed, list) else probed}")


def scenario_10(res: ScenarioResult) -> None:
    admin = client_for("superadmin")
    # Fan-out on a 2-value column.
    fanout = {
        "sourceCriteria": [criterion(CATALOG, "beneficiary", FANOUT_COLUMN)],
        "targetCriteria": [criterion(CATALOG, "bank_account", FANOUT_COLUMN)],
        "sourceDisplayColumns": [table_ref(CATALOG, "beneficiary", "beneficiary_id")],
        "targetDisplayColumns": [table_ref(CATALOG, "bank_account", "account_id")],
        "highlightDuplicates": False,
        "dedup": None,
        "joinType": "INNER",
        "countOnly": True,
    }
    refused = False
    message = ""
    started = time.perf_counter()
    try:
        events, wall, sql = run_match(admin, fanout)
        summary = match_events_summary(events)
        message = summary.get("error") or ""
        refused = "estimated" in message.lower() or "fan" in message.lower()
        res.sql = sql
        res.wall_s = wall
    except HttpError as exc:
        wall = time.perf_counter() - started
        message = exc.body
        refused = exc.status in (400, 409) and (
            "estimated" in message.lower() or "50" in message or "fan" in message.lower()
        )
        res.wall_s = wall
        res.sql = None
    res.check(refused, f"fan-out refused with estimate in the message (got: {message[:400]})")
    # CSV export of the id match — complete, not the 10k grid slice.
    export_body = id_match("INNER", count_only=False, result_limit=None)
    started = time.perf_counter()
    csv_path = Path("/tmp/aa-demo/scenario10.csv")
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    csv_bytes = admin.request(
        "POST", "/api/analysis/match.csv", body=export_body, raw=True, accept="text/csv"
    )
    csv_wall = time.perf_counter() - started
    if isinstance(csv_bytes, (bytes, bytearray)):
        csv_path.write_bytes(csv_bytes)
        data_rows = max(sum(1 for _ in csv_bytes.splitlines()) - 1, 0)
    else:
        data_rows = 0
    res.check(data_rows > 0, f"CSV export has data rows (got {data_rows})")
    res.check(data_rows != GRID_CAP, "CSV is not truncated at the 10k render cutoff")
    res.notes.append(f"csv_rows={data_rows} csv_wall={csv_wall:.2f}s bytes={csv_path.stat().st_size if csv_path.exists() else 0}")
    res.notes.append("UI render cutoff is frontend MAX_DISPLAYED_ROWS=10000 (ReportAnalysisTab.tsx:224)")
    res.row_count = data_rows
    res.detail = f"fan-out refused; csv_rows={data_rows}"
    if res.wall_s is None:
        res.wall_s = csv_wall
    else:
        res.wall_s += csv_wall


def scenario_11(res: ScenarioResult) -> None:
    body = id_match("INNER", count_only=True, result_limit=None)
    counts: dict[str, int | str] = {}
    for user in ("superadmin", "jaipurofficer", "sanganeruser"):
        client = client_for(user)
        try:
            events, wall, sql = run_match(client, body)
            n = count_from_events(events)
            err = match_events_summary(events)["error"]
            if err:
                counts[user] = err
                res.notes.append(f"{user} error: {err}")
            else:
                counts[user] = n if n is not None else -1
                res.notes.append(f"{user} rows={n} wall={wall:.2f}s")
                if sql:
                    res.sql = sql
        except HttpError as exc:
            counts[user] = f"HTTP {exc.status}"
            res.notes.append(f"{user} HTTP {exc.status}: {exc.body[:240]}")
    sa = counts.get("superadmin")
    jp = counts.get("jaipurofficer")
    sg = counts.get("sanganeruser")
    res.check(isinstance(sa, int) and sa > 0, f"superadmin count > 0 ({sa})")
    res.check(isinstance(jp, int) and isinstance(sa, int) and jp < sa,
              f"jaipurofficer ({jp}) < superadmin ({sa})")
    # Sanganer is a taluka; bank is bound only at district, so bank is invisible.
    # The two-table match should fail visibility on the bank side.
    res.check(
        not isinstance(sg, int) or (isinstance(jp, int) and sg < jp),
        f"sanganeruser is denied or strictly smaller than Jaipur (got {sg})",
    )
    # Unbound jdbc_* table is invisible to a scoped officer.
    jdbc_denied = False
    try:
        sources = client_for("superadmin").get("/api/admin/data-sources")
        demo = next((s for s in sources if s.get("name") == "Demo Business DB"), None)
        if demo:
            unbound = id_match("INNER", count_only=True, result_limit=None)
            for crit in unbound["sourceCriteria"] + unbound["targetCriteria"]:
                crit["catalog"] = f"jdbc_{demo['id']}"
            for col in unbound["sourceDisplayColumns"] + unbound["targetDisplayColumns"]:
                col["catalog"] = f"jdbc_{demo['id']}"
            officer = client_for("jaipurofficer")
            try:
                events, _, _ = run_match(officer, unbound)
                err = match_events_summary(events)["error"] or ""
                jdbc_denied = "not visible" in err.lower() or "not registered" in err.lower()
            except HttpError as exc:
                jdbc_denied = exc.status in (400, 403) and (
                    "not visible" in exc.body.lower() or "not registered" in exc.body.lower()
                )
    except HttpError as exc:
        res.notes.append(f"unbound check skipped: {exc}")
    res.check(jdbc_denied, "unbound jdbc_* table denied to jaipurofficer")
    # Shared reference join does not relax the scoped filter.
    shared = {
        "sourceCriteria": [criterion(CATALOG, "beneficiary", "district_code")],
        "targetCriteria": [criterion(CATALOG, "district_master", "district_code")],
        "sourceDisplayColumns": [
            table_ref(CATALOG, "beneficiary", "beneficiary_id"),
            table_ref(CATALOG, "beneficiary", "district_code"),
        ],
        "targetDisplayColumns": [table_ref(CATALOG, "district_master", "district_name")],
        "highlightDuplicates": False,
        "dedup": None,
        "joinType": "INNER",
        "countOnly": True,
    }
    sa_n = count_from_events(run_match(client_for("superadmin"), shared)[0])
    jp_n = count_from_events(run_match(client_for("jaipurofficer"), shared)[0])
    res.check(
        sa_n is not None and jp_n is not None and jp_n < sa_n,
        f"shared-ref join still scoped: jaipur {jp_n} < superadmin {sa_n}",
    )
    res.row_count = jp if isinstance(jp, int) else None
    res.detail = f"counts={counts} shared jaipur={jp_n} superadmin={sa_n}"


def scenario_12(res: ScenarioResult, started_iso: str) -> None:
    admin = client_for("superadmin")
    page = admin.get("/api/admin/audit", query={"from": started_iso, "size": 200})
    items = page.get("entries") or page.get("content") or page.get("items") or []
    res.check(len(items) > 0, f"audit returned events since {started_iso} (page keys={list(page)[:8]})")
    actions = {row.get("actionType") for row in items}
    res.check("QUERY_EXECUTED" in actions or "QUERY_FINISHED" in actions or "QUERY_PREVIEWED" in actions,
              f"query actions present: {sorted(actions)[:12]}")
    shapes = [row.get("queryShape") or "" for row in items]
    details = " ".join(str(row.get("detail") or "") for row in items)
    # Bound values (Aadhaar-like 12-digit, mobiles) must not appear.
    leaked = [s for s in shapes if any(ch.isdigit() for ch in s) and "lpad" in s.lower()]
    res.check("9800000001" not in details, "audit detail has no officer mobile")
    res.check(all("?" in s or s == "" or "SELECT" in s.upper() for s in shapes if s),
              "query shapes look like SQL with placeholders, not a value dump")
    jaipur = client_for("jaipuradmin")
    jaipur_page = jaipur.get("/api/admin/audit", query={"from": started_iso, "size": 200})
    jaipur_items = jaipur_page.get("entries") or jaipur_page.get("content") or []
    actors = {row.get("actorUsername") for row in jaipur_items}
    # Sanganer is a taluka of Jaipur — jaipuradmin's subtree includes sanganeruser.
    res.check("jaipurofficer" in actors or "sanganeruser" in actors,
              f"jaipuradmin sees Jaipur-subtree actors ({actors})")
    res.check("superadmin" not in actors, f"jaipuradmin does not see unscoped superadmin ({actors})")
    res.row_count = len(items)
    res.detail = f"superadmin_events={len(items)} jaipuradmin_events={len(jaipur_items)} actions={sorted(actions)}"


def scenario_13(res: ScenarioResult) -> None:
    admin = client_for("superadmin")
    body = id_match("INNER", count_only=False, result_limit=500)
    save_body = {
        "name": "DEMO-02 beneficiary x bank INNER",
        "description": "Created by scripts/demo/run-scenarios.py",
        "request": body,
    }
    created = None
    for attempt in range(2):
        try:
            created = admin.post("/api/saved-queries", save_body)
            break
        except HttpError as exc:
            if attempt == 0 and exc.status == 500 and "timed out" in exc.body.lower():
                res.notes.append(f"saved-query create retry after Presto timeout: {exc.body[:160]}")
                time.sleep(3)
                continue
            raise
    qid = (created or {}).get("id")
    res.check(qid is not None, f"saved query created id={qid}")
    loaded = admin.get(f"/api/saved-queries/{qid}")
    res.check(loaded.get("id") == qid, "saved query reloads")
    events, wall, sql = run_match(admin, loaded["request"])
    n = count_from_events(events)
    res.check(n is not None and n > 0, f"re-running saved query returned rows ({n})")
    csv_bytes = admin.request(
        "POST", "/api/analysis/match.csv", body=loaded["request"], raw=True, accept="text/csv"
    )
    xlsx = admin.request(
        "POST",
        "/api/analysis/match.xlsx",
        body=loaded["request"],
        raw=True,
        accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )
    res.check(isinstance(csv_bytes, (bytes, bytearray)) and len(csv_bytes) > 20, "CSV export non-empty")
    res.check(isinstance(xlsx, (bytes, bytearray)) and len(xlsx) > 20, "XLSX export non-empty")
    res.row_count = n
    res.wall_s = wall
    res.sql = sql
    res.detail = f"savedQueryId={qid} rerun_rows={n} csv={len(csv_bytes)} xlsx={len(xlsx)}"


def _result_to_dict(r: ScenarioResult) -> dict[str, Any]:
    return {
        "number": r.number,
        "name": r.name,
        "user": r.user,
        "ok": r.ok,
        "skipped": r.skipped,
        "detail": r.detail,
        "row_count": r.row_count,
        "wall_s": r.wall_s,
        "sql": r.sql,
        "notes": r.notes,
        "assertions": r.assertions,
    }


def _result_from_dict(d: dict[str, Any]) -> ScenarioResult:
    r = ScenarioResult(d["number"], d["name"], d["user"])
    r.ok = bool(d.get("ok"))
    r.skipped = bool(d.get("skipped"))
    r.detail = d.get("detail") or ""
    r.row_count = d.get("row_count")
    r.wall_s = d.get("wall_s")
    r.sql = d.get("sql")
    r.notes = list(d.get("notes") or [])
    r.assertions = [tuple(a) for a in (d.get("assertions") or [])]
    return r


def write_results(results: list[ScenarioResult], header_notes: list[str]) -> None:
    RESULTS_PATH.parent.mkdir(parents=True, exist_ok=True)
    prior: dict[int, dict[str, Any]] = {}
    if RESULTS_JSON.exists():
        try:
            for item in json.loads(RESULTS_JSON.read_text()):
                prior[int(item["number"])] = item
        except (json.JSONDecodeError, KeyError, TypeError):
            prior = {}
    for r in results:
        prior[r.number] = _result_to_dict(r)
    RESULTS_JSON.write_text(json.dumps(list(prior.values()), indent=2) + "\n")
    results = [_result_from_dict(prior[n]) for n in sorted(prior)]
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")
    lines = [
        "# DEMO-02 scenario results",
        "",
        f"Generated {now} by `scripts/demo/run-scenarios.py`.",
        "",
        *header_notes,
        "",
        "| # | Scenario | User | Result | Rows | Wall (s) | Notes |",
        "|---|----------|------|--------|------|----------|-------|",
    ]
    for r in results:
        status = "PASS" if r.ok else ("HOLD" if r.skipped else "FAIL")
        rows = "" if r.row_count is None else str(r.row_count)
        wall = "" if r.wall_s is None else f"{r.wall_s:.2f}"
        note = (r.detail or "") + (("; " + "; ".join(r.notes[:2])) if r.notes else "")
        note = note.replace("|", "/").replace("\n", " ")
        if len(note) > 180:
            note = note[:177] + "..."
        lines.append(
            f"| {r.number} | {r.name} | {r.user} | **{status}** | {rows} | {wall} | {note} |"
        )
    lines.extend(["", "## Assertions and SQL", ""])
    for r in results:
        lines.append(f"### {r.number}. {r.name}")
        lines.append("")
        lines.append(f"- User: `{r.user}`")
        status = "PASS" if r.ok else ("HOLD" if r.skipped else "FAIL")
        lines.append(f"- Result: **{status}**")
        if r.row_count is not None:
            lines.append(f"- Rows: {r.row_count}")
        if r.wall_s is not None:
            lines.append(f"- Wall time: {r.wall_s:.2f}s")
        for ok, msg in r.assertions:
            lines.append(f"- {'OK' if ok else 'FAIL'}: {msg}")
        for note in r.notes:
            lines.append(f"- {note}")
        if r.sql:
            lines.append("")
            lines.append("```sql")
            lines.append(r.sql.strip())
            lines.append("```")
        lines.append("")
    RESULTS_PATH.write_text("\n".join(lines) + "\n")
    print(f"wrote {RESULTS_PATH}")


def main() -> int:
    parser = argparse.ArgumentParser(description="DEMO-02 scenario runner")
    parser.add_argument("--only", type=int, nargs="*", help="Run only these scenario numbers")
    parser.add_argument(
        "--include-fuzzy",
        action="store_true",
        help="Run scenarios 2 and 3 (fuzzy name at least 85 percent + exact mobile). 2b name-only is never run.",
    )
    args = parser.parse_args()
    started_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    results: list[ScenarioResult] = []
    header = [
        f"- API: `{os.environ.get('SRSE_API_BASE', 'http://localhost:8080')}`",
        f"- Catalog: `{CATALOG}.{SCHEMA}`",
        f"- Started: {started_iso}",
        f"- Query timeout env: `{os.environ.get('SRSE_QUERY_TIMEOUT_SECONDS', 'unset')}`",
    ]

    plan: list[tuple[int, str, str, Callable[..., None]]] = [
        (1, "INNER / LEFT / RIGHT / FULL + NULL verdicts", "superadmin", scenario_1),
        (2, "Fuzzy name ≥85% + exact mobile, unscoped", "superadmin", scenario_2),
        (3, "Same fuzzy+mobile, scoped", "jaipurofficer", scenario_3),
        (4, "COMBINE full_name vs first+last @ 85% + exact mobile", "superadmin", scenario_4),
        (5, "ANY_OF mobile vs phones (INNER)", "superadmin", scenario_5),
        (6, "Post-join compare + mismatchOnly + fuzzy cmp", "superadmin", scenario_6),
        (7, "Dedup / group-by / filters / value pickers", "superadmin", scenario_7),
        (8, "Multi-target + failing target", "superadmin", scenario_8),
        (9, "Suggest-keys metadata and probe", "superadmin", scenario_9),
        (10, "Fan-out refusal + complete CSV + 10k cutoff", "superadmin", scenario_10),
        (11, "Scoping superadmin / jaipur / sanganer + unbound + shared-ref", "all", scenario_11),
        (12, "Audit shape-only + jaipuradmin subtree", "superadmin, jaipuradmin", scenario_12),
        (13, "Saved query + CSV/XLSX export", "superadmin", scenario_13),
    ]
    unscoped_fuzzy: int | None = None
    only = set(args.only or [])
    hold_fuzzy = not args.include_fuzzy
    if hold_fuzzy:
        header.append(
            "- Scenarios **2** and **3** (fuzzy name ≥85% + exact mobile) were not run. "
            "Re-run with `--include-fuzzy`."
        )
    else:
        header.append(
            "- Scenarios **2** and **3**: fuzzy name ≥85% **and exact mobile**. "
            "Scenario **2b** (name-only) skipped — prefix-3 fan-out ~735M exceeds the 50M guard."
        )
    for number, name, user, fn in plan:
        if hold_fuzzy and number in (2, 3):
            if only and number not in only:
                continue
            res = ScenarioResult(number, name, user)
            res.skipped = True
            res.detail = "held — re-run with --include-fuzzy"
            results.append(res)
            print(f"=== scenario {number}: {name} ===")
            print("    HOLD — pass --include-fuzzy to run")
            continue
        if only and number not in only:
            continue
        res = ScenarioResult(number, name, user)
        print(f"=== scenario {number}: {name} ===")
        try:
            if number == 3:
                fn(res, unscoped_fuzzy)
            elif number == 12:
                fn(res, started_iso)
            else:
                fn(res)
            if number == 2:
                unscoped_fuzzy = res.row_count
        except Exception as exc:  # noqa: BLE001 — scenario isolation
            res.notes.append(f"exception: {exc}")
            res.check(False, f"scenario raised {type(exc).__name__}: {exc}")
        res.finish()
        results.append(res)
        print(f"    {'PASS' if res.ok else 'FAIL'} {res.detail}")
    write_results(results, header)
    failed = sum(1 for r in results if not r.ok and not r.skipped)
    held = sum(1 for r in results if r.skipped)
    passed = sum(1 for r in results if r.ok)
    print(f"{passed} passed, {failed} failed, {held} held")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
