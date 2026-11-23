-- Audit trail (filled by triggers on policy/claim tables) and notifications produced by batch jobs.

CREATE SEQUENCE audit_log_seq START WITH 1 INCREMENT BY 1 CACHE 20;

CREATE TABLE audit_log (
    id         NUMBER(19)        DEFAULT audit_log_seq.NEXTVAL NOT NULL,
    table_name VARCHAR2(30 CHAR) NOT NULL,
    record_id  NUMBER(19)        NOT NULL,
    operation  VARCHAR2(10 CHAR) NOT NULL,
    old_values CLOB,
    new_values CLOB,
    changed_by VARCHAR2(50 CHAR) NOT NULL,
    changed_at TIMESTAMP         DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_audit_log_operation CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_audit_log_old_json CHECK (old_values IS JSON),
    CONSTRAINT ck_audit_log_new_json CHECK (new_values IS JSON)
);

CREATE INDEX ix_audit_log_record ON audit_log (table_name, record_id);
CREATE INDEX ix_audit_log_changed_at ON audit_log (changed_at);

CREATE SEQUENCE notification_seq START WITH 1 INCREMENT BY 1 CACHE 20;

CREATE TABLE notification (
    id                NUMBER(19)          DEFAULT notification_seq.NEXTVAL NOT NULL,
    customer_id       NUMBER(19),
    policy_id         NUMBER(19),
    notification_type VARCHAR2(30 CHAR)   NOT NULL,
    channel           VARCHAR2(10 CHAR)   NOT NULL,
    recipient         VARCHAR2(150 CHAR)  NOT NULL,
    subject           VARCHAR2(200 CHAR)  NOT NULL,
    body              VARCHAR2(4000 CHAR) NOT NULL,
    status            VARCHAR2(10 CHAR)   DEFAULT 'PENDING' NOT NULL,
    sent_at           TIMESTAMP,
    created_at        TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(50 CHAR)   DEFAULT 'system' NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_notification_policy FOREIGN KEY (policy_id) REFERENCES policy (id),
    CONSTRAINT ck_notification_type CHECK (notification_type IN (
        'RENEWAL_OFFER', 'INSTALLMENT_OVERDUE', 'POLICY_SUSPENDED', 'POLICY_EXPIRED',
        'CLAIM_STATUS_CHANGED', 'PAYMENT_RECEIVED')),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notification_sent CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);

CREATE INDEX ix_notification_customer ON notification (customer_id);
CREATE INDEX ix_notification_policy ON notification (policy_id);
CREATE INDEX ix_notification_status ON notification (status, created_at);
