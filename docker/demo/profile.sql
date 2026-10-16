-- Profile queries for docs/demo/DATA_PROFILE.md (run against analytics_demo via psql).

\echo '=== row_counts ==='
SELECT 'beneficiary' AS tbl, count(*) AS n FROM demo.beneficiary
UNION ALL SELECT 'bank_account', count(*) FROM demo.bank_account
UNION ALL SELECT 'pension_register', count(*) FROM demo.pension_register
UNION ALL SELECT 'ration_card', count(*) FROM demo.ration_card
UNION ALL SELECT 'district_master', count(*) FROM demo.district_master;

\echo '=== bank_overlap ==='
WITH bank AS (
  SELECT ba.account_id,
         b.beneficiary_id IS NOT NULL AS links_beneficiary
  FROM demo.bank_account ba
  LEFT JOIN demo.beneficiary b
    ON b.beneficiary_id = NULLIF(ltrim(ba.beneficiary_ref, '0'), '')::bigint
)
SELECT
  round(100.0 * avg(CASE WHEN links_beneficiary THEN 1 ELSE 0 END), 2) AS pct_bank_rows_linked,
  round(100.0 * (1 - avg(CASE WHEN links_beneficiary THEN 1 ELSE 0 END)), 2) AS pct_orphan_bank_rows
FROM bank;

\echo '=== beneficiaries_with_bank (distinct linked beneficiaries) ==='
WITH linked AS (
  SELECT DISTINCT NULLIF(ltrim(ba.beneficiary_ref, '0'), '')::bigint AS beneficiary_id
  FROM demo.bank_account ba
  WHERE NULLIF(ltrim(ba.beneficiary_ref, '0'), '') IS NOT NULL
)
SELECT round(
  100.0 * (SELECT count(*) FROM linked l JOIN demo.beneficiary b USING (beneficiary_id))
  / nullif((SELECT count(*) FROM demo.beneficiary), 0),
  2
) AS pct_beneficiaries_with_bank;

\echo '=== bank_mobile_match (linked rows only) ==='
SELECT round(100.0 * avg(CASE WHEN ba.mobile = b.mobile THEN 1 ELSE 0 END), 2) AS pct_linked_mobile_match
FROM demo.bank_account ba
JOIN demo.beneficiary b
  ON b.beneficiary_id = NULLIF(ltrim(ba.beneficiary_ref, '0'), '')::bigint;

\echo '=== full_name_quality ==='
WITH counts AS (
  SELECT full_name, count(*) AS c FROM demo.beneficiary GROUP BY full_name
)
SELECT
  (SELECT count(*) FROM demo.beneficiary WHERE trim(full_name) = '') AS blank_names,
  max(c) AS max_full_name_rows,
  round(100.0 * (SELECT count(DISTINCT full_name) FROM demo.beneficiary)
    / nullif((SELECT count(*) FROM demo.beneficiary), 0), 2) AS pct_distinct_full_names,
  CASE WHEN max(c) <= 1500 THEN 'pass' ELSE 'FAIL' END AS max_name_cap_1500,
  CASE WHEN round(100.0 * (SELECT count(DISTINCT full_name) FROM demo.beneficiary)
    / nullif((SELECT count(*) FROM demo.beneficiary), 0), 2) >= 60 THEN 'pass' ELSE 'FAIL' END AS distinct_pct_ge_60
FROM counts;

\echo '=== name_regex_check (no digits; tokens 3+ chars; max 3 tokens on full names) ==='
SELECT 'beneficiary.full_name' AS field,
       count(*) FILTER (WHERE full_name ~ '[0-9]') AS rows_with_digit,
       count(*) FILTER (WHERE full_name ~ '(^|[[:space:]])[A-Za-z]{1,2}([[:space:]]|$)') AS rows_short_token,
       count(*) FILTER (WHERE array_length(regexp_split_to_array(trim(full_name), '[[:space:]]+'), 1) > 3) AS rows_too_many_tokens
FROM demo.beneficiary
UNION ALL
SELECT 'beneficiary.father_name',
       count(*) FILTER (WHERE father_name ~ '[0-9]'),
       count(*) FILTER (WHERE father_name ~ '(^|[[:space:]])[A-Za-z]{1,2}([[:space:]]|$)'),
       count(*) FILTER (WHERE array_length(regexp_split_to_array(trim(father_name), '[[:space:]]+'), 1) > 3)
FROM demo.beneficiary
UNION ALL
SELECT 'bank_account.account_holder_name',
       count(*) FILTER (WHERE account_holder_name ~ '[0-9]'),
       count(*) FILTER (WHERE account_holder_name ~ '(^|[[:space:]])[A-Za-z]{1,2}([[:space:]]|$)'),
       count(*) FILTER (WHERE array_length(regexp_split_to_array(trim(account_holder_name), '[[:space:]]+'), 1) > 3)
FROM demo.bank_account
UNION ALL
SELECT 'pension_register.first_name',
       count(*) FILTER (WHERE first_name ~ '[0-9]'),
       count(*) FILTER (WHERE first_name ~ '(^|[[:space:]])[A-Za-z]{1,2}([[:space:]]|$)'),
       0
FROM demo.pension_register
UNION ALL
SELECT 'pension_register.last_name',
       count(*) FILTER (WHERE last_name ~ '[0-9]'),
       count(*) FILTER (WHERE last_name ~ '(^|[[:space:]])[A-Za-z]{1,2}([[:space:]]|$)'),
       0
FROM demo.pension_register
UNION ALL
SELECT 'ration_card.head_name',
       count(*) FILTER (WHERE head_name ~ '[0-9]'),
       count(*) FILTER (WHERE head_name ~ '(^|[[:space:]])[A-Za-z]{1,2}([[:space:]]|$)'),
       count(*) FILTER (WHERE array_length(regexp_split_to_array(trim(head_name), '[[:space:]]+'), 1) > 3)
FROM demo.ration_card;

\echo '=== income_mismatch ==='
SELECT round(100.0 * avg(CASE WHEN ba.declared_income <> b.annual_income THEN 1 ELSE 0 END), 2) AS pct_matched_income_mismatch
FROM demo.bank_account ba
JOIN demo.beneficiary b
  ON b.beneficiary_id = NULLIF(ltrim(ba.beneficiary_ref, '0'), '')::bigint;

\echo '=== status_mismatch ==='
SELECT round(100.0 * avg(CASE WHEN ba.status <> b.status THEN 1 ELSE 0 END), 2) AS pct_matched_status_mismatch
FROM demo.bank_account ba
JOIN demo.beneficiary b
  ON b.beneficiary_id = NULLIF(ltrim(ba.beneficiary_ref, '0'), '')::bigint;

\echo '=== typo_blocking_recall ==='
WITH linked AS (
  SELECT ba.account_id,
         ba.account_holder_name,
         b.full_name,
         sum(1) OVER (ORDER BY ba.account_id) AS link_seq
  FROM demo.bank_account ba
  JOIN demo.beneficiary b
    ON b.beneficiary_id = NULLIF(ltrim(ba.beneficiary_ref, '0'), '')::bigint
),
planted AS (
  SELECT *
  FROM linked
  WHERE account_holder_name IS DISTINCT FROM full_name
    AND mod(link_seq, 100) < 23
)
SELECT
  count(*) AS planted_typo_pairs,
  sum(CASE WHEN substr(lower(account_holder_name), 1, :blocking_prefix_len)
            = substr(lower(full_name), 1, :blocking_prefix_len) THEN 1 ELSE 0 END) AS kept_by_blocking,
  round(100.0 * sum(CASE WHEN substr(lower(account_holder_name), 1, :blocking_prefix_len)
            = substr(lower(full_name), 1, :blocking_prefix_len) THEN 1 ELSE 0 END)
    / nullif(count(*), 0), 2) AS typo_blocking_recall_pct
FROM planted;

\echo '=== pension_overlap ==='
SELECT round(100.0 * avg(CASE WHEN b.beneficiary_id IS NOT NULL THEN 1 ELSE 0 END), 2) AS pct_pension_linked
FROM demo.pension_register p
LEFT JOIN demo.beneficiary b ON b.beneficiary_id = p.beneficiary_id;

\echo '=== ration_duplicate_persons ==='
WITH cards AS (
  SELECT beneficiary_id, count(*) AS c FROM demo.ration_card GROUP BY beneficiary_id
)
SELECT
  round(100.0 * avg(CASE WHEN c > 1 THEN 1 ELSE 0 END), 2) AS pct_beneficiaries_with_multiple_cards,
  round(100.0 * sum(CASE WHEN c > 1 THEN c - 1 ELSE 0 END) / nullif((SELECT count(*) FROM demo.ration_card), 0), 2) AS pct_extra_ration_rows
FROM cards;

\echo '=== rows_by_district ==='
SELECT district_code, count(*) AS n FROM demo.beneficiary GROUP BY 1 ORDER BY n DESC;

\echo '=== rows_by_taluka_jaipur ==='
SELECT taluka, count(*) AS n FROM demo.beneficiary WHERE district_code = 'JP' GROUP BY 1 ORDER BY n DESC LIMIT 10;

\echo '=== rows_by_department ==='
SELECT department_code, count(*) AS n FROM demo.beneficiary GROUP BY 1 ORDER BY 1;

\echo '=== name_block_distribution ==='
WITH blocks AS (
  SELECT substr(lower(full_name), 1, :blocking_prefix_len) AS block, count(*) AS block_size
  FROM demo.beneficiary
  GROUP BY 1
)
SELECT
  count(*) AS distinct_blocks,
  max(block_size) AS max_block_size,
  percentile_cont(0.99) WITHIN GROUP (ORDER BY block_size) AS p99_block_size,
  percentile_cont(0.5) WITHIN GROUP (ORDER BY block_size) AS median_block_size
FROM blocks;

\echo '=== top_blocks ==='
SELECT substr(lower(full_name), 1, :blocking_prefix_len) AS block, count(*) AS block_size
FROM demo.beneficiary
GROUP BY 1
ORDER BY block_size DESC
LIMIT 15;
