CREATE TABLE public_source_pack_install (
    tenant_id VARCHAR(32) NOT NULL,
    pack_code VARCHAR(50) NOT NULL,
    pack_version VARCHAR(50) NOT NULL,
    installed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    installed_by VARCHAR(32) NOT NULL,
    PRIMARY KEY (tenant_id, pack_code, pack_version)
);

CREATE TABLE public_source_pack_source (
    tenant_id VARCHAR(32) NOT NULL,
    source_code VARCHAR(50) NOT NULL,
    source_id VARCHAR(32) NOT NULL,
    PRIMARY KEY (tenant_id, source_code)
);

CREATE TABLE public_source_pack_entry (
    tenant_id VARCHAR(32) NOT NULL,
    entry_code VARCHAR(50) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    installed_version VARCHAR(50) NOT NULL,
    PRIMARY KEY (tenant_id, entry_code)
);
