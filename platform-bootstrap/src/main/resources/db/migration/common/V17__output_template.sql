CREATE TABLE ai_output_template (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    template_code VARCHAR(50) NOT NULL, template_name VARCHAR(200) NOT NULL,
    document_type_code VARCHAR(20) NOT NULL, template_file_path VARCHAR(500) NOT NULL,
    template_file_hash VARCHAR(64), has_red_header BOOLEAN NOT NULL DEFAULT FALSE,
    has_footer_mark BOOLEAN NOT NULL DEFAULT FALSE, placeholder_map TEXT NOT NULL,
    direction VARCHAR(10), description TEXT, current_version INTEGER NOT NULL DEFAULT 1,
    lifecycle_status VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_output_template_code UNIQUE (tenant_id, template_code)
);
CREATE INDEX idx_output_template_type ON ai_output_template(tenant_id, document_type_code);
CREATE INDEX idx_output_template_status ON ai_output_template(tenant_id, lifecycle_status);

CREATE TABLE ai_output_template_version (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    template_id VARCHAR(32) NOT NULL, version_number INTEGER NOT NULL,
    template_file_path VARCHAR(500) NOT NULL, template_file_hash VARCHAR(64),
    change_note VARCHAR(500), lifecycle_status VARCHAR(10) NOT NULL,
    approved_by VARCHAR(32), approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_output_template_version UNIQUE (tenant_id, template_id, version_number),
    CONSTRAINT fk_output_template_version_template FOREIGN KEY (template_id) REFERENCES ai_output_template(id)
);
CREATE INDEX idx_output_template_version ON ai_output_template_version(tenant_id, template_id, version_number);
