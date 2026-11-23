-- Customers (individual or corporate) and the insured assets they own.

CREATE SEQUENCE customer_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE customer (
    id            NUMBER(19)         DEFAULT customer_seq.NEXTVAL NOT NULL,
    customer_type VARCHAR2(20 CHAR)  NOT NULL,
    national_id   VARCHAR2(11 CHAR),
    tax_number    VARCHAR2(10 CHAR),
    first_name    VARCHAR2(50 CHAR),
    last_name     VARCHAR2(50 CHAR),
    company_name  VARCHAR2(150 CHAR),
    birth_date    DATE,
    gender        VARCHAR2(10 CHAR),
    email         VARCHAR2(150 CHAR),
    phone         VARCHAR2(20 CHAR),
    address_line  VARCHAR2(300 CHAR),
    city_id       NUMBER(19)         NOT NULL,
    created_at    TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    created_by    VARCHAR2(50 CHAR)  DEFAULT 'system' NOT NULL,
    updated_at    TIMESTAMP,
    updated_by    VARCHAR2(50 CHAR),
    version       NUMBER(10)         DEFAULT 0 NOT NULL,
    CONSTRAINT pk_customer PRIMARY KEY (id),
    CONSTRAINT fk_customer_city FOREIGN KEY (city_id) REFERENCES city (id),
    CONSTRAINT uk_customer_national_id UNIQUE (national_id),
    CONSTRAINT uk_customer_tax_number UNIQUE (tax_number),
    CONSTRAINT ck_customer_type CHECK (customer_type IN ('INDIVIDUAL', 'CORPORATE')),
    CONSTRAINT ck_customer_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE')),
    -- Individual: TCKN (11 digits) + name + birth date. Corporate: VKN (10 digits) + company name.
    -- Only the format is checked here; checksum validation happens in the service layer.
    CONSTRAINT ck_customer_identity CHECK (
        (customer_type = 'INDIVIDUAL'
            AND REGEXP_LIKE(national_id, '^[1-9][0-9]{10}$')
            AND tax_number IS NULL
            AND first_name IS NOT NULL AND last_name IS NOT NULL
            AND birth_date IS NOT NULL)
        OR (customer_type = 'CORPORATE'
            AND REGEXP_LIKE(tax_number, '^[0-9]{10}$')
            AND national_id IS NULL
            AND company_name IS NOT NULL))
);

CREATE INDEX ix_customer_city ON customer (city_id);
CREATE INDEX ix_customer_last_name ON customer (UPPER(last_name));
CREATE INDEX ix_customer_company_name ON customer (UPPER(company_name));

CREATE SEQUENCE vehicle_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE vehicle (
    id             NUMBER(19)         DEFAULT vehicle_seq.NEXTVAL NOT NULL,
    customer_id    NUMBER(19)         NOT NULL,
    plate_number   VARCHAR2(15 CHAR)  NOT NULL,
    chassis_number VARCHAR2(17 CHAR)  NOT NULL,
    brand          VARCHAR2(50 CHAR)  NOT NULL,
    model          VARCHAR2(50 CHAR)  NOT NULL,
    model_year     NUMBER(4)          NOT NULL,
    usage_type     VARCHAR2(20 CHAR)  DEFAULT 'PRIVATE' NOT NULL,
    vehicle_value  NUMBER(15, 2)      NOT NULL,
    created_at     TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    created_by     VARCHAR2(50 CHAR)  DEFAULT 'system' NOT NULL,
    updated_at     TIMESTAMP,
    updated_by     VARCHAR2(50 CHAR),
    version        NUMBER(10)         DEFAULT 0 NOT NULL,
    CONSTRAINT pk_vehicle PRIMARY KEY (id),
    CONSTRAINT fk_vehicle_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT uk_vehicle_plate_number UNIQUE (plate_number),
    CONSTRAINT uk_vehicle_chassis_number UNIQUE (chassis_number),
    CONSTRAINT ck_vehicle_model_year CHECK (model_year BETWEEN 1950 AND 2100),
    CONSTRAINT ck_vehicle_usage_type CHECK (usage_type IN ('PRIVATE', 'COMMERCIAL', 'TAXI')),
    CONSTRAINT ck_vehicle_value CHECK (vehicle_value > 0)
);

CREATE INDEX ix_vehicle_customer ON vehicle (customer_id);

CREATE SEQUENCE property_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE property (
    id                NUMBER(19)         DEFAULT property_seq.NEXTVAL NOT NULL,
    customer_id       NUMBER(19)         NOT NULL,
    city_id           NUMBER(19)         NOT NULL,
    address_line      VARCHAR2(300 CHAR) NOT NULL,
    building_year     NUMBER(4)          NOT NULL,
    gross_area_m2     NUMBER(7, 2)       NOT NULL,
    construction_type VARCHAR2(30 CHAR)  NOT NULL,
    property_value    NUMBER(15, 2)      NOT NULL,
    created_at        TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(50 CHAR)  DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP,
    updated_by        VARCHAR2(50 CHAR),
    version           NUMBER(10)         DEFAULT 0 NOT NULL,
    CONSTRAINT pk_property PRIMARY KEY (id),
    CONSTRAINT fk_property_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_property_city FOREIGN KEY (city_id) REFERENCES city (id),
    CONSTRAINT ck_property_building_year CHECK (building_year BETWEEN 1800 AND 2100),
    CONSTRAINT ck_property_area CHECK (gross_area_m2 > 0),
    CONSTRAINT ck_property_construction CHECK (construction_type IN (
        'REINFORCED_CONCRETE', 'STEEL', 'MASONRY', 'WOOD')),
    CONSTRAINT ck_property_value CHECK (property_value > 0)
);

CREATE INDEX ix_property_customer ON property (customer_id);
CREATE INDEX ix_property_city ON property (city_id);
