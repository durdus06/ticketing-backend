--liquibase formatted sql

--changeset selahattin:002-create-user-tokens-table
CREATE TABLE user_tokens (
    id          uuid        NOT NULL,
    user_id     uuid        NOT NULL,
    token_type  varchar(30) NOT NULL,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz NOT NULL,
    CONSTRAINT pk_user_tokens PRIMARY KEY (id),
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_user_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_user_tokens_token_type CHECK (token_type IN ('INVITATION', 'PASSWORD_RESET', 'ACCOUNT_RESTORE'))
);
CREATE INDEX idx_user_tokens_user_id_token_type ON user_tokens (user_id, token_type);
--rollback DROP TABLE user_tokens;
