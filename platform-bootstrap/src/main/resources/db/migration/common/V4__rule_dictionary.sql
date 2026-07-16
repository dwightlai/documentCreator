CREATE TABLE ai_review_rule (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    rule_code VARCHAR(50) NOT NULL, rule_name VARCHAR(200) NOT NULL, category VARCHAR(20) NOT NULL,
    applicable_document_types TEXT, risk_level VARCHAR(10) NOT NULL, detection_method VARCHAR(20) NOT NULL,
    rule_expression TEXT, basis TEXT, lifecycle_status VARCHAR(10) NOT NULL,
    effective_date DATE, expiry_date DATE, current_version INTEGER NOT NULL DEFAULT 1, enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_rule_dates CHECK (effective_date IS NULL OR expiry_date IS NULL OR expiry_date >= effective_date)
);
CREATE INDEX idx_rule_code ON ai_review_rule(tenant_id, rule_code, is_deleted);
CREATE INDEX idx_rule_category_status ON ai_review_rule(tenant_id, category, lifecycle_status);

CREATE TABLE ai_review_rule_version (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    rule_id VARCHAR(32) NOT NULL, version_number INTEGER NOT NULL, rule_expression TEXT,
    change_description TEXT, approved_by VARCHAR(32), approved_at TIMESTAMP WITH TIME ZONE, test_results TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_rule_version_rule FOREIGN KEY (rule_id) REFERENCES ai_review_rule(id),
    CONSTRAINT uq_rule_version UNIQUE (tenant_id, rule_id, version_number)
);

CREATE TABLE ai_review_dictionary (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    dict_code VARCHAR(50) NOT NULL, dict_name VARCHAR(200) NOT NULL, dict_type VARCHAR(20) NOT NULL,
    source_description TEXT, effective_date DATE, expiry_date DATE,
    current_version INTEGER NOT NULL DEFAULT 1, entry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_dictionary_dates CHECK (effective_date IS NULL OR expiry_date IS NULL OR expiry_date >= effective_date)
);
CREATE INDEX idx_dictionary_code ON ai_review_dictionary(tenant_id, dict_code, is_deleted);
CREATE INDEX idx_dictionary_type ON ai_review_dictionary(tenant_id, dict_type);

CREATE TABLE ai_review_term (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    dictionary_id VARCHAR(32) NOT NULL, term_text VARCHAR(500) NOT NULL, standard_form VARCHAR(500), aliases TEXT,
    category_tag VARCHAR(50), risk_level VARCHAR(10), source_reference TEXT,
    effective_date DATE, expiry_date DATE, usage_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_term_dictionary FOREIGN KEY (dictionary_id) REFERENCES ai_review_dictionary(id),
    CONSTRAINT ck_term_dates CHECK (effective_date IS NULL OR expiry_date IS NULL OR expiry_date >= effective_date),
    CONSTRAINT uq_dictionary_term UNIQUE (tenant_id, dictionary_id, term_text, is_deleted)
);
CREATE INDEX idx_term_category ON ai_review_term(tenant_id, dictionary_id, category_tag);
