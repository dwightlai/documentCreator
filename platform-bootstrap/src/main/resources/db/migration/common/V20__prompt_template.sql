CREATE TABLE ai_prompt_template (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    template_code VARCHAR(50) NOT NULL, template_name VARCHAR(200) NOT NULL,
    applicable_scene VARCHAR(50) NOT NULL, document_type_code VARCHAR(20),
    applicable_models TEXT, system_role TEXT, document_type_method TEXT,
    input_fields TEXT, material_citation_rule TEXT, anti_fabrication_rule TEXT,
    output_format TEXT, risk_constraint TEXT, model_parameters TEXT,
    current_version INTEGER NOT NULL DEFAULT 1, lifecycle_status VARCHAR(10) NOT NULL,
    test_sample_ids TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_prompt_template_code UNIQUE (tenant_id, template_code, is_deleted)
);
CREATE INDEX idx_prompt_template_scene ON ai_prompt_template(tenant_id, applicable_scene, document_type_code, lifecycle_status);

CREATE TABLE ai_prompt_template_version (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    template_id VARCHAR(32) NOT NULL, version_number INTEGER NOT NULL,
    system_role TEXT, full_prompt TEXT NOT NULL, change_description TEXT,
    test_results TEXT, approved_by VARCHAR(32), approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_prompt_template_version FOREIGN KEY (template_id) REFERENCES ai_prompt_template(id),
    CONSTRAINT uq_prompt_template_version UNIQUE (tenant_id, template_id, version_number)
);
