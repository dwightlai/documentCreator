CREATE TABLE official_review_pack_install (
    tenant_id VARCHAR(32) NOT NULL,
    pack_code VARCHAR(64) NOT NULL,
    pack_version VARCHAR(32) NOT NULL,
    installed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    installed_by VARCHAR(32) NOT NULL,
    PRIMARY KEY (tenant_id, pack_code, pack_version)
);
