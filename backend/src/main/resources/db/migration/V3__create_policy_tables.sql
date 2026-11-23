-- Policies (quotes are policies in QUOTE status), installment plans and payments.

CREATE SEQUENCE policy_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE policy (
    id                     NUMBER(19)         DEFAULT policy_seq.NEXTVAL NOT NULL,
    policy_number          VARCHAR2(30 CHAR)  NOT NULL,
    product_id             NUMBER(19)         NOT NULL,
    customer_id            NUMBER(19)         NOT NULL,
    vehicle_id             NUMBER(19),
    property_id            NUMBER(19),
    agent_id               NUMBER(19)         NOT NULL,
    status                 VARCHAR2(20 CHAR)  NOT NULL,
    start_date             DATE               NOT NULL,
    end_date               DATE               NOT NULL,
    ncd_step               NUMBER(1)          DEFAULT 4 NOT NULL,
    net_premium            NUMBER(15, 2)      NOT NULL,
    tax_amount             NUMBER(15, 2)      DEFAULT 0 NOT NULL,
    gross_premium          NUMBER(15, 2)      NOT NULL,
    installment_count      NUMBER(2)          DEFAULT 1 NOT NULL,
    quote_valid_until      DATE,
    issued_at              TIMESTAMP,
    cancelled_at           TIMESTAMP,
    cancel_reason          VARCHAR2(300 CHAR),
    refund_amount          NUMBER(15, 2),
    renewed_from_policy_id NUMBER(19),
    created_at             TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    created_by             VARCHAR2(50 CHAR)  DEFAULT 'system' NOT NULL,
    updated_at             TIMESTAMP,
    updated_by             VARCHAR2(50 CHAR),
    version                NUMBER(10)         DEFAULT 0 NOT NULL,
    CONSTRAINT pk_policy PRIMARY KEY (id),
    CONSTRAINT uk_policy_number UNIQUE (policy_number),
    CONSTRAINT fk_policy_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_policy_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_policy_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_policy_property FOREIGN KEY (property_id) REFERENCES property (id),
    CONSTRAINT fk_policy_agent FOREIGN KEY (agent_id) REFERENCES app_user (id),
    CONSTRAINT fk_policy_renewed_from FOREIGN KEY (renewed_from_policy_id) REFERENCES policy (id),
    CONSTRAINT ck_policy_status CHECK (status IN (
        'QUOTE', 'ACTIVE', 'SUSPENDED', 'CANCELLED', 'EXPIRED', 'RENEWED')),
    CONSTRAINT ck_policy_dates CHECK (end_date > start_date),
    CONSTRAINT ck_policy_single_asset CHECK (vehicle_id IS NULL OR property_id IS NULL),
    -- NCD = no-claims discount step (hasarsızlık basamağı), 4 is the entry step.
    CONSTRAINT ck_policy_ncd_step CHECK (ncd_step BETWEEN 1 AND 8),
    CONSTRAINT ck_policy_premiums CHECK (
        net_premium >= 0 AND tax_amount >= 0 AND gross_premium = net_premium + tax_amount),
    CONSTRAINT ck_policy_installments CHECK (installment_count BETWEEN 1 AND 12),
    CONSTRAINT ck_policy_issued CHECK (status = 'QUOTE' OR issued_at IS NOT NULL),
    CONSTRAINT ck_policy_cancelled CHECK (
        status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)),
    CONSTRAINT ck_policy_refund CHECK (refund_amount IS NULL OR refund_amount >= 0)
);

CREATE INDEX ix_policy_customer ON policy (customer_id);
CREATE INDEX ix_policy_product ON policy (product_id);
CREATE INDEX ix_policy_agent ON policy (agent_id);
CREATE INDEX ix_policy_vehicle ON policy (vehicle_id);
CREATE INDEX ix_policy_property ON policy (property_id);
CREATE INDEX ix_policy_renewed_from ON policy (renewed_from_policy_id);
-- Batch jobs scan by status + end date (renewal, expiration).
CREATE INDEX ix_policy_status_end_date ON policy (status, end_date);

CREATE SEQUENCE installment_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE installment (
    id             NUMBER(19)        DEFAULT installment_seq.NEXTVAL NOT NULL,
    policy_id      NUMBER(19)        NOT NULL,
    installment_no NUMBER(2)         NOT NULL,
    due_date       DATE              NOT NULL,
    amount         NUMBER(15, 2)     NOT NULL,
    status         VARCHAR2(20 CHAR) DEFAULT 'PENDING' NOT NULL,
    paid_at        TIMESTAMP,
    created_at     TIMESTAMP         DEFAULT SYSTIMESTAMP NOT NULL,
    created_by     VARCHAR2(50 CHAR) DEFAULT 'system' NOT NULL,
    updated_at     TIMESTAMP,
    updated_by     VARCHAR2(50 CHAR),
    version        NUMBER(10)        DEFAULT 0 NOT NULL,
    CONSTRAINT pk_installment PRIMARY KEY (id),
    CONSTRAINT fk_installment_policy FOREIGN KEY (policy_id) REFERENCES policy (id),
    CONSTRAINT uk_installment_policy_no UNIQUE (policy_id, installment_no),
    CONSTRAINT ck_installment_no CHECK (installment_no BETWEEN 1 AND 12),
    CONSTRAINT ck_installment_amount CHECK (amount > 0),
    CONSTRAINT ck_installment_status CHECK (status IN ('PENDING', 'PAID', 'OVERDUE', 'CANCELLED')),
    CONSTRAINT ck_installment_paid CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL) OR (status <> 'PAID' AND paid_at IS NULL))
);

-- Overdue job scans PENDING installments by due date.
CREATE INDEX ix_installment_status_due_date ON installment (status, due_date);

CREATE SEQUENCE payment_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE payment (
    id             NUMBER(19)        DEFAULT payment_seq.NEXTVAL NOT NULL,
    installment_id NUMBER(19)        NOT NULL,
    amount         NUMBER(15, 2)     NOT NULL,
    payment_date   TIMESTAMP         DEFAULT SYSTIMESTAMP NOT NULL,
    payment_method VARCHAR2(20 CHAR) NOT NULL,
    reference_no   VARCHAR2(50 CHAR) NOT NULL,
    created_at     TIMESTAMP         DEFAULT SYSTIMESTAMP NOT NULL,
    created_by     VARCHAR2(50 CHAR) DEFAULT 'system' NOT NULL,
    updated_at     TIMESTAMP,
    updated_by     VARCHAR2(50 CHAR),
    version        NUMBER(10)        DEFAULT 0 NOT NULL,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT fk_payment_installment FOREIGN KEY (installment_id) REFERENCES installment (id),
    CONSTRAINT uk_payment_reference_no UNIQUE (reference_no),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (payment_method IN ('CREDIT_CARD', 'BANK_TRANSFER', 'CASH'))
);

CREATE INDEX ix_payment_installment ON payment (installment_id);
