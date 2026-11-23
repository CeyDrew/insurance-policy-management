-- Claims (hasar ihbarları) and their uploaded documents.

CREATE SEQUENCE claim_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE claim (
    id               NUMBER(19)          DEFAULT claim_seq.NEXTVAL NOT NULL,
    claim_number     VARCHAR2(30 CHAR)   NOT NULL,
    policy_id        NUMBER(19)          NOT NULL,
    incident_date    DATE                NOT NULL,
    reported_at      TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    incident_city_id NUMBER(19),
    description      VARCHAR2(2000 CHAR) NOT NULL,
    status           VARCHAR2(20 CHAR)   DEFAULT 'REPORTED' NOT NULL,
    estimated_amount NUMBER(15, 2),
    approved_amount  NUMBER(15, 2),
    paid_amount      NUMBER(15, 2),
    handler_id       NUMBER(19),
    decision_note    VARCHAR2(1000 CHAR),
    decided_at       TIMESTAMP,
    paid_at          TIMESTAMP,
    created_at       TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    created_by       VARCHAR2(50 CHAR)   DEFAULT 'system' NOT NULL,
    updated_at       TIMESTAMP,
    updated_by       VARCHAR2(50 CHAR),
    version          NUMBER(10)          DEFAULT 0 NOT NULL,
    CONSTRAINT pk_claim PRIMARY KEY (id),
    CONSTRAINT uk_claim_number UNIQUE (claim_number),
    CONSTRAINT fk_claim_policy FOREIGN KEY (policy_id) REFERENCES policy (id),
    CONSTRAINT fk_claim_incident_city FOREIGN KEY (incident_city_id) REFERENCES city (id),
    CONSTRAINT fk_claim_handler FOREIGN KEY (handler_id) REFERENCES app_user (id),
    CONSTRAINT ck_claim_status CHECK (status IN ('REPORTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'PAID')),
    CONSTRAINT ck_claim_amounts CHECK (
        (estimated_amount IS NULL OR estimated_amount >= 0)
        AND (approved_amount IS NULL OR approved_amount >= 0)
        AND (paid_amount IS NULL OR paid_amount >= 0)),
    CONSTRAINT ck_claim_review CHECK (status = 'REPORTED' OR handler_id IS NOT NULL),
    CONSTRAINT ck_claim_approved CHECK (status NOT IN ('APPROVED', 'PAID') OR approved_amount IS NOT NULL),
    CONSTRAINT ck_claim_rejected CHECK (status <> 'REJECTED' OR decision_note IS NOT NULL),
    CONSTRAINT ck_claim_paid CHECK (status <> 'PAID' OR (paid_amount IS NOT NULL AND paid_at IS NOT NULL))
);

CREATE INDEX ix_claim_policy ON claim (policy_id);
CREATE INDEX ix_claim_handler ON claim (handler_id);
CREATE INDEX ix_claim_incident_city ON claim (incident_city_id);
CREATE INDEX ix_claim_status ON claim (status);

CREATE SEQUENCE claim_document_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE claim_document (
    id            NUMBER(19)         DEFAULT claim_document_seq.NEXTVAL NOT NULL,
    claim_id      NUMBER(19)         NOT NULL,
    document_type VARCHAR2(30 CHAR)  NOT NULL,
    file_name     VARCHAR2(255 CHAR) NOT NULL,
    content_type  VARCHAR2(100 CHAR) NOT NULL,
    file_size     NUMBER(12)         NOT NULL,
    storage_path  VARCHAR2(500 CHAR) NOT NULL,
    created_at    TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    created_by    VARCHAR2(50 CHAR)  DEFAULT 'system' NOT NULL,
    updated_at    TIMESTAMP,
    updated_by    VARCHAR2(50 CHAR),
    version       NUMBER(10)         DEFAULT 0 NOT NULL,
    CONSTRAINT pk_claim_document PRIMARY KEY (id),
    CONSTRAINT fk_claim_document_claim FOREIGN KEY (claim_id) REFERENCES claim (id),
    CONSTRAINT uk_claim_document_path UNIQUE (storage_path),
    CONSTRAINT ck_claim_document_type CHECK (document_type IN (
        'ACCIDENT_REPORT', 'PHOTO', 'INVOICE', 'EXPERT_REPORT', 'MEDICAL_REPORT', 'OTHER')),
    CONSTRAINT ck_claim_document_size CHECK (file_size > 0)
);

CREATE INDEX ix_claim_document_claim ON claim_document (claim_id);
