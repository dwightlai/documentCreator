CREATE TABLE ai_document_type_config (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    type_code VARCHAR(20) NOT NULL, type_name VARCHAR(50) NOT NULL,
    document_category VARCHAR(10) NOT NULL, applicable_scenario TEXT,
    direction VARCHAR(10), required_fields TEXT, recommended_structure TEXT,
    forbidden_patterns TEXT, closing_patterns TEXT,
    current_version INTEGER NOT NULL DEFAULT 1, lifecycle_status VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_type_config_code UNIQUE (tenant_id, type_code, is_deleted)
);
CREATE INDEX idx_document_type_config_filters
    ON ai_document_type_config(tenant_id, document_category, direction, lifecycle_status);

CREATE TABLE ai_document_type_config_version (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    config_id VARCHAR(32) NOT NULL, version_number INTEGER NOT NULL,
    full_config TEXT NOT NULL, change_description TEXT,
    test_results TEXT, approved_by VARCHAR(32), approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_document_type_config_version FOREIGN KEY (config_id) REFERENCES ai_document_type_config(id),
    CONSTRAINT uq_document_type_config_version UNIQUE (tenant_id, config_id, version_number)
);
