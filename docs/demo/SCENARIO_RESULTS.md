# DEMO-02 scenario results

Generated 2026-10-16 11:45:13Z by `scripts/demo/run-scenarios.py`.

- API: `http://localhost:8080`
- Catalog: `demo_pg.demo`
- Started: 2026-10-16T11:44:52Z
- Query timeout env: `1200`
- Scenarios **2** and **3**: fuzzy name ≥85% **and exact mobile**. Scenario **2b** (name-only) skipped — prefix-3 fan-out ~735M exceeds the 50M guard.

| # | Scenario | User | Result | Rows | Wall (s) | Notes |
|---|----------|------|--------|------|----------|-------|
| 1 | INNER / LEFT / RIGHT / FULL + NULL verdicts | superadmin | **PASS** | 855000 | 13.64 | INNER=855000 LEFT=1004250 RIGHT=900000 FULL=1049250; INNER rows=855000 wall=13.63s; LEFT rows=1004250 wall=12.29s |
| 2 | Fuzzy name ≥85% + exact mobile, unscoped | superadmin | **PASS** | 675387 | 14.73 | unscoped fuzzy+mobile rows=675387 wall=14.73s fan-out-guard=passed; 2b (name-only) skipped — ~735M prefix-3 fan-out exceeds the 50M guard. |
| 3 | Same fuzzy+mobile, scoped | jaipurofficer | **PASS** | 222170 | 10.61 | jaipurofficer fuzzy+mobile rows=222170 unscoped=675387 wall=10.61s fan-out-guard=passed |
| 4 | COMBINE full_name vs first+last @ 85% + exact mobile | superadmin | **PASS** | 36274 | 20.42 | COMBINE+mobile rows=36274 |
| 5 | ANY_OF mobile vs phones (INNER) | superadmin | **PASS** | 300000 | 12.57 | ANY_OF INNER rows=300000 |
| 6 | Post-join compare + mismatchOnly + fuzzy cmp | superadmin | **PASS** | 328815 | 41.03 | mismatchOnly=328815 all=1004250 |
| 7 | Dedup / group-by / filters / value pickers | superadmin | **PASS** | 7 | 23.39 | group-by districts=7 picker_values=7 dedup_rows=370515 |
| 8 | Multi-target + failing target | superadmin | **PASS** | 1155000 | 27.83 | per_target={'bank': {'rows': 855000, 'label': 'bank', 'status': 'ok'}, 'pension': {'rows': 300000, 'label': 'pension', 'status': 'ok'}} fanout=http-400; PRODUCT DEFECT: Analysis... |
| 9 | Suggest-keys metadata and probe | superadmin | **PASS** | 20 | 25.01 | meta=20 probe=20; top metadata: [{'sourceColumn': 'district_code', 'targetColumn': 'district_code', 'sourceType': 'varchar(4)', 'targetType': 'varchar(4)', 'reason': 'Same colum... |
| 10 | Fan-out refusal + complete CSV + 10k cutoff | superadmin | **PASS** | 855000 | 28.71 | fan-out refused; csv_rows=855000; csv_rows=855000 csv_wall=20.82s bytes=87208273; UI render cutoff is frontend MAX_DISPLAYED_ROWS=10000 (ReportAnalysisTab.tsx:224) |
| 11 | Scoping superadmin / jaipur / sanganer + unbound + shared-ref | all | **PASS** | 273579 |  | counts={'superadmin': 855000, 'jaipurofficer': 273579, 'sanganeruser': 'HTTP 400'} shared jaipur=320000 superadmin=1000000; superadmin rows=855000 wall=14.66s; jaipurofficer row... |
| 12 | Audit shape-only + jaipuradmin subtree | superadmin, jaipuradmin | **PASS** | 93 |  | superadmin_events=93 jaipuradmin_events=27 actions=['EXPORT', 'LOGIN_SUCCESS', 'MFA_CHALLENGE_ISSUED', 'MFA_VERIFIED', 'QUERY_EXECUTED', 'QUERY_FINISHED', 'QUERY_PREVIEWED', 'QU... |
| 13 | Saved query + CSV/XLSX export | superadmin | **PASS** | 500 | 16.67 | savedQueryId=155 rerun_rows=500 csv=49549 xlsx=30172 |

## Assertions and SQL

### 1. INNER / LEFT / RIGHT / FULL + NULL verdicts

- User: `superadmin`
- Result: **PASS**
- Rows: 855000
- Wall time: 13.64s
- OK: INNER count ran: None
- OK: INNER count > 0 (got 855000)
- OK: LEFT count ran: None
- OK: LEFT count > 0 (got 1004250)
- OK: RIGHT count ran: None
- OK: RIGHT count > 0 (got 900000)
- OK: FULL count ran: None
- OK: FULL count > 0 (got 1049250)
- OK: LEFT (1004250) >= INNER (855000)
- OK: RIGHT (900000) >= INNER (855000)
- OK: FULL (1049250) >= LEFT/RIGHT
- OK: INNER SQL uses bare JOIN
- OK: LEFT emits match_status
- INNER rows=855000 wall=13.63s
- LEFT rows=1004250 wall=12.29s
- RIGHT rows=900000 wall=11.75s
- FULL rows=1049250 wall=11.27s
- no NO_TARGET row in first 80 LEFT rows — count relationship still asserted

```sql
SELECT COUNT(*) AS row_count FROM (SELECT src.beneficiary_id AS "source_beneficiary_id", tgt.beneficiary_ref AS "target_beneficiary_ref", src.full_name AS "source_full_name", src.district_code AS "source_district_code", src.annual_income AS "source_annual_income", src.status AS "source_status", tgt.account_id AS "target_account_id", tgt.account_holder_name AS "target_account_holder_name", tgt.declared_income AS "target_declared_income", tgt.status AS "target_status" FROM demo_pg.demo.beneficiary src JOIN demo_pg.demo.bank_account tgt ON src.beneficiary_id = TRY_CAST(tgt.beneficiary_ref AS DOUBLE) WHERE TRUE) __cnt
```

### 2. Fuzzy name ≥85% + exact mobile, unscoped

- User: `superadmin`
- Result: **PASS**
- Rows: 675387
- Wall time: 14.73s
- OK: fuzzy count ran: None
- OK: fuzzy unscoped rows > 0 (got 675387)
- OK: emitted SQL is fuzzy (Levenshtein)
- OK: emitted SQL also joins exact mobile
- OK: fan-out guard passed (query executed, not refused): None
- 2b (name-only, no mobile) skipped — Agent A profile estimates ~735M prefix-3 fan-out, above the 50M guard.
- fan-out guard passed=True

```sql
SELECT COUNT(*) AS row_count FROM (SELECT src.full_name AS "source_full_name", src.mobile AS "source_mobile", tgt.account_holder_name AS "target_account_holder_name", tgt.mobile AS "target_mobile", src.beneficiary_id AS "source_beneficiary_id", src.district_code AS "source_district_code", tgt.account_id AS "target_account_id" FROM demo_pg.demo.beneficiary src JOIN demo_pg.demo.bank_account tgt ON substr(lower(src.full_name), 1, 3) = substr(lower(tgt.account_holder_name), 1, 3) AND src.mobile = tgt.mobile WHERE (1.0 - CAST(levenshtein_distance(lower(src.full_name), lower(tgt.account_holder_name)) AS DOUBLE) / GREATEST(length(src.full_name), length(tgt.account_holder_name), 1)) >= 0.85) __cnt
```

### 3. Same fuzzy+mobile, scoped

- User: `jaipurofficer`
- Result: **PASS**
- Rows: 222170
- Wall time: 10.61s
- OK: scoped fuzzy ran: None
- OK: scoped fuzzy returned a count (got 222170)
- OK: scoped (222170) < unscoped (675387)
- OK: scoped SQL mentions district filter
- OK: fan-out guard passed for jaipurofficer: None
- fan-out guard passed=True

```sql
SELECT COUNT(*) AS row_count FROM (SELECT src.full_name AS "source_full_name", src.mobile AS "source_mobile", tgt.account_holder_name AS "target_account_holder_name", tgt.mobile AS "target_mobile", src.beneficiary_id AS "source_beneficiary_id", src.district_code AS "source_district_code", tgt.account_id AS "target_account_id" FROM (SELECT * FROM demo_pg.demo.beneficiary t WHERE district_code IN ('JP')) src JOIN (SELECT * FROM demo_pg.demo.bank_account t WHERE district_code IN ('JP')) tgt ON substr(lower(src.full_name), 1, 3) = substr(lower(tgt.account_holder_name), 1, 3) AND src.mobile = tgt.mobile WHERE (1.0 - CAST(levenshtein_distance(lower(src.full_name), lower(tgt.account_holder_name)) AS DOUBLE) / GREATEST(length(src.full_name), length(tgt.account_holder_name), 1)) >= 0.85) __cnt
```

### 4. COMBINE full_name vs first+last @ 85% + exact mobile

- User: `superadmin`
- Result: **PASS**
- Rows: 36274
- Wall time: 20.42s
- OK: COMBINE ran: None
- OK: COMBINE rows > 0 (got 36274)
- OK: COMBINE rows under 10M (got 36274)
- OK: COMBINE SQL folds with array_join
- OK: COMBINE SQL also joins exact mobile to phone_primary

```sql
SELECT COUNT(*) AS row_count FROM (SELECT src.full_name AS "source_full_name", src.mobile AS "source_mobile", tgt.first_name AS "target_first_name", tgt.last_name AS "target_last_name", tgt.phone_primary AS "target_phone_primary", src.beneficiary_id AS "source_beneficiary_id", tgt.pension_id AS "target_pension_id" FROM demo_pg.demo.beneficiary src JOIN demo_pg.demo.pension_register tgt ON substr(lower(src.full_name), 1, 3) = substr(lower(array_join(filter(ARRAY[CAST(tgt.first_name AS VARCHAR), CAST(tgt.last_name AS VARCHAR)], x -> x IS NOT NULL AND x <> ''), ' ')), 1, 3) AND src.mobile = tgt.phone_primary WHERE (1.0 - CAST(levenshtein_distance(lower(src.full_name), lower(array_join(filter(ARRAY[CAST(tgt.first_name AS VARCHAR), CAST(tgt.last_name AS VARCHAR)], x -> x IS NOT NULL AND x <> ''), ' '))) AS DOUBLE) / GREATEST(length(src.full_name), length(array_join(filter(ARRAY[CAST(tgt.first_name AS VARCHAR), CAST(tgt.last_name AS VARCHAR)], x -> x IS NOT NULL AND x <> ''), ' ')), 1)) >= 0.85) __cnt
```

### 5. ANY_OF mobile vs phones (INNER)

- User: `superadmin`
- Result: **PASS**
- Rows: 300000
- Wall time: 12.57s
- OK: ANY_OF ran: None
- OK: ANY_OF rows > 0 (got 300000)
- OK: ANY_OF uses CROSS JOIN UNNEST, not OR
- OK: no OR in ON clause

```sql
SELECT COUNT(*) AS row_count FROM (SELECT src.mobile AS "source_mobile", tgt.phone_primary AS "target_phone_primary", tgt.phone_secondary AS "target_phone_secondary", src.beneficiary_id AS "source_beneficiary_id", tgt.pension_id AS "target_pension_id", tgt.g0_matched_on AS "target_g0_matched_on" FROM demo_pg.demo.beneficiary src JOIN (SELECT t.*, g0_key, g0_matched_on FROM demo_pg.demo.pension_register t CROSS JOIN UNNEST(ARRAY[t.phone_primary, t.phone_secondary], ARRAY['phone_primary', 'phone_secondary']) AS u0 (g0_key, g0_matched_on)) tgt ON src.mobile = tgt.g0_key WHERE TRUE) __cnt
```

### 6. Post-join compare + mismatchOnly + fuzzy cmp

- User: `superadmin`
- Result: **PASS**
- Rows: 328815
- Wall time: 41.03s
- OK: comparison sample ran: None
- OK: income and status verdicts projected
- OK: fuzzy comparison projects score_pct
- OK: mismatchOnly (328815) < all LEFT comparisons (1004250)

```sql
SELECT COUNT(*) AS row_count FROM (SELECT src.beneficiary_id AS "source_beneficiary_id", tgt.beneficiary_ref AS "target_beneficiary_ref", src.full_name AS "source_full_name", src.district_code AS "source_district_code", src.annual_income AS "source_annual_income", src.status AS "source_status", tgt.account_id AS "target_account_id", tgt.account_holder_name AS "target_account_holder_name", tgt.declared_income AS "target_declared_income", tgt.status AS "target_status", (CASE WHEN tgt.beneficiary_ref IS NULL THEN 'NO_TARGET' ELSE 'MATCHED' END) AS "match_status", (src.annual_income) AS "cmp_0_source", (tgt.declared_income) AS "cmp_0_target", (CASE WHEN tgt.beneficiary_ref IS NULL THEN NULL ELSE ((NOT (src.annual_income IS DISTINCT FROM tgt.declared_income))) END) AS "cmp_0_match", (src.status) AS "cmp_1_source", (tgt.status) AS "cmp_1_target", (CASE WHEN tgt.beneficiary_ref IS NULL THEN NULL ELSE ((NOT (src.status IS DISTINCT FROM tgt.status))) END) AS "cmp_1_match", (src.full_name) AS "cmp_2_source", (tgt.account_holder_name) AS "cmp_2_target", (CASE WHEN tgt.beneficiary_ref IS NULL THEN NULL ELSE (((1.0 - CAST(levenshtein_distance(lower(src.full_name), lower(tgt.account_holder_name)) AS DOUBLE) / GREATEST(length(src.full_name), length(tgt.account_holder_name), 1)) >= 0.8)) END) AS "cmp_2_match", (CASE WHEN tgt.beneficiary_ref IS NULL THEN NULL ELSE (ROUND(((1.0 - CAST(levenshtein_distance(lower(src.full_name), lower(tgt.account_holder_name)) AS DOUBLE) / GREATEST(length(src.full_name), length(tgt.account_holder_name), 1))) * 100, 1)) END) AS "cmp_2_score_pct" FROM demo_pg.demo.beneficiary src LEFT JOIN demo_pg.demo.bank_account tgt ON src.beneficiary_id = TRY_CAST(tgt.beneficiary_ref AS DOUBLE) WHERE (tgt.beneficiary_ref IS NULL OR NOT (((NOT (src.annual_income IS DISTINCT FROM tgt.declared_income))) AND ((NOT (src.status IS DISTINCT FROM tgt.status))) AND (((1.0 - CAST(levenshtein_distance(lower(src.full_name), lower(tgt.account_holder_name)) AS DOUBLE) / GREATEST(length(src.full_name), length(tgt.account_holder_name), 1)) >= 0.8))))) __cnt
```

### 7. Dedup / group-by / filters / value pickers

- User: `superadmin`
- Result: **PASS**
- Rows: 7
- Wall time: 23.39s
- OK: value picker includes JP (got ['AJ', 'AL', 'BI', 'JO', 'JP', 'KO', 'UD'])
- OK: group-by ran: None
- OK: group-by returned district rows
- OK: SQL contains GROUP BY
- OK: filter reached SQL
- OK: dedup count > 0 (got 370515)

```sql
SELECT COALESCE(CAST(src.district_code AS VARCHAR), '(NULL)') AS "src_district_code", count(*) AS "n" FROM (SELECT * FROM demo_pg.demo.beneficiary t WHERE t.status = 'ACTIVE') src JOIN demo_pg.demo.ration_card tgt ON src.beneficiary_id = tgt.beneficiary_id WHERE TRUE GROUP BY src.district_code LIMIT 50
```

### 8. Multi-target + failing target

- User: `superadmin`
- Result: **PASS**
- Rows: 1155000
- Wall time: 27.83s
- OK: bank target ran: {'rows': 855000, 'label': 'bank', 'status': 'ok'}
- OK: pension target ran: {'rows': 300000, 'label': 'pension', 'status': 'ok'}
- OK: at least one target produced rows: {'bank': {'rows': 855000, 'label': 'bank', 'status': 'ok'}, 'pension': {'rows': 300000, 'label': 'pension', 'status': 'ok'}}
- OK: failing target refused with estimate (in-band or HTTP 400): Estimated match fan-out is about 450,000,000,000 rows processed before the join (limit 50,000,000). Try a longer blocking prefix (SRSE_ANALYSIS_BLOCKING_PREFIX_LEN), a more selective join key, or fewer folded groups.
- PRODUCT DEFECT: AnalysisAuditService.planMultiMatchAudited pre-plans every target, so a fan-out on one target is HTTP 400 rather than an in-band NDJSON error. Failing request: hub beneficiary_id + targets bank (beneficiary_ref) and fanout (status). Estimate was in the 400 body.

```sql
SELECT src.beneficiary_id AS "source_beneficiary_id", tgt.beneficiary_ref AS "target_beneficiary_ref", src.full_name AS "source_full_name", tgt.account_id AS "target_account_id" FROM demo_pg.demo.beneficiary src JOIN demo_pg.demo.bank_account tgt ON src.beneficiary_id = TRY_CAST(tgt.beneficiary_ref AS DOUBLE) WHERE TRUE
```

### 9. Suggest-keys metadata and probe

- User: `superadmin`
- Result: **PASS**
- Rows: 20
- Wall time: 25.01s
- OK: metadata suggestions non-empty (20)
- OK: probe suggestions non-empty
- top metadata: [{'sourceColumn': 'district_code', 'targetColumn': 'district_code', 'sourceType': 'varchar(4)', 'targetType': 'varchar(4)', 'reason': 'Same column name, both TEXT'}, {'sourceColumn': 'mobile', 'targetColumn': 'mobile', 'sourceType': 'varchar(10)', 'targetType': 'varchar(10)', 'reason': 'Same column name, both TEXT'}, {'sourceColumn': 'status', 'targetColumn': 'status', 'sourceType': 'varchar(16)', 'targetType': 'varchar(16)', 'reason': 'Same column name, both TEXT'}]
- top probe: [{'sourceColumn': 'full_name', 'targetColumn': 'account_holder_name', 'sourceType': 'varchar(256)', 'targetType': 'varchar(256)', 'reason': '85% of sampled source values found in target (source TABLESAMPLE BERNOULLI 10%, target scanned in full; approx_distinct estimate — not a row count)'}, {'sourceColumn': 'beneficiary_id', 'targetColumn': 'beneficiary_ref', 'sourceType': 'bigint', 'targetType': 'varchar(12)', 'reason': '84% of sampled source values found in target (source TABLESAMPLE BERNOULLI 10%, target scanned in full; approx_distinct estimate — not a row count)'}, {'sourceColumn': 'mobile', 'targetColumn': 'mobile', 'sourceType': 'varchar(10)', 'targetType': 'varchar(10)', 'reason': '72% of sampled source values found in target (source TABLESAMPLE BERNOULLI 10%, target scanned in full; approx_distinct estimate — not a row count)'}]

### 10. Fan-out refusal + complete CSV + 10k cutoff

- User: `superadmin`
- Result: **PASS**
- Rows: 855000
- Wall time: 28.71s
- OK: fan-out refused with estimate in the message (got: Estimated match fan-out is about 450,000,000,000 rows processed before the join (limit 50,000,000). Try a longer blocking prefix (SRSE_ANALYSIS_BLOCKING_PREFIX_LEN), a more selective join key, or fewer folded groups.)
- OK: CSV export has data rows (got 855000)
- OK: CSV is not truncated at the 10k render cutoff
- csv_rows=855000 csv_wall=20.82s bytes=87208273
- UI render cutoff is frontend MAX_DISPLAYED_ROWS=10000 (ReportAnalysisTab.tsx:224)

### 11. Scoping superadmin / jaipur / sanganer + unbound + shared-ref

- User: `all`
- Result: **PASS**
- Rows: 273579
- OK: superadmin count > 0 (855000)
- OK: jaipurofficer (273579) < superadmin (855000)
- OK: sanganeruser is denied or strictly smaller than Jaipur (got HTTP 400)
- OK: unbound jdbc_* table denied to jaipurofficer
- OK: shared-ref join still scoped: jaipur 320000 < superadmin 1000000
- superadmin rows=855000 wall=14.66s
- jaipurofficer rows=273579 wall=17.01s
- sanganeruser HTTP 400: Table is not visible in your scope — bind scope columns, exempt dimensions, or mark shared reference

```sql
SELECT COUNT(*) AS row_count FROM (SELECT src.beneficiary_id AS "source_beneficiary_id", tgt.beneficiary_ref AS "target_beneficiary_ref", src.full_name AS "source_full_name", src.district_code AS "source_district_code", src.annual_income AS "source_annual_income", src.status AS "source_status", tgt.account_id AS "target_account_id", tgt.account_holder_name AS "target_account_holder_name", tgt.declared_income AS "target_declared_income", tgt.status AS "target_status" FROM (SELECT * FROM demo_pg.demo.beneficiary t WHERE district_code IN ('JP')) src JOIN (SELECT * FROM demo_pg.demo.bank_account t WHERE district_code IN ('JP')) tgt ON src.beneficiary_id = TRY_CAST(tgt.beneficiary_ref AS DOUBLE) WHERE TRUE) __cnt
```

### 12. Audit shape-only + jaipuradmin subtree

- User: `superadmin, jaipuradmin`
- Result: **PASS**
- Rows: 93
- OK: audit returned events since 2026-10-16T11:26:40Z (page keys=['entries', 'page', 'size', 'totalElements', 'totalPages', 'retentionDays', 'retentionNote'])
- OK: query actions present: ['EXPORT', 'LOGIN_SUCCESS', 'MFA_CHALLENGE_ISSUED', 'MFA_VERIFIED', 'QUERY_EXECUTED', 'QUERY_FINISHED', 'QUERY_PREVIEWED', 'QUERY_REFUSED']
- OK: audit detail has no officer mobile
- OK: query shapes look like SQL with placeholders, not a value dump
- OK: jaipuradmin sees Jaipur-subtree actors ({'sanganeruser', 'jaipurofficer', 'jaipuradmin'})
- OK: jaipuradmin does not see unscoped superadmin ({'sanganeruser', 'jaipurofficer', 'jaipuradmin'})

### 13. Saved query + CSV/XLSX export

- User: `superadmin`
- Result: **PASS**
- Rows: 500
- Wall time: 16.67s
- OK: saved query created id=155
- OK: saved query reloads
- OK: re-running saved query returned rows (500)
- OK: CSV export non-empty
- OK: XLSX export non-empty

```sql
SELECT src.beneficiary_id AS "source_beneficiary_id", tgt.beneficiary_ref AS "target_beneficiary_ref", src.full_name AS "source_full_name", src.district_code AS "source_district_code", src.annual_income AS "source_annual_income", src.status AS "source_status", tgt.account_id AS "target_account_id", tgt.account_holder_name AS "target_account_holder_name", tgt.declared_income AS "target_declared_income", tgt.status AS "target_status" FROM demo_pg.demo.beneficiary src JOIN demo_pg.demo.bank_account tgt ON src.beneficiary_id = TRY_CAST(tgt.beneficiary_ref AS DOUBLE) WHERE TRUE LIMIT 500
```

