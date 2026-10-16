# Demo data profile (generated)

Generated: **2026-10-16T11:23:07Z** · `ROWS=1000000` · blocking prefix **3** (product default) · beneficiary=1000000 · bank=900000

## Row counts and overlap

```
=== row_counts ===
       tbl        |    n    
------------------+---------
 district_master  |       7
 pension_register |  300000
 ration_card      |  400000
 bank_account     |  900000
 beneficiary      | 1000000
(5 rows)
=== bank_overlap ===
 pct_bank_rows_linked | pct_orphan_bank_rows 
----------------------+----------------------
                95.00 |                 5.00
(1 row)
=== beneficiaries_with_bank (distinct linked beneficiaries) ===
 pct_beneficiaries_with_bank 
-----------------------------
                       85.08
(1 row)
=== bank_mobile_match (linked rows only) ===
 pct_linked_mobile_match 
-------------------------
                   85.00
(1 row)
=== full_name_quality ===
 blank_names | max_full_name_rows | pct_distinct_full_names | max_name_cap_1500 | distinct_pct_ge_60 
-------------+--------------------+-------------------------+-------------------+--------------------
           0 |                772 |                   65.05 | pass              | pass
(1 row)
=== name_regex_check (no digits; tokens 3+ chars; max 3 tokens on full names) ===
              field               | rows_with_digit | rows_short_token | rows_too_many_tokens 
----------------------------------+-----------------+------------------+----------------------
 beneficiary.full_name            |               0 |                0 |                    0
 beneficiary.father_name          |               0 |                0 |                    0
 bank_account.account_holder_name |               0 |                0 |                    0
 pension_register.first_name      |               0 |                0 |                    0
 pension_register.last_name       |               0 |                0 |                    0
 ration_card.head_name            |               0 |                0 |                    0
(6 rows)
=== income_mismatch ===
 pct_matched_income_mismatch 
-----------------------------
                       12.00
(1 row)
=== status_mismatch ===
 pct_matched_status_mismatch 
-----------------------------
                        4.00
(1 row)
=== typo_blocking_recall ===
 planted_typo_pairs | kept_by_blocking | typo_blocking_recall_pct 
--------------------+------------------+--------------------------
              63718 |            15693 |                    24.63
(1 row)
=== pension_overlap ===
 pct_pension_linked 
--------------------
             100.00
(1 row)
=== ration_duplicate_persons ===
 pct_beneficiaries_with_multiple_cards | pct_extra_ration_rows 
---------------------------------------+-----------------------
                                  1.08 |                  7.37
(1 row)
=== rows_by_district ===
 district_code |   n    
---------------+--------
 JP            | 320000
 JO            | 200000
 KO            | 140000
 BI            | 100000
 AJ            | 100000
 UD            |  80000
 AL            |  60000
(7 rows)
=== rows_by_taluka_jaipur ===
    taluka    |   n    
--------------+--------
 Sanganer     | 110000
 Taluka-JP-30 |  10000
 Taluka-JP-26 |  10000
 Taluka-JP-27 |  10000
 Taluka-JP-10 |  10000
 Taluka-JP-29 |  10000
 Taluka-JP-28 |  10000
 Taluka-JP-25 |  10000
 Taluka-JP-24 |  10000
 Taluka-JP-4  |  10000
(10 rows)
=== rows_by_department ===
 department_code |   n    
-----------------+--------
 D01             | 200000
 D02             | 200000
 D03             | 200000
 D04             | 200000
 D05             | 200000
(5 rows)
=== name_block_distribution ===
 distinct_blocks | max_block_size |   p99_block_size   | median_block_size 
-----------------+----------------+--------------------+-------------------
             262 |          28469 | 17214.689999999995 |              2171
(1 row)
=== top_blocks ===
 block | block_size 
-------+------------
 ram   |      28469
 cha   |      20612
 bha   |      18173
 man   |      16602
 sha   |      15742
 san   |      15100
 shi   |      14944
 raj   |      14513
 ran   |      10371
 mad   |      10128
 kam   |      10086
 sar   |       9829
 gan   |       8400
 pra   |       8400
 kan   |       8356
(15 rows)
```

## Fan-out estimate (product guard, prefix len 3)

Formula: `sourceRows × targetRows / max(distinct blocking prefix)` on
`substr(lower(full_name), 1, 3)` vs `substr(lower(account_holder_name), 1, 3)`.

| Input | Value |
|-------|-------|
| sourceRows | 1000000 |
| targetRows | 900000 |
| max distinct blocking prefixes | 1224 |
| **Estimated fan-out** | **735294117** |
| Demo acceptance target | 40000000 (must stay below 40M; guard unchanged at 50M) |
| `SRSE_ANALYSIS_MAX_ESTIMATED_ROWS` | 50000000 |
| Guard would refuse? | Yes |
| Under 40M target? | No |

## Fuzzy benchmark (Presto direct, blocking prefix 3)

Fuzzy name ≥85% **and exact mobile**, unscoped: **675387** rows, **675387 / 675387** ID precision (100.00%), **3s** wall time.

Precision counts rows where `beneficiary_id` matches `beneficiary_ref` on the linked bank row.

## Sample names (20 each)

### beneficiary.full_name (20)
```
Sandhya Hada
Jagannath Hada
Sandhya Hada
Jagannath Hada
Sandhya Hada
Jagannath Hada
Sandhya Hada
Jagannath Hada
Sandhya Hada
Jagannath Hada
Sandhya Hada
Jagannath Hada
Sandhya Hada
Jagannath Hada
Sandhya Hada
Jagannath Hada
Sandhya Hada
Jagannath Hada
Sandhya Hada
Jagannath Hada
```

### bank_account.account_holder_name (20)
```
Chetan Dwarika Parmar
Faruk Brijesh Parmar
Sudhir Shankar Prajapati
Kirtan Kathodi
Kirtan Kathodi
Kailash Regar
Aarav Sharma
Mahendra Bhanwar Verma
Bhagat Ram Sharma
Shivkumar Shyamlal Sharma
Lalit Garasia
Mahesh Dhanaka
Mohan Kumar
Kesar Chaturvedi
Nathu Lohia
Motilal Khatri
Naresh Goverdhan Prajapati
Rohit Sogani
Satya Khandelwal
Santosh Ranka
```

### pension_register first + last (20)
```
Parveen Mishra
Mutka olanki
Deora Parvati
Zakir Luthra
Rohtash Lohar
Kailash Yadav
Vinita Kumhar
Raamwati Ratsogi
Ramkali Bana
Premwati Charan
Droopadi Gurajr
yesha Ojjha
Shivam Dutt
Ghanshyam Nehra
Pawan Barfa
Rukhsar Bhil
HHema aKthodi
Gopldas KKoli
Gaanga Mehgwal
Arun Malviya
```

### ration_card.head_name (20)
```
```

## Notes

- Name pools: `docker/demo/name_pools/` (250 M / 250 F first, 75 M / 75 F middle, 1000 single-token surnames); at most three tokens per person name; typos on bank/pension only.
- Quality gates: distinct full names ≥60%, most common full name ≤1,500 rows; `name_regex_check` in profile output must show zero digits and zero short tokens.
- Re-seed checksums: run `./docker/demo/seed.sh` and compare md5 lines at end of output.

