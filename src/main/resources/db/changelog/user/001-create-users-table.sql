--liquibase formatted sql

--changeset selahattin:001-create-users-table
CREATE TABLE users (
    id            uuid         NOT NULL,
    first_name    varchar(100) NOT NULL,
    last_name     varchar(100) NOT NULL,
    date_of_birth date,
    email         varchar(254) NOT NULL,
    password_hash varchar(255),
    role          varchar(20)  NOT NULL,
    status        varchar(20)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    deleted_at    timestamptz,
    anonymize_after timestamptz,
    anonymized_at timestamptz,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ORGANIZER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_users_date_of_birth_required CHECK (date_of_birth IS NOT NULL OR anonymized_at IS NOT NULL),
    CONSTRAINT ck_users_anonymize_after_requires_deleted CHECK (anonymize_after IS NULL OR deleted_at IS NOT NULL),
    CONSTRAINT ck_users_anonymized_is_deleted CHECK (anonymized_at IS NULL OR deleted_at IS NOT NULL),
    CONSTRAINT ck_users_active_has_password CHECK (status <> 'ACTIVE' OR password_hash IS NOT NULL)
);
CREATE UNIQUE INDEX uq_users_email_active ON users (email) WHERE deleted_at IS NULL;
--rollback DROP TABLE users;
