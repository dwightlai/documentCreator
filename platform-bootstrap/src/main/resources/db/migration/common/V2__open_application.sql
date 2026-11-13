CREATE TABLE open_application (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    app_code character varying(32) NOT NULL,
    app_name character varying(100) NOT NULL,
    app_type character varying(32) NOT NULL,
    auth_type character varying(32) NOT NULL,
    status character varying(16) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
CREATE TABLE open_application_key (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    app_id character varying(32) NOT NULL,
    key_prefix character varying(16) NOT NULL,
    key_hash character varying(64) NOT NULL,
    status character varying(16) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE TABLE open_application_grant (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    app_id character varying(32) NOT NULL,
    scope_type character varying(16) NOT NULL,
    scope_code character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
ALTER TABLE open_application ADD CONSTRAINT open_application_pkey PRIMARY KEY (id);
ALTER TABLE open_application_key ADD CONSTRAINT open_application_key_pkey PRIMARY KEY (id);
ALTER TABLE open_application_grant ADD CONSTRAINT open_application_grant_pkey PRIMARY KEY (id);
ALTER TABLE open_application ADD CONSTRAINT uk_open_application_code UNIQUE (tenant_id, app_code);
ALTER TABLE open_application_key ADD CONSTRAINT uk_open_application_key_hash UNIQUE (key_hash);
ALTER TABLE open_application_grant ADD CONSTRAINT uk_open_application_grant UNIQUE (app_id, scope_type, scope_code);
CREATE INDEX idx_open_application_key_app ON open_application_key (app_id);
CREATE INDEX idx_open_application_grant_app ON open_application_grant (app_id);
