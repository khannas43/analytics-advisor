-- Deterministic demo seed (DEMO-01). Variables: beneficiary_rows, bank_rows, pension_rows, ration_rows
\set ON_ERROR_STOP on

DROP TABLE IF EXISTS demo.ration_card CASCADE;
DROP TABLE IF EXISTS demo.pension_register CASCADE;
DROP TABLE IF EXISTS demo.bank_account CASCADE;
DROP TABLE IF EXISTS demo.beneficiary CASCADE;
DROP TABLE IF EXISTS demo.district_master CASCADE;

CREATE OR REPLACE FUNCTION demo._vary_name(base text, row_id bigint) RETURNS text
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
  i int;
  result text;
BEGIN
  IF base IS NULL OR length(base) < 3 OR mod(row_id, 100) >= 18 THEN
    RETURN base;
  END IF;
  i := 1 + mod(row_id, greatest(length(base) - 2, 1));
  CASE mod(row_id, 3)
    WHEN 0 THEN result := substr(base, 1, i - 1) || substr(base, i + 1);
    WHEN 1 THEN result := substr(base, 1, i) || substr(base, i, 1) || substr(base, i + 1);
    ELSE result := substr(base, 1, i - 1) || substr(base, i + 1, 1) || substr(base, i, 1) || substr(base, i + 2);
  END CASE;
  IF result ~ '(^|[[:space:]])[A-Za-z]{1,2}([[:space:]]|$)' OR result ~ '[0-9]' THEN
    RETURN base;
  END IF;
  RETURN result;
END;
$$;

CREATE OR REPLACE FUNCTION demo._reorder_name(base text, row_id bigint) RETURNS text
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
  parts text[];
BEGIN
  parts := regexp_split_to_array(trim(base), '\s+');
  IF array_length(parts, 1) IS NULL OR array_length(parts, 1) < 2 THEN
    RETURN base;
  END IF;
  RETURN parts[array_length(parts, 1)] || ' ' || parts[1];
END;
$$;

CREATE OR REPLACE FUNCTION demo._surname_from_full(full_name text) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
  SELECT (regexp_split_to_array(trim(full_name), '[[:space:]]+'))[
    array_length(regexp_split_to_array(trim(full_name), '[[:space:]]+'), 1)];
$$;

CREATE OR REPLACE FUNCTION demo._district_code(row_id bigint) RETURNS varchar
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE
    WHEN mod(row_id, 100) < 32 THEN 'JP'
    WHEN mod(row_id, 100) < 52 THEN 'JO'
    WHEN mod(row_id, 100) < 66 THEN 'KO'
    WHEN mod(row_id, 100) < 76 THEN 'AJ'
    WHEN mod(row_id, 100) < 84 THEN 'UD'
    WHEN mod(row_id, 100) < 90 THEN 'AL'
    ELSE 'BI'
  END;
$$;

CREATE OR REPLACE FUNCTION demo._district_name(code varchar) RETURNS varchar
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE code
    WHEN 'JP' THEN 'Jaipur'
    WHEN 'JO' THEN 'Jodhpur'
    WHEN 'UD' THEN 'Udaipur'
    WHEN 'KO' THEN 'Kota'
    WHEN 'AJ' THEN 'Ajmer'
    WHEN 'BI' THEN 'Bikaner'
    WHEN 'AL' THEN 'Alwar'
    ELSE 'Unknown'
  END;
$$;

CREATE TABLE demo.district_master (
  district_code varchar(4) PRIMARY KEY,
  district_name varchar(64) NOT NULL,
  division varchar(64) NOT NULL
);

CREATE TABLE demo.beneficiary (
  beneficiary_id bigint PRIMARY KEY,
  jan_aadhaar_no varchar(12) NOT NULL,
  full_name varchar(256) NOT NULL,
  father_name varchar(256) NOT NULL,
  gender varchar(8) NOT NULL,
  dob date NOT NULL,
  district_code varchar(4) NOT NULL,
  district varchar(64) NOT NULL,
  taluka_code varchar(8) NOT NULL,
  taluka varchar(64) NOT NULL,
  village_code varchar(16) NOT NULL,
  department_code varchar(8) NOT NULL,
  annual_income numeric(14, 2) NOT NULL,
  mobile varchar(10) NOT NULL,
  address varchar(256) NOT NULL,
  status varchar(16) NOT NULL
);

CREATE TABLE demo.bank_account (
  account_id bigint PRIMARY KEY,
  beneficiary_ref varchar(12) NOT NULL,
  account_holder_name varchar(256) NOT NULL,
  declared_income numeric(14, 2) NOT NULL,
  mobile varchar(10) NOT NULL,
  bank_name varchar(64) NOT NULL,
  ifsc varchar(11) NOT NULL,
  account_no varchar(18) NOT NULL,
  district_code varchar(4) NOT NULL,
  status varchar(16) NOT NULL
);

CREATE TABLE demo.pension_register (
  pension_id bigint PRIMARY KEY,
  beneficiary_id bigint NOT NULL,
  first_name varchar(128) NOT NULL,
  last_name varchar(128) NOT NULL,
  phone_primary varchar(10) NOT NULL,
  phone_secondary varchar(10),
  pension_type varchar(32) NOT NULL,
  monthly_amount numeric(12, 2) NOT NULL,
  district_code varchar(4) NOT NULL
);

CREATE TABLE demo.ration_card (
  card_id bigint PRIMARY KEY,
  beneficiary_id bigint NOT NULL,
  head_name varchar(256) NOT NULL,
  family_size integer NOT NULL,
  district_code varchar(4) NOT NULL
);

INSERT INTO demo.district_master (district_code, district_name, division) VALUES
  ('JP', 'Jaipur', 'Jaipur Division'),
  ('JO', 'Jodhpur', 'Jodhpur Division'),
  ('UD', 'Udaipur', 'Udaipur Division'),
  ('KO', 'Kota', 'Kota Division'),
  ('AJ', 'Ajmer', 'Ajmer Division'),
  ('BI', 'Bikaner', 'Bikaner Division'),
  ('AL', 'Alwar', 'Alwar Division');

INSERT INTO demo.beneficiary
WITH person AS (
  SELECT
    g.id,
    demo._district_code(g.id) AS dc,
    CASE
      WHEN mod(g.id, 100) < 35 THEN
        sum(CASE WHEN mod(g.id, 100) < 35 THEN 1 ELSE 0 END) OVER (ORDER BY g.id) - 1
    END AS hot_ix,
    CASE
      WHEN mod(g.id, 100) >= 35 THEN
        sum(CASE WHEN mod(g.id, 100) >= 35 THEN 1 ELSE 0 END) OVER (ORDER BY g.id) - 1
    END AS tail_ix
  FROM generate_series(1, :beneficiary_rows) AS g(id)
),
indices AS (
  SELECT
    p.id,
    p.dc,
    CASE
      WHEN p.hot_ix IS NOT NULL THEN 1 + mod(p.hot_ix / 1500, 250)
      ELSE 1 + mod(p.tail_ix, 250)
    END AS fi,
    CASE
      WHEN p.hot_ix IS NOT NULL THEN 1 + mod(p.hot_ix / 1500, 1000)
      ELSE 1 + mod(p.tail_ix / (250 * 75), 1000)
    END AS li,
    CASE
      WHEN p.hot_ix IS NOT NULL THEN 0
      ELSE 1 + mod(p.tail_ix / 250, 75)
    END AS mi,
    CASE WHEN mod(p.id, 2) = 0 THEN 'M' ELSE 'F' END AS gender
  FROM person p
),
father_indices AS (
  SELECT
    p.id,
    1 + mod((p.id + 17) * 17, 250) AS fi,
    1 + mod((p.id + 17) * 31, 1000) AS li
  FROM person p
)
SELECT
  ix.id AS beneficiary_id,
  lpad((mod(ix.id::bigint * 1000003, 1000000000000::bigint))::text, 12, '0') AS jan_aadhaar_no,
  trim(both FROM concat(
    fn.name,
    CASE WHEN ix.mi > 0 THEN ' ' || mn.name ELSE '' END,
    ' ',
    ln.name)) AS full_name,
  trim(both FROM concat(ff.name, ' ', fl.name)) AS father_name,
  CASE WHEN mod(ix.id, 2) = 0 THEN 'MALE' ELSE 'FEMALE' END AS gender,
  (DATE '1960-01-01' + mod(ix.id, 18000)) AS dob,
  ix.dc AS district_code,
  demo._district_name(ix.dc) AS district,
  CASE
    WHEN ix.dc = 'JP' AND mod(ix.id, 10) < 3 THEN 'SG'
    ELSE concat('T', ix.dc, lpad((1 + mod(ix.id, 40))::text, 2, '0'))
  END AS taluka_code,
  CASE
    WHEN ix.dc = 'JP' AND mod(ix.id, 10) < 3 THEN 'Sanganer'
    ELSE concat('Taluka-', ix.dc, '-', (1 + mod(ix.id, 40))::text)
  END AS taluka,
  concat('V', CASE WHEN ix.dc = 'JP' AND mod(ix.id, 10) < 3 THEN 'SG' ELSE concat('T', ix.dc) END,
         lpad((mod(ix.id, 900) + 100)::text, 3, '0')) AS village_code,
  concat('D0', (1 + mod(ix.id, 5))::text) AS department_code,
  (50000 + mod(ix.id * 17, 500000))::numeric(14, 2) AS annual_income,
  lpad((6000000000::bigint + mod(ix.id, 999999999))::text, 10, '0') AS mobile,
  concat('Demo address ward ', mod(ix.id, 200), ', ', demo._district_name(ix.dc)) AS address,
  CASE WHEN mod(ix.id, 50) = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END AS status
FROM indices ix
JOIN demo._name_first_pool fn ON fn.gender = ix.gender AND fn.idx = ix.fi
LEFT JOIN demo._name_middle_pool mn ON mn.gender = ix.gender AND mn.idx = ix.mi AND ix.mi > 0
JOIN demo._name_last_pool ln ON ln.idx = ix.li
JOIN father_indices fix ON fix.id = ix.id
JOIN demo._name_first_pool ff ON ff.gender = 'M' AND ff.idx = fix.fi
JOIN demo._name_last_pool fl ON fl.idx = fix.li;

INSERT INTO demo.bank_account
WITH bank_gen AS (
  SELECT b.id, (mod(b.id, 100) < 5) AS is_orphan
  FROM generate_series(1, :bank_rows) AS b(id)
),
bank AS (
  SELECT
    id,
    is_orphan,
    CASE
      WHEN is_orphan THEN NULL::bigint
      ELSE sum(CASE WHEN NOT is_orphan THEN 1 ELSE 0 END) OVER (ORDER BY id)
    END AS link_seq
  FROM bank_gen
),
target AS (
  SELECT
    bg.id,
    bg.is_orphan,
    bg.link_seq,
    CASE
      WHEN bg.is_orphan THEN NULL::bigint
      WHEN bg.link_seq <= ceil(0.85 * :beneficiary_rows)::bigint THEN bg.link_seq
      ELSE 1 + mod(bg.id::bigint * 2654435761, :beneficiary_rows)
    END AS beneficiary_id
  FROM bank bg
)
SELECT
  t.id AS account_id,
  lpad(
    CASE
      WHEN t.is_orphan THEN (:beneficiary_rows + t.id)::text
      ELSE t.beneficiary_id::text
    END,
    12, '0') AS beneficiary_ref,
  CASE
    WHEN t.is_orphan THEN oph.full_name
    WHEN mod(t.link_seq, 100) < 18 THEN demo._vary_name(ben.full_name, t.id)
    WHEN mod(t.link_seq, 100) >= 18 AND mod(t.link_seq, 100) < 23 THEN demo._reorder_name(ben.full_name, t.id)
    ELSE ben.full_name
  END AS account_holder_name,
  CASE
    WHEN NOT t.is_orphan AND mod(t.link_seq, 100) < 12 THEN ben.annual_income + 25000
    WHEN t.is_orphan THEN (50000 + mod(t.id * 17, 500000))::numeric(14, 2)
    ELSE ben.annual_income
  END::numeric(14, 2) AS declared_income,
  CASE
    WHEN t.is_orphan THEN lpad((6100000000::bigint + mod(t.id, 999999999))::text, 10, '0')
    WHEN mod(t.link_seq, 100) < 85 THEN ben.mobile
    ELSE lpad((6200000000::bigint + mod(t.id, 999999999))::text, 10, '0')
  END AS mobile,
  (ARRAY['SBI', 'PNB', 'BOB', 'HDFC', 'ICICI', 'Axis', 'Rajasthan Gramin'])[1 + mod(t.id, 7)] AS bank_name,
  concat('SBIN0', lpad(mod(t.id, 999999)::text, 6, '0')) AS ifsc,
  lpad(t.id::text, 18, '0') AS account_no,
  coalesce(ben.district_code, demo._district_code(t.id)) AS district_code,
  CASE
    WHEN NOT t.is_orphan AND mod(t.link_seq, 100) >= 12 AND mod(t.link_seq, 100) < 16 THEN
      CASE ben.status WHEN 'ACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END
    WHEN t.is_orphan THEN 'ACTIVE'
    ELSE ben.status
  END AS status
FROM target t
LEFT JOIN demo.beneficiary ben ON ben.beneficiary_id = t.beneficiary_id
LEFT JOIN LATERAL (
  SELECT trim(both FROM concat(fn.name, ' ', ln.name)) AS full_name
  FROM demo._name_first_pool fn
  JOIN demo._name_last_pool ln ON ln.idx = 1 + mod((t.id + 500000) * 31, 1000)
  WHERE fn.gender = CASE WHEN mod(t.id, 2) = 0 THEN 'M' ELSE 'F' END
    AND fn.idx = 1 + mod((t.id + 500000) * 17, 250)
) oph ON t.is_orphan;

INSERT INTO demo.pension_register
SELECT
  p.id AS pension_id,
  1 + mod(p.id::bigint * 7919, :beneficiary_rows) AS beneficiary_id,
  CASE
    WHEN mod(p.id, 100) < 18 THEN demo._vary_name(split_part(ben.full_name, ' ', 1), p.id)
    WHEN mod(p.id, 100) >= 18 AND mod(p.id, 100) < 23 THEN split_part(demo._reorder_name(ben.full_name, p.id), ' ', 1)
    ELSE split_part(ben.full_name, ' ', 1)
  END AS first_name,
  CASE
    WHEN mod(p.id, 100) < 18 THEN demo._vary_name(demo._surname_from_full(ben.full_name), p.id + 1)
    WHEN mod(p.id, 100) >= 18 AND mod(p.id, 100) < 23 THEN demo._surname_from_full(demo._reorder_name(ben.full_name, p.id))
    ELSE demo._surname_from_full(ben.full_name)
  END AS last_name,
  CASE WHEN mod(p.id, 2) = 0 THEN ben.mobile ELSE lpad((7000000000::bigint + mod(p.id, 999999999))::text, 10, '0') END AS phone_primary,
  CASE WHEN mod(p.id, 2) = 0 THEN lpad((7000000000::bigint + mod(p.id, 999999999))::text, 10, '0') ELSE ben.mobile END AS phone_secondary,
  (ARRAY['OLD_AGE', 'WIDOW', 'DISABILITY', 'FAMILY'])[1 + mod(p.id, 4)] AS pension_type,
  (1500 + mod(p.id * 13, 8500))::numeric(12, 2) AS monthly_amount,
  ben.district_code
FROM generate_series(1, :pension_rows) AS p(id)
JOIN demo.beneficiary ben ON ben.beneficiary_id = 1 + mod(p.id::bigint * 7919, :beneficiary_rows);

INSERT INTO demo.ration_card
SELECT
  c.id AS card_id,
  CASE
    WHEN mod(c.id, 100) < 8 THEN 1 + mod((c.id / 100)::bigint * 7919, least(:beneficiary_rows, 5000))
    ELSE 1 + mod(c.id::bigint * 7919, :beneficiary_rows)
  END AS beneficiary_id,
  ben.full_name AS head_name,
  (1 + mod(c.id, 8)) AS family_size,
  ben.district_code
FROM generate_series(1, :ration_rows) AS c(id)
JOIN demo.beneficiary ben ON ben.beneficiary_id = CASE
  WHEN mod(c.id, 100) < 8 THEN 1 + mod((c.id / 100)::bigint * 7919, least(:beneficiary_rows, 5000))
  ELSE 1 + mod(c.id::bigint * 7919, :beneficiary_rows)
END;

CREATE INDEX idx_beneficiary_district ON demo.beneficiary (district_code);
CREATE INDEX idx_beneficiary_taluka ON demo.beneficiary (taluka_code);
CREATE INDEX idx_beneficiary_department ON demo.beneficiary (department_code);
CREATE INDEX idx_beneficiary_full_name ON demo.beneficiary (full_name);

CREATE INDEX idx_bank_beneficiary_ref ON demo.bank_account (beneficiary_ref);
CREATE INDEX idx_bank_district ON demo.bank_account (district_code);
CREATE INDEX idx_bank_holder_name ON demo.bank_account (account_holder_name);

CREATE INDEX idx_pension_beneficiary ON demo.pension_register (beneficiary_id);
CREATE INDEX idx_pension_district ON demo.pension_register (district_code);

CREATE INDEX idx_ration_beneficiary ON demo.ration_card (beneficiary_id);
CREATE INDEX idx_ration_district ON demo.ration_card (district_code);

ANALYZE demo.beneficiary;
ANALYZE demo.bank_account;
ANALYZE demo.pension_register;
ANALYZE demo.ration_card;
ANALYZE demo.district_master;

GRANT SELECT ON ALL TABLES IN SCHEMA demo TO demo_ro;

DROP FUNCTION demo._district_name(varchar);
DROP FUNCTION demo._district_code(bigint);
DROP FUNCTION demo._reorder_name(text, bigint);
DROP FUNCTION demo._vary_name(text, bigint);
DROP FUNCTION demo._surname_from_full(text);
