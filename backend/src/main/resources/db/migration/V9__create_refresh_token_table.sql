-- Refresh tokens for JWT authentication. Only a SHA-256 hash of the token is stored,
-- so a leaked table cannot be used to impersonate users. Tokens are rotated on every refresh.

CREATE SEQUENCE refresh_token_seq START WITH 1 INCREMENT BY 1 CACHE 20;

CREATE TABLE refresh_token (
    id         NUMBER(19)        DEFAULT refresh_token_seq.NEXTVAL NOT NULL,
    user_id    NUMBER(19)        NOT NULL,
    token_hash VARCHAR2(64 CHAR) NOT NULL,
    expires_at TIMESTAMP         NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP         DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
