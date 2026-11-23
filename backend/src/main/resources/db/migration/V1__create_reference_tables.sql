-- Reference tables: users, cities, products and tariff parameters.
-- Conventions: every table has a NUMBER(19) surrogate key fed by its own sequence,
-- audit columns (created_at/by, updated_at/by) and an optimistic-lock "version" column.
-- Booleans are NUMBER(1) with a 0/1 check to stay compatible with Oracle 19c.

CREATE SEQUENCE app_user_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE app_user (
    id            NUMBER(19)          DEFAULT app_user_seq.NEXTVAL NOT NULL,
    username      VARCHAR2(50 CHAR)   NOT NULL,
    password_hash VARCHAR2(100 CHAR)  NOT NULL,
    full_name     VARCHAR2(100 CHAR)  NOT NULL,
    email         VARCHAR2(150 CHAR)  NOT NULL,
    role          VARCHAR2(20 CHAR)   NOT NULL,
    active        NUMBER(1)           DEFAULT 1 NOT NULL,
    created_at    TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    created_by    VARCHAR2(50 CHAR)   DEFAULT 'system' NOT NULL,
    updated_at    TIMESTAMP,
    updated_by    VARCHAR2(50 CHAR),
    version       NUMBER(10)          DEFAULT 0 NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'AGENT', 'CLAIM_HANDLER')),
    CONSTRAINT ck_app_user_active CHECK (active IN (0, 1))
);

CREATE SEQUENCE city_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE city (
    id          NUMBER(19)        DEFAULT city_seq.NEXTVAL NOT NULL,
    plate_code  NUMBER(2)         NOT NULL,
    name        VARCHAR2(50 CHAR) NOT NULL,
    risk_factor NUMBER(5, 3)      DEFAULT 1 NOT NULL,
    CONSTRAINT pk_city PRIMARY KEY (id),
    CONSTRAINT uk_city_plate_code UNIQUE (plate_code),
    CONSTRAINT uk_city_name UNIQUE (name),
    CONSTRAINT ck_city_plate_code CHECK (plate_code BETWEEN 1 AND 81),
    CONSTRAINT ck_city_risk_factor CHECK (risk_factor > 0)
);

CREATE SEQUENCE product_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE product (
    id               NUMBER(19)         DEFAULT product_seq.NEXTVAL NOT NULL,
    code             VARCHAR2(20 CHAR)  NOT NULL,
    name             VARCHAR2(100 CHAR) NOT NULL,
    description      VARCHAR2(500 CHAR),
    asset_type       VARCHAR2(20 CHAR)  NOT NULL,
    base_premium     NUMBER(15, 2)      DEFAULT 0 NOT NULL,
    min_premium      NUMBER(15, 2)      DEFAULT 0 NOT NULL,
    tax_rate         NUMBER(5, 4)       DEFAULT 0 NOT NULL,
    max_installments NUMBER(2)          DEFAULT 1 NOT NULL,
    term_months      NUMBER(3)          DEFAULT 12 NOT NULL,
    active           NUMBER(1)          DEFAULT 1 NOT NULL,
    created_at       TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    created_by       VARCHAR2(50 CHAR)  DEFAULT 'system' NOT NULL,
    updated_at       TIMESTAMP,
    updated_by       VARCHAR2(50 CHAR),
    version          NUMBER(10)         DEFAULT 0 NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT uk_product_code UNIQUE (code),
    CONSTRAINT ck_product_code CHECK (code IN ('TRAFFIC', 'CASCO', 'HOME', 'HEALTH')),
    CONSTRAINT ck_product_asset_type CHECK (asset_type IN ('VEHICLE', 'PROPERTY', 'PERSON')),
    CONSTRAINT ck_product_premiums CHECK (base_premium >= 0 AND min_premium >= 0),
    CONSTRAINT ck_product_tax_rate CHECK (tax_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_product_installments CHECK (max_installments BETWEEN 1 AND 12),
    CONSTRAINT ck_product_term CHECK (term_months > 0),
    CONSTRAINT ck_product_active CHECK (active IN (0, 1))
);

-- Generic tariff table read by the PL/SQL premium engine.
-- A row is either a numeric range (min_value..max_value, inclusive) or a categorical key (param_key).
CREATE SEQUENCE tariff_parameter_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE tariff_parameter (
    id          NUMBER(19)         DEFAULT tariff_parameter_seq.NEXTVAL NOT NULL,
    product_id  NUMBER(19)         NOT NULL,
    param_code  VARCHAR2(30 CHAR)  NOT NULL,
    param_key   VARCHAR2(30 CHAR),
    min_value   NUMBER(15, 2),
    max_value   NUMBER(15, 2),
    factor      NUMBER(10, 6)      NOT NULL,
    description VARCHAR2(200 CHAR),
    valid_from  DATE               DEFAULT DATE '2020-01-01' NOT NULL,
    valid_to    DATE,
    created_at  TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    created_by  VARCHAR2(50 CHAR)  DEFAULT 'system' NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  VARCHAR2(50 CHAR),
    version     NUMBER(10)         DEFAULT 0 NOT NULL,
    CONSTRAINT pk_tariff_parameter PRIMARY KEY (id),
    CONSTRAINT fk_tariff_parameter_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_tariff_parameter_code CHECK (param_code IN (
        'DRIVER_AGE', 'INSURED_AGE', 'VEHICLE_AGE', 'BUILDING_AGE', 'VALUE_RATE',
        'NCD_STEP', 'USAGE_TYPE', 'CONSTRUCTION_TYPE')),
    CONSTRAINT ck_tariff_parameter_shape CHECK (
        (param_key IS NOT NULL AND min_value IS NULL AND max_value IS NULL)
        OR (param_key IS NULL AND min_value IS NOT NULL AND max_value IS NOT NULL AND min_value <= max_value)),
    CONSTRAINT ck_tariff_parameter_factor CHECK (factor > 0),
    CONSTRAINT ck_tariff_parameter_validity CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_tariff_parameter_lookup ON tariff_parameter (product_id, param_code, valid_from);
