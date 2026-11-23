-- Rows of a claim CSV import that were rejected (skipped) by claimImportJob, kept for the import report.

CREATE SEQUENCE claim_import_error_seq START WITH 1 INCREMENT BY 1 CACHE 20;

CREATE TABLE claim_import_error (
    id               NUMBER(19)          DEFAULT claim_import_error_seq.NEXTVAL NOT NULL,
    job_execution_id NUMBER(19)          NOT NULL,
    line_number      NUMBER(10)          NOT NULL,
    raw_line         VARCHAR2(4000 CHAR),
    error_message    VARCHAR2(1000 CHAR) NOT NULL,
    created_at       TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_claim_import_error PRIMARY KEY (id),
    CONSTRAINT fk_claim_import_error_job FOREIGN KEY (job_execution_id)
        REFERENCES batch_job_execution (job_execution_id),
    CONSTRAINT ck_claim_import_error_line CHECK (line_number > 0)
);

CREATE INDEX ix_claim_import_error_job ON claim_import_error (job_execution_id, line_number);

-- Duplicate check of the import (same policy, incident date and description).
CREATE INDEX ix_claim_policy_incident ON claim (policy_id, incident_date);
