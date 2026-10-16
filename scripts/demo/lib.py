"""Shared HTTP client for DEMO-02 setup and scenario scripts.

Stdlib only. Talks to the same admin/officer APIs the UI uses.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
STATE_PATH = Path(__file__).resolve().parent / ".setup-state.json"
DEMO_ENV_EXAMPLE = Path(__file__).resolve().parent / "demo.env.example"

SOURCE_SYSTEM = "Rajasthan Welfare Demo"
DATA_SOURCE_NAME = "Demo Business DB"
PRESTO_CATALOG_ALIAS = "demo_pg"
SCHEMA = "demo"

PASSWORDS = {
    "superadmin": os.environ.get("DEMO_SUPERADMIN_PASSWORD", "Supradmin@123"),
    "jaipurofficer": os.environ.get("DEMO_JAIPUR_OFFICER_PASSWORD", "JaiOffic$1x"),
    "sanganeruser": os.environ.get("DEMO_SANGANER_PASSWORD", "Officer$New1"),
    "jaipuradmin": os.environ.get("DEMO_JAIPUR_ADMIN_PASSWORD", "JaipurNew$1"),
}

DISTRICTS = [
    ("JP", "Jaipur"),
    ("JO", "Jodhpur"),
    ("UD", "Udaipur"),
    ("KO", "Kota"),
    ("AJ", "Ajmer"),
    ("BI", "Bikaner"),
    ("AL", "Alwar"),
]
DEPARTMENTS = [
    ("D01", "Social Justice"),
    ("D02", "Rural Development"),
    ("D03", "Food and Civil Supplies"),
    ("D04", "Medical and Health"),
    ("D05", "Education"),
]

OTP_CODE_RE = re.compile(r"Your SRSE verification code is: (\d{6})")


def api_base() -> str:
    return os.environ.get("SRSE_API_BASE", "http://localhost:8080").rstrip("/")


def http_timeout() -> float:
    return float(os.environ.get("DEMO_HTTP_TIMEOUT_SECONDS", "1260"))


@dataclass
class HttpError(Exception):
    status: int
    body: str
    url: str

    def __str__(self) -> str:
        return f"HTTP {self.status} {self.url}: {self.body[:800]}"


@dataclass
class ApiClient:
    token: str | None = None
    timeout: float = field(default_factory=http_timeout)

    def with_token(self, token: str | None) -> "ApiClient":
        return ApiClient(token=token, timeout=self.timeout)

    def request(
        self,
        method: str,
        path: str,
        body: Any = None,
        query: dict[str, Any] | None = None,
        accept: str | None = None,
        raw: bool = False,
    ) -> Any:
        url = api_base() + path
        if query:
            url += "?" + urllib.parse.urlencode(
                {k: v for k, v in query.items() if v is not None}
            )
        data = None
        headers = {}
        if accept:
            headers["Accept"] = accept
        elif not raw:
            headers["Accept"] = "application/json"
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                payload = resp.read()
                if raw:
                    return payload
                if not payload:
                    return None
                ctype = resp.headers.get("Content-Type", "")
                if "json" in ctype:
                    return json.loads(payload.decode("utf-8"))
                return payload.decode("utf-8")
        except urllib.error.HTTPError as exc:
            err_body = exc.read().decode("utf-8", errors="replace")
            raise HttpError(exc.code, err_body, url) from exc

    def get(self, path: str, **kwargs: Any) -> Any:
        return self.request("GET", path, **kwargs)

    def post(self, path: str, body: Any = None, **kwargs: Any) -> Any:
        return self.request("POST", path, body=body, **kwargs)

    def put(self, path: str, body: Any = None, **kwargs: Any) -> Any:
        return self.request("PUT", path, body=body, **kwargs)

    def delete(self, path: str, **kwargs: Any) -> Any:
        return self.request("DELETE", path, **kwargs)

    def stream_ndjson(
        self, path: str, body: Any, *, keep_rows: int = 200
    ) -> list[dict[str, Any]]:
        """Stream NDJSON. Keep meta/progress/done/error plus the first keep_rows row events."""
        url = api_base() + path
        data = json.dumps(body).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/x-ndjson",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        req = urllib.request.Request(url, data=data, headers=headers, method="POST")
        events: list[dict[str, Any]] = []
        kept_rows = 0
        extra_rows = 0
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                for raw_line in resp:
                    line = raw_line.decode("utf-8", errors="replace").strip()
                    if not line:
                        continue
                    event = json.loads(line)
                    if event.get("type") == "row":
                        if kept_rows < keep_rows:
                            events.append(event)
                            kept_rows += 1
                        else:
                            extra_rows += 1
                    else:
                        events.append(event)
        except urllib.error.HTTPError as exc:
            err_body = exc.read().decode("utf-8", errors="replace")
            raise HttpError(exc.code, err_body, url) from exc
        if extra_rows:
            events.append({"type": "_truncated_rows", "dropped": extra_rows})
        return events


def load_state() -> dict[str, Any]:
    if not STATE_PATH.exists():
        return {"created": {}, "reused": {}}
    return json.loads(STATE_PATH.read_text())


def save_state(state: dict[str, Any]) -> None:
    STATE_PATH.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n")


def read_otp_from_source(since_epoch: float) -> str | None:
    source = os.environ.get("DEMO_OTP_SOURCE", "docker:srse-backend")
    if source.startswith("file:"):
        path = Path(source[5:])
        if not path.exists():
            return None
        text = path.read_text(errors="replace")
        matches = OTP_CODE_RE.findall(text)
        return matches[-1] if matches else None
    container = source.split(":", 1)[1] if source.startswith("docker:") else source
    since = max(int(time.time() - since_epoch) + 2, 2)
    try:
        proc = subprocess.run(
            ["docker", "logs", "--since", f"{since}s", container],
            check=False,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError:
        return None
    text = (proc.stdout or "") + "\n" + (proc.stderr or "")
    if proc.returncode != 0 and "No such container" in text:
        # Compose project prefixes the service name.
        alt = _guess_backend_container()
        if alt and alt != container:
            proc = subprocess.run(
                ["docker", "logs", "--since", f"{since}s", alt],
                check=False,
                capture_output=True,
                text=True,
            )
            text = (proc.stdout or "") + "\n" + (proc.stderr or "")
    matches = OTP_CODE_RE.findall(text)
    return matches[-1] if matches else None


def _guess_backend_container() -> str | None:
    proc = subprocess.run(
        ["docker", "ps", "--format", "{{.Names}}"],
        check=False,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        return None
    for name in proc.stdout.splitlines():
        if "srse-backend" in name or "backend" in name:
            return name
    return None


def login(username: str, password: str, client: ApiClient | None = None) -> str:
    """Return a session token, completing MFA from the backend log when needed."""
    anon = client or ApiClient()
    started = time.time()
    result = anon.post("/api/auth/login", {"username": username, "password": password})
    if result.get("token"):
        token = result["token"]
        if result.get("mustChangePassword"):
            token = _finish_password_change(anon.with_token(token), password)
        return token
    challenge = result.get("mfaChallengeId")
    if not challenge:
        raise RuntimeError(f"Login failed for {username}: {result}")
    code = None
    for _ in range(40):
        time.sleep(0.5)
        code = read_otp_from_source(started)
        if code:
            break
    if not code:
        raise RuntimeError(
            f"MFA challenge for {username} but no OTP in {os.environ.get('DEMO_OTP_SOURCE', 'docker:srse-backend')}. "
            "Start the backend with --srse.otp.sender=log and set DEMO_OTP_SOURCE."
        )
    verified = anon.post(
        "/api/auth/verify-otp", {"challengeId": challenge, "code": code}
    )
    if not verified.get("token"):
        raise RuntimeError(f"OTP verify failed for {username}: {verified}")
    token = verified["token"]
    if verified.get("mustChangePassword"):
        token = _finish_password_change(anon.with_token(token), password)
    return token


def _finish_password_change(authed: ApiClient, password: str) -> str:
    # Same password is fine — we only need to clear mustChangePassword.
    authed.post(
        "/api/auth/change-password",
        {"currentPassword": password, "newPassword": password},
    )
    # Session version incremented; re-login.
    # Caller username is not on the client — parse is not available; the
    # change-password path is only used right after login, so the next login
    # is done by the caller. Here we return the old token which is now stale.
    raise RuntimeError(
        "Password change required; caller must login again after change-password"
    )


def login_ready(username: str, password: str) -> ApiClient:
    """Login, clear must-change-password if needed, return an authenticated client."""
    anon = ApiClient()
    started = time.time()
    result = anon.post("/api/auth/login", {"username": username, "password": password})
    token = result.get("token")
    challenge = result.get("mfaChallengeId")
    if challenge:
        code = None
        for _ in range(40):
            time.sleep(0.5)
            code = read_otp_from_source(started)
            if code:
                break
        if not code:
            raise RuntimeError(
                f"MFA challenge for {username} but no OTP in logs "
                f"({os.environ.get('DEMO_OTP_SOURCE', 'docker:srse-backend')})"
            )
        result = anon.post(
            "/api/auth/verify-otp", {"challengeId": challenge, "code": code}
        )
        token = result.get("token")
    if not token:
        raise RuntimeError(f"Login failed for {username}: {result}")
    client = anon.with_token(token)
    if result.get("mustChangePassword"):
        client.post(
            "/api/auth/change-password",
            {"currentPassword": password, "newPassword": password},
        )
        return login_ready(username, password)
    return client


def table_ref(catalog: str, table: str, column: str | None = None) -> dict[str, str]:
    ref = {"catalog": catalog, "schema": SCHEMA, "table": table}
    if column is not None:
        ref["column"] = column
    return ref


def criterion(
    catalog: str,
    table: str,
    column: str,
    fuzzy: float | None = None,
) -> dict[str, Any]:
    item: dict[str, Any] = table_ref(catalog, table, column)
    item["fuzzyThresholdPercent"] = fuzzy
    return item


def match_events_summary(events: list[dict[str, Any]]) -> dict[str, Any]:
    meta = next((e for e in events if e.get("type") == "meta"), {})
    done = next((e for e in events if e.get("type") == "done"), {})
    error = next((e for e in events if e.get("type") == "error"), None)
    rows = [e.get("data") or {} for e in events if e.get("type") == "row"]
    progress = [e for e in events if e.get("type") == "progress"]
    return {
        "columns": meta.get("columns") or [],
        "sql": meta.get("sql"),
        "total_rows": done.get("totalRows"),
        "per_target": done.get("perTarget"),
        "error": error.get("message") if error else None,
        "rows": rows,
        "progress": progress,
    }


def count_from_events(events: list[dict[str, Any]]) -> int | None:
    summary = match_events_summary(events)
    if summary["error"]:
        return None
    rows = summary["rows"]
    # countOnly streams one row; done.totalRows is 1 (the COUNT row), not the join size.
    if len(rows) == 1:
        for key in ("row_count", "ROW_COUNT", "rowCount"):
            if key in rows[0] and rows[0][key] is not None:
                return int(rows[0][key])
    if summary["total_rows"] is not None:
        return int(summary["total_rows"])
    return len(rows)
