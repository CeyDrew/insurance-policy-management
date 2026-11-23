-- REGEXP_LIKE(NULL, ...) evaluates to UNKNOWN and a CHECK constraint only rejects FALSE,
-- so ck_customer_identity accepted customers without a national id / tax number.
-- Recreate it with explicit NOT NULL conditions.

ALTER TABLE customer DROP CONSTRAINT ck_customer_identity;

ALTER TABLE customer ADD CONSTRAINT ck_customer_identity CHECK (
    (customer_type = 'INDIVIDUAL'
        AND national_id IS NOT NULL
        AND REGEXP_LIKE(national_id, '^[1-9][0-9]{10}$')
        AND tax_number IS NULL
        AND first_name IS NOT NULL AND last_name IS NOT NULL
        AND birth_date IS NOT NULL)
    OR (customer_type = 'CORPORATE'
        AND tax_number IS NOT NULL
        AND REGEXP_LIKE(tax_number, '^[0-9]{10}$')
        AND national_id IS NULL
        AND company_name IS NOT NULL));
