# Timeouts between the browser and the engine (DEMO-02)

The only guardrail we raise is the analytical query timeout. Everything else is
either already long enough, is not on the query path, or is documented here so
a 20-minute fuzzy stream is not killed by a shorter hop.

| Hop | File:line | Default | Demo value | Notes |
|---|---|---|---|---|
| Analytical query timeout | `backend/src/main/resources/application.yml:78` (`srse.guardrails.query-timeout-seconds` / `SRSE_QUERY_TIMEOUT_SECONDS`) | **180s** | **1200s** | Applied. Bound onto the shared `JdbcTemplate` before every match/browse (`RecordMatchService` ~1261, `LakehouseBrowseService` ~80, `AnalyticalDataSourceConfig` ~96). |
| Compose env for that property | `docker-compose.yml:68` | was hardcoded `180`; now `${SRSE_QUERY_TIMEOUT_SECONDS:-180}` | set `1200` in `.env` | Restart `srse-backend` after changing. |
| Spring MVC / Tomcat async | `docker-compose.yml:69` → `spring.mvc.async.request-timeout` / `SPRING_MVC_ASYNC_REQUEST_TIMEOUT` | **Tomcat 30s** if unset (`application.yml` does not set this) | **1260000 ms (21 min)** | A 20-minute NDJSON stream is async (`StreamingResponseBody`). Without this, Tomcat cuts the stream at 30s while Presto is still running. |
| Session idle | `application.yml:60` `srse.session.idle-timeout` | **30m** | unchanged | 20-minute query fits. Do not lower. |
| Session absolute | `application.yml:59` | **8h** | unchanged | |
| Multi-target wall budget | `application.yml:99` `SRSE_ANALYSIS_MULTI_MATCH_BUDGET_SECONDS` | **120s** | unchanged | Scenario 8 only. Not a fuzzy-path limit. Do not raise. |
| Join-key distinctness | `application.yml:105` | **10s** | unchanged | Scenario 9 ranking only. |
| Hikari connection acquire (local profile) | `application.yml:126` / `:129` | **3s** | unchanged | Pool checkout, not query runtime. |
| Presto `query.max-run-time` / `query.client.timeout` | `docker/demo/presto/config.properties:8-9` (DEMO-01) | **30m** | 30m (Agent A) | Engine-side; must stay ≥ query timeout. |
| Next.js rewrite / proxy | `frontend/next.config.ts` | none | none | Frontend calls `NEXT_PUBLIC_API_BASE` directly. No proxy hop. |
| Browser `fetch` / `AbortController` | `frontend/src/lib/analysisApi.ts:486` / `ReportAnalysisTab.tsx:1534` | no timer | unchanged | AbortController is user-cancel only. **No frontend source change.** |
| UI render cutoff (not a timeout) | `frontend/src/components/query-builder/ReportAnalysisTab.tsx:224` | 10,000 displayed | unchanged | Past this the grid is not rendered; CSV is the complete result. |
| UI stream parse cap (not a timeout) | same file `:233` | 200,000 | unchanged | Count becomes `200000+`; CSV still complete. |
| Fan-out guard | `application.yml:104` `SRSE_ANALYSIS_MAX_ESTIMATED_ROWS` | **50,000,000** | **unchanged** | Do not raise. |
| Fuzzy blocking prefix | `application.yml:103` `SRSE_ANALYSIS_BLOCKING_PREFIX_LEN` | **3** | **unchanged** | Do not raise. |

## How to apply

```bash
# repo-root .env (docker compose reads it)
SRSE_QUERY_TIMEOUT_SECONDS=1200
SPRING_MVC_ASYNC_REQUEST_TIMEOUT=1260000
SRSE_AUTH_MODE=local
SRSE_PRESTO_URL=jdbc:presto://host.docker.internal:8083
SRSE_OTP_SENDER=log
```

Host-run backend (add the Spring async flag — it is not in `application.yml`):

```bash
java -jar backend/target/srse-backend-0.1.0.jar \
  --srse.auth-mode=local \
  --srse.otp.sender=log \
  --srse.guardrails.query-timeout-seconds=1200 \
  --spring.mvc.async.request-timeout=1260000 \
  --srse.presto.url=jdbc:presto://localhost:8083
```

Restart the backend after changing either value. `scripts/demo/setup.sh` warns
when `SRSE_QUERY_TIMEOUT_SECONDS` is not `1200`.
