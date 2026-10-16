#!/usr/bin/env python3
"""Idempotent DEMO-02 product setup via the admin API.

Re-run is a no-op when the contract objects already exist. --reset removes
only what this script created (tracked in .setup-state.json).
"""

from __future__ import annotations

import argparse
import os
import sys
from typing import Any

from lib import (
    DEPARTMENTS,
    DISTRICTS,
    DATA_SOURCE_NAME,
    PASSWORDS,
    PRESTO_CATALOG_ALIAS,
    SCHEMA,
    SOURCE_SYSTEM,
    STATE_PATH,
    ApiClient,
    HttpError,
    load_state,
    login_ready,
    save_state,
)

TABLES = [
    {
        "table": "beneficiary",
        "layer": "GOLD",
        "business": "Beneficiary master",
        "scoped": True,
        "shared": False,
        "columns": {
            "beneficiary_id": {"businessName": "Beneficiary ID", "fuzzy": False, "visible": True, "compareAs": "AUTO", "picker": False},
            "jan_aadhaar_no": {"businessName": "Jan Aadhaar (fake)", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": False},
            "full_name": {"businessName": "Full name", "fuzzy": True, "visible": True, "compareAs": "TEXT", "picker": False},
            "father_name": {"businessName": "Father name", "fuzzy": True, "visible": True, "compareAs": "TEXT", "picker": False},
            "gender": {"businessName": "Gender", "fuzzy": False, "visible": True, "compareAs": "AUTO", "picker": True},
            "dob": {"businessName": "Date of birth", "fuzzy": False, "visible": True, "compareAs": "DATE", "picker": False},
            "district_code": {"businessName": "District code", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "district": {"businessName": "District", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "taluka_code": {"businessName": "Taluka code", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "taluka": {"businessName": "Taluka", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "village_code": {"businessName": "Village code", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": False},
            "department_code": {"businessName": "Department code", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "annual_income": {"businessName": "Annual income", "fuzzy": False, "visible": True, "compareAs": "NUMBER", "picker": False},
            "mobile": {"businessName": "Mobile", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": False},
            "address": {"businessName": "Address", "fuzzy": True, "visible": True, "compareAs": "TEXT", "picker": False},
            "status": {"businessName": "Status", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
        },
        "bindings": [("district", "district_code"), ("taluka", "taluka_code"), ("village", "village_code")],
        "exempt_department": False,
    },
    {
        "table": "bank_account",
        "layer": "SILVER",
        "business": "Bank accounts",
        "scoped": True,
        "shared": False,
        "columns": {
            "account_id": {"businessName": "Account ID", "fuzzy": False, "visible": True, "compareAs": "AUTO", "picker": False},
            "beneficiary_ref": {"businessName": "Beneficiary ref (padded text)", "fuzzy": False, "visible": True, "compareAs": "NUMBER", "picker": False},
            "account_holder_name": {"businessName": "Account holder name", "fuzzy": True, "visible": True, "compareAs": "TEXT", "picker": False},
            "declared_income": {"businessName": "Declared income", "fuzzy": False, "visible": True, "compareAs": "NUMBER", "picker": False},
            "mobile": {"businessName": "Mobile", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": False},
            "bank_name": {"businessName": "Bank", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "ifsc": {"businessName": "IFSC", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": False},
            "account_no": {"businessName": "Account number", "fuzzy": False, "visible": False, "compareAs": "TEXT", "picker": False},
            "district_code": {"businessName": "District code", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "status": {"businessName": "Account status", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
        },
        "bindings": [("district", "district_code")],
        "exempt_department": True,
    },
    {
        "table": "pension_register",
        "layer": "SILVER",
        "business": "Pension register",
        "scoped": True,
        "shared": False,
        "columns": {
            "pension_id": {"businessName": "Pension ID", "fuzzy": False, "visible": True, "compareAs": "AUTO", "picker": False},
            "beneficiary_id": {"businessName": "Beneficiary ID", "fuzzy": False, "visible": True, "compareAs": "AUTO", "picker": False},
            "first_name": {"businessName": "First name", "fuzzy": True, "visible": True, "compareAs": "TEXT", "picker": False},
            "last_name": {"businessName": "Last name", "fuzzy": True, "visible": True, "compareAs": "TEXT", "picker": False},
            "phone_primary": {"businessName": "Primary phone", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": False},
            "phone_secondary": {"businessName": "Secondary phone", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": False},
            "pension_type": {"businessName": "Pension type", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "monthly_amount": {"businessName": "Monthly amount", "fuzzy": False, "visible": True, "compareAs": "NUMBER", "picker": False},
            "district_code": {"businessName": "District code", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
        },
        "bindings": [("district", "district_code")],
        "exempt_department": True,
    },
    {
        "table": "ration_card",
        "layer": "SILVER",
        "business": "Ration cards",
        "scoped": True,
        "shared": False,
        "columns": {
            "card_id": {"businessName": "Card ID", "fuzzy": False, "visible": True, "compareAs": "AUTO", "picker": False},
            "beneficiary_id": {"businessName": "Beneficiary ID", "fuzzy": False, "visible": True, "compareAs": "AUTO", "picker": False},
            "head_name": {"businessName": "Head of household", "fuzzy": True, "visible": True, "compareAs": "TEXT", "picker": False},
            "family_size": {"businessName": "Family size", "fuzzy": False, "visible": True, "compareAs": "NUMBER", "picker": False},
            "district_code": {"businessName": "District code", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
        },
        "bindings": [("district", "district_code")],
        "exempt_department": True,
    },
    {
        "table": "district_master",
        "layer": "SILVER",
        "business": "District reference",
        "scoped": False,
        "shared": True,
        "columns": {
            "district_code": {"businessName": "District code", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "district_name": {"businessName": "District name", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
            "division": {"businessName": "Division", "fuzzy": False, "visible": True, "compareAs": "TEXT", "picker": True},
        },
        "bindings": [],
        "exempt_department": True,
    },
]


def _mark(state: dict[str, Any], kind: str, key: str, value: Any, created: bool) -> None:
    bucket = "created" if created else "reused"
    state.setdefault(bucket, {}).setdefault(kind, {})[key] = value


def ensure_data_source(admin: ApiClient, state: dict[str, Any]) -> dict[str, Any]:
    existing = admin.get("/api/admin/data-sources")
    for row in existing:
        if row.get("name") == DATA_SOURCE_NAME:
            source_id = row["id"]
            if row.get("federationCatalog") != PRESTO_CATALOG_ALIAS:
                admin.put(
                    f"/api/admin/data-sources/{source_id}/federation",
                    {"federationCatalog": PRESTO_CATALOG_ALIAS},
                )
            test = admin.post(f"/api/admin/data-sources/{source_id}/test")
            if not test.get("success"):
                raise RuntimeError(f"Data source test failed: {test}")
            _mark(state, "data_source", DATA_SOURCE_NAME, source_id, created=False)
            print(f"data-source: reused id={source_id} test={test.get('productName')}")
            return row
    host = os.environ.get("DEMO_DATASOURCE_HOST", "localhost")
    port = int(os.environ.get("DEMO_DATASOURCE_PORT", "5433"))
    created = admin.post(
        "/api/admin/data-sources",
        {
            "name": DATA_SOURCE_NAME,
            "databaseType": "POSTGRESQL",
            "host": host,
            "port": port,
            "database": os.environ.get("DEMO_DATASOURCE_DB", "analytics_demo"),
            "username": os.environ.get("DEMO_DATASOURCE_USER", "demo_ro"),
            "password": os.environ.get("DEMO_DATASOURCE_PASSWORD", "demo_ro_dev_pw"),
            "ssl": False,
            "federationCatalog": PRESTO_CATALOG_ALIAS,
        },
    )
    source_id = created["id"]
    test = admin.post(f"/api/admin/data-sources/{source_id}/test")
    if not test.get("success"):
        raise RuntimeError(f"Data source test failed after create: {test}")
    _mark(state, "data_source", DATA_SOURCE_NAME, source_id, created=True)
    print(f"data-source: created id={source_id} test={test.get('productName')}")
    return created


def _find_registration(rows: list[dict[str, Any]], catalog: str, table: str) -> dict[str, Any] | None:
    for row in rows:
        if row.get("catalog") == catalog and row.get("schema") == SCHEMA and row.get("table") == table:
            return row
        # External registration view uses logicalCatalog
        if row.get("logicalCatalog") == catalog and row.get("schema") == SCHEMA and row.get("table") == table:
            return row
    return None


def register_via_data_source(
    admin: ApiClient, source_id: int, state: dict[str, Any]
) -> str:
    """Register contract tables on jdbc_<id> (unbound — deny-by-default demo)."""
    logical = f"jdbc_{source_id}"
    existing = admin.get(f"/api/admin/data-sources/{source_id}/registrations")
    for spec in TABLES:
        found = _find_registration(existing, logical, spec["table"])
        if found:
            _mark(state, "jdbc_registration", spec["table"], found.get("registrationId") or found.get("id"), False)
            continue
        created = admin.post(
            f"/api/admin/data-sources/{source_id}/registrations",
            {
                "catalog": os.environ.get("DEMO_DATASOURCE_DB", "analytics_demo"),
                "schema": SCHEMA,
                "table": spec["table"],
                "layer": spec["layer"],
                "sourceSystem": SOURCE_SYSTEM,
                "tableGroup": spec["business"],
            },
        )
        rid = created.get("registrationId") or created.get("id")
        _mark(state, "jdbc_registration", spec["table"], rid, True)
        print(f"  jdbc register {logical}.{SCHEMA}.{spec['table']} id={rid}")
    return logical


def register_via_lakehouse(admin: ApiClient, state: dict[str, Any]) -> None:
    """Register the same tables on demo_pg so Query Builder + scoping share one address."""
    try:
        catalogs = admin.get("/api/admin/lakehouse/browse/catalogs")
    except HttpError as exc:
        raise RuntimeError(
            "Cannot browse Presto catalogs. Point the backend at the demo Presto "
            "(jdbc:presto://localhost:8083) — see docs/demo/DEMO_SCRIPT.md. "
            f"Detail: {exc}"
        ) from exc
    if PRESTO_CATALOG_ALIAS not in catalogs:
        raise RuntimeError(
            f"Presto catalogs are {catalogs}; {PRESTO_CATALOG_ALIAS} is missing. "
            "Start docker/demo Presto and point SRSE_PRESTO_URL at it."
        )
    existing = admin.get("/api/admin/lakehouse/registrations")
    for spec in TABLES:
        found = _find_registration(existing, PRESTO_CATALOG_ALIAS, spec["table"])
        if found:
            _mark(state, "presto_registration", spec["table"], found["id"], False)
            continue
        created = admin.post(
            "/api/admin/lakehouse/registrations",
            {
                "catalog": PRESTO_CATALOG_ALIAS,
                "schema": SCHEMA,
                "table": spec["table"],
                "layer": spec["layer"],
                "sourceSystem": SOURCE_SYSTEM,
                "tableGroup": spec["business"],
            },
        )
        _mark(state, "presto_registration", spec["table"], created["id"], True)
        print(f"  presto register {PRESTO_CATALOG_ALIAS}.{SCHEMA}.{spec['table']} id={created['id']}")


def upsert_column_metadata(admin: ApiClient, catalog: str) -> None:
    for spec in TABLES:
        for column, meta in spec["columns"].items():
            admin.put(
                "/api/analysis/column-metadata",
                {
                    "catalog": catalog,
                    "schema": SCHEMA,
                    "table": spec["table"],
                    "column": column,
                    "businessName": meta["businessName"],
                    "fuzzyMatchable": meta["fuzzy"],
                    "visible": meta["visible"],
                    "compareAs": meta["compareAs"],
                    "valuePickerEnabled": meta["picker"],
                },
            )
        print(f"  column metadata {catalog}.{SCHEMA}.{spec['table']} ({len(spec['columns'])} cols)")


def ensure_dimensions(admin: ApiClient, state: dict[str, Any]) -> dict[str, Any]:
    dims = {d["code"].upper(): d for d in admin.get("/api/admin/scope/dimensions")}
    geo = dims.get("GEOGRAPHY")
    if geo is None:
        geo = admin.post(
            "/api/admin/scope/dimensions",
            {"code": "GEOGRAPHY", "name": "Geography", "displayOrder": 1},
        )
        _mark(state, "dimension", "GEOGRAPHY", geo["id"], True)
        print(f"dimension GEOGRAPHY id={geo['id']}")
    else:
        _mark(state, "dimension", "GEOGRAPHY", geo["id"], False)

    dept = dims.get("DEPARTMENT")
    if dept is None:
        dept = admin.post(
            "/api/admin/scope/dimensions",
            {"code": "DEPARTMENT", "name": "Department", "displayOrder": 2},
        )
        _mark(state, "dimension", "DEPARTMENT", dept["id"], True)
        print(f"dimension DEPARTMENT id={dept['id']}")
    else:
        _mark(state, "dimension", "DEPARTMENT", dept["id"], False)

    geo_levels = _ensure_levels(
        admin, state, geo["id"],
        [(1, "District"), (2, "Taluka"), (3, "Village")],
    )
    dept_levels = _ensure_levels(admin, state, dept["id"], [(1, "Department")])
    geo_nodes = _ensure_geo_nodes(admin, state, geo["id"], geo_levels)
    dept_nodes = _ensure_dept_nodes(admin, state, dept["id"], dept_levels[1])
    return {
        "geo": geo,
        "dept": dept,
        "geo_levels": geo_levels,
        "dept_levels": dept_levels,
        "geo_nodes": geo_nodes,
        "dept_nodes": dept_nodes,
    }


def _ensure_levels(
    admin: ApiClient, state: dict[str, Any], dimension_id: int, wanted: list[tuple[int, str]]
) -> dict[int, dict[str, Any]]:
    existing = {row["depth"]: row for row in admin.get("/api/admin/scope/levels", query={"dimensionId": dimension_id})}
    out: dict[int, dict[str, Any]] = {}
    for depth, name in wanted:
        if depth in existing:
            out[depth] = existing[depth]
            _mark(state, "level", f"{dimension_id}:{depth}", existing[depth]["id"], False)
            continue
        created = admin.post(
            "/api/admin/scope/levels",
            {"dimensionId": dimension_id, "depth": depth, "name": name},
        )
        out[depth] = created
        _mark(state, "level", f"{dimension_id}:{depth}", created["id"], True)
        print(f"  level {name} depth={depth} id={created['id']}")
    return out


def _ensure_geo_nodes(
    admin: ApiClient,
    state: dict[str, Any],
    dimension_id: int,
    levels: dict[int, dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    existing = {
        row["code"]: row
        for row in admin.get("/api/admin/scope/nodes", query={"dimensionId": dimension_id})
    }
    nodes: dict[str, dict[str, Any]] = {}
    for code, name in DISTRICTS:
        node = _ensure_node(
            admin, state, existing, dimension_id, levels[1]["id"], None, code, name
        )
        nodes[code] = node
    # Sanganer is a taluka of Jaipur (seed taluka_code = SG).
    sg = _ensure_node(
        admin, state, existing, dimension_id, levels[2]["id"], nodes["JP"]["id"], "SG", "Sanganer"
    )
    nodes["SG"] = sg
    # A second Jaipur taluka so Sanganer is visibly a subset.
    tjp = _ensure_node(
        admin, state, existing, dimension_id, levels[2]["id"], nodes["JP"]["id"], "TJP01", "Taluka-JP-1"
    )
    nodes["TJP01"] = tjp
    village = _ensure_node(
        admin, state, existing, dimension_id, levels[3]["id"], sg["id"], "VSG100", "Sanganer ward 100"
    )
    nodes["VSG100"] = village
    return nodes


def _ensure_dept_nodes(
    admin: ApiClient, state: dict[str, Any], dimension_id: int, level: dict[str, Any]
) -> dict[str, dict[str, Any]]:
    existing = {
        row["code"]: row
        for row in admin.get("/api/admin/scope/nodes", query={"dimensionId": dimension_id})
    }
    nodes = {}
    for code, name in DEPARTMENTS:
        nodes[code] = _ensure_node(
            admin, state, existing, dimension_id, level["id"], None, code, name
        )
    return nodes


def _ensure_node(
    admin: ApiClient,
    state: dict[str, Any],
    existing: dict[str, dict[str, Any]],
    dimension_id: int,
    level_id: int,
    parent_id: int | None,
    code: str,
    name: str,
) -> dict[str, Any]:
    if code in existing:
        _mark(state, "node", code, existing[code]["id"], False)
        return existing[code]
    created = admin.post(
        "/api/admin/scope/nodes",
        {
            "dimensionId": dimension_id,
            "levelId": level_id,
            "parentId": parent_id,
            "code": code,
            "name": name,
        },
    )
    existing[code] = created
    _mark(state, "node", code, created["id"], True)
    print(f"  node {code} ({name}) id={created['id']}")
    return created


def bind_scopes(
    admin: ApiClient,
    state: dict[str, Any],
    hierarchy: dict[str, Any],
    catalog: str,
) -> list[str]:
    """Bind demo_pg tables. Returns defect notes (empty when all bindings worked)."""
    defects: list[str] = []
    registrations = {
        (r["catalog"], r["schema"], r["table"]): r
        for r in admin.get("/api/admin/lakehouse/registrations")
    }
    geo_levels = hierarchy["geo_levels"]
    dept_id = hierarchy["dept"]["id"]
    level_by_name = {1: geo_levels[1], 2: geo_levels[2], 3: geo_levels[3]}
    name_to_depth = {"district": 1, "taluka": 2, "village": 3}
    for spec in TABLES:
        key = (catalog, SCHEMA, spec["table"])
        row = registrations.get(key)
        if row is None:
            defects.append(f"no {catalog}.{SCHEMA}.{spec['table']} registration to bind")
            continue
        bindings = []
        for level_name, column in spec["bindings"]:
            depth = name_to_depth[level_name]
            bindings.append(
                {"scopeLevelId": level_by_name[depth]["id"], "columnName": column}
            )
        exempt = [dept_id] if spec["exempt_department"] else []
        body = {
            "bindings": bindings,
            "exemptDimensionIds": exempt,
            "sharedReference": spec["shared"],
        }
        current = admin.get(f"/api/admin/lakehouse/registrations/{row['id']}/scope")
        already = (
            bool(current.get("sharedReference")) == bool(spec["shared"])
            and {b["columnName"] for b in current.get("bindings") or []}
            == {b["columnName"] for b in bindings}
            and set(current.get("exemptDimensionIds") or []) == set(exempt)
        )
        if already:
            print(f"  scope {catalog}.{SCHEMA}.{spec['table']} already bound")
            continue
        try:
            admin.put(f"/api/admin/lakehouse/registrations/{row['id']}/scope", body)
            print(f"  scope {catalog}.{SCHEMA}.{spec['table']} shared={spec['shared']} bindings={len(bindings)}")
        except HttpError as exc:
            msg = (
                f"scope bind failed for {catalog}.{SCHEMA}.{spec['table']}: {exc}. "
                "If this is jdbc_* talking to Presto browse, that is a product defect "
                "(TableScopeRegistrationService uses LakehouseBrowseService)."
            )
            defects.append(msg)
            print("  DEFECT: " + msg)
    return defects


def ensure_users(admin: ApiClient, state: dict[str, Any], hierarchy: dict[str, Any]) -> None:
    users = {u["username"].lower(): u for u in admin.get("/api/admin/users")}
    geo_nodes = hierarchy["geo_nodes"]
    specs = [
        {
            "username": "jaipurofficer",
            "email": "jaipur.officer@demo.local",
            "mobile": "9800000001",
            "roles": ["OFFICER"],
            "scope_codes": ["JP"],
            "mfa": True,
            "password": PASSWORDS["jaipurofficer"],
        },
        {
            "username": "sanganeruser",
            "email": "sanganer.user@demo.local",
            "mobile": "9800000002",
            "roles": ["OFFICER"],
            "scope_codes": ["SG"],
            "mfa": False,
            "password": PASSWORDS["sanganeruser"],
        },
        {
            "username": "jaipuradmin",
            "email": "jaipur.admin@demo.local",
            "mobile": "9800000003",
            "roles": ["ADMIN", "AUDIT_READER"],
            "scope_codes": ["JP"],
            "mfa": False,
            "password": PASSWORDS["jaipuradmin"],
        },
    ]
    for spec in specs:
        node_ids = [geo_nodes[code]["id"] for code in spec["scope_codes"]]
        existing = users.get(spec["username"].lower())
        if existing is None:
            created = admin.post(
                "/api/admin/users",
                {
                    "username": spec["username"],
                    "email": spec["email"],
                    "mobile": spec["mobile"],
                    "initialPassword": spec["password"],
                    "roles": spec["roles"],
                    "scopeNodeIds": node_ids,
                    "mfaRequired": False,
                },
            )
            uid = created["id"]
            _mark(state, "user", spec["username"], uid, True)
            print(f"user created {spec['username']} id={uid}")
            _verify_and_maybe_mfa(admin, uid, spec)
            _clear_must_change(spec["username"], spec["password"])
            continue
        uid = existing["id"]
        _mark(state, "user", spec["username"], uid, False)
        admin.put(
            f"/api/admin/users/{uid}",
            {
                "email": spec["email"],
                "mobile": spec["mobile"],
                "roles": spec["roles"],
                "mfaRequired": False,
                "emailVerified": True,
                "mobileVerified": True,
            },
        )
        admin.put(f"/api/admin/users/{uid}/scopes", {"scopeNodeIds": node_ids})
        _verify_and_maybe_mfa(admin, uid, spec)
        print(f"user updated {spec['username']} id={uid} scopes={spec['scope_codes']} mfa={spec['mfa']}")


def _verify_and_maybe_mfa(admin: ApiClient, user_id: int, spec: dict[str, Any]) -> None:
    admin.put(
        f"/api/admin/users/{user_id}",
        {
            "email": spec["email"],
            "mobile": spec["mobile"],
            "roles": spec["roles"],
            "mfaRequired": bool(spec["mfa"]),
            "emailVerified": True,
            "mobileVerified": True,
        },
    )


def _clear_must_change(username: str, password: str) -> None:
    try:
        login_ready(username, password)
    except Exception as exc:  # noqa: BLE001 — first-login hygiene, not fatal to setup
        print(f"  warn: could not complete first login for {username}: {exc}")


def warn_timeouts(admin: ApiClient) -> None:
    configured = os.environ.get("SRSE_QUERY_TIMEOUT_SECONDS", "")
    print("timeouts:")
    print(f"  SRSE_QUERY_TIMEOUT_SECONDS (process env) = {configured or '(unset — backend default 180)'}")
    if configured != "1200":
        print(
            "  WARN: demo fuzzy needs SRSE_QUERY_TIMEOUT_SECONDS=1200 and "
            "SPRING_MVC_ASYNC_REQUEST_TIMEOUT=1260000. See scripts/demo/TIMEOUTS.md"
        )
    try:
        limits = admin.get("/api/analysis/limits")
        print(f"  analysis limits: {limits}")
    except HttpError as exc:
        print(f"  could not read /api/analysis/limits: {exc}")


def reset(admin: ApiClient, state: dict[str, Any]) -> None:
    created = state.get("created", {})
    # Unregister tables we created (jdbc then presto).
    for key in ("jdbc_registration", "presto_registration"):
        for table, rid in list(created.get(key, {}).items()):
            try:
                admin.delete(f"/api/admin/lakehouse/registrations/{rid}")
                print(f"reset: unregistered {table} id={rid}")
            except HttpError as exc:
                print(f"reset: skip unregister {table}: {exc}")
    for name, sid in list(created.get("data_source", {}).items()):
        try:
            admin.delete(f"/api/admin/data-sources/{sid}")
            print(f"reset: deleted data source {name} id={sid}")
        except HttpError as exc:
            print(f"reset: skip data source {name}: {exc}")
    for username, uid in list(created.get("user", {}).items()):
        if username == "superadmin":
            continue
        try:
            admin.post(f"/api/admin/users/{uid}/deactivate")
            print(f"reset: deactivated user {username}")
        except HttpError as exc:
            print(f"reset: skip user {username}: {exc}")
    # Nodes leaf-first. Assignments on reused users may block delete.
    for code, nid in reversed(list(created.get("node", {}).items())):
        try:
            admin.delete(f"/api/admin/scope/nodes/{nid}")
            print(f"reset: deleted node {code}")
        except HttpError as exc:
            print(f"reset: skip node {code}: {exc}")
    save_state({"created": {}, "reused": {}})
    print(f"reset: cleared {STATE_PATH}")


def main() -> int:
    parser = argparse.ArgumentParser(description="DEMO-02 idempotent admin setup")
    parser.add_argument(
        "--reset",
        action="store_true",
        help="Remove only objects this script created, then exit",
    )
    args = parser.parse_args()
    try:
        admin = login_ready(
            os.environ.get("DEMO_SUPERADMIN_USER", "superadmin"),
            PASSWORDS["superadmin"],
        )
    except HttpError as exc:
        if exc.status == 404:
            print(
                "Login endpoint missing — backend is not in srse.auth-mode=local. "
                "See docs/RUNNING_LOCALLY.md §B and docs/demo/DEMO_SCRIPT.md.",
                file=sys.stderr,
            )
            return 2
        print(f"Cannot reach backend at {os.environ.get('SRSE_API_BASE', 'http://localhost:8080')}: {exc}", file=sys.stderr)
        return 2
    except Exception as exc:  # noqa: BLE001
        print(f"Login as superadmin failed: {exc}", file=sys.stderr)
        return 2

    state = load_state()
    if args.reset:
        reset(admin, state)
        return 0

    warn_timeouts(admin)
    source = ensure_data_source(admin, state)
    jdbc_catalog = register_via_data_source(admin, source["id"], state)
    register_via_lakehouse(admin, state)
    print(f"column metadata on {PRESTO_CATALOG_ALIAS} (officer path):")
    upsert_column_metadata(admin, PRESTO_CATALOG_ALIAS)
    print(f"column metadata on {jdbc_catalog} (data-source path, left unbound):")
    try:
        upsert_column_metadata(admin, jdbc_catalog)
    except HttpError as exc:
        print(f"  skip jdbc column metadata (table not visible to browse?): {exc}")
    hierarchy = ensure_dimensions(admin, state)
    defects = bind_scopes(admin, state, hierarchy, PRESTO_CATALOG_ALIAS)
    ensure_users(admin, state, hierarchy)
    save_state(state)
    print("setup complete")
    print(f"  officer catalog: {PRESTO_CATALOG_ALIAS}.{SCHEMA}.* (scoped)")
    print(f"  data-source catalog: {jdbc_catalog}.{SCHEMA}.* (unbound — deny-by-default)")
    if defects:
        print("defects:")
        for item in defects:
            print("  - " + item)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
