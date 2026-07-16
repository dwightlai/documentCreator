CREATE TABLE ai_review_task (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    source_system VARCHAR(20) NOT NULL DEFAULT 'internal', business_id VARCHAR(100),
    document_id VARCHAR(32), document_file_id VARCHAR(100), document_type_code VARCHAR(20),
    review_plan_id VARCHAR(32), writing_task_id VARCHAR(32), output_template_id VARCHAR(32),
    status VARCHAR(20) NOT NULL, initiator_id VARCHAR(32) NOT NULL, callback_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_document FOREIGN KEY (document_id) REFERENCES ai_document(id),
    CONSTRAINT fk_review_writing_task FOREIGN KEY (writing_task_id) REFERENCES ai_writing_task(id)
);
CREATE INDEX idx_review_task_status ON ai_review_task(tenant_id, status);
CREATE INDEX idx_review_task_document ON ai_review_task(tenant_id, document_id);

CREATE TABLE ai_review_issue (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    review_task_id VARCHAR(32) NOT NULL, issue_number VARCHAR(20) NOT NULL,
    issue_category VARCHAR(20) NOT NULL, risk_level VARCHAR(10) NOT NULL,
    page_number INTEGER, paragraph_id VARCHAR(32), char_position_start INTEGER, char_position_end INTEGER,
    original_text TEXT NOT NULL, suggested_fix TEXT, issue_explanation TEXT, rule_basis TEXT,
    detection_method VARCHAR(20) NOT NULL, confidence_score DECIMAL(3,2),
    processing_status VARCHAR(10) NOT NULL DEFAULT '待处理', processing_note TEXT,
    processed_by VARCHAR(32), processed_at TIMESTAMP WITH TIME ZONE, merged_group_id VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_issue_task FOREIGN KEY (review_task_id) REFERENCES ai_review_task(id),
    CONSTRAINT fk_review_issue_paragraph FOREIGN KEY (paragraph_id) REFERENCES ai_document_paragraph(id),
    CONSTRAINT uq_review_issue_number UNIQUE (tenant_id, review_task_id, issue_number)
);
CREATE INDEX idx_review_issue_filters ON ai_review_issue(tenant_id, review_task_id, issue_category, risk_level, processing_status);

CREATE TABLE ai_review_issue_action (
    id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32) NOT NULL,
    issue_id VARCHAR(32) NOT NULL, action_type VARCHAR(20) NOT NULL, action_detail TEXT,
    operator_id VARCHAR(32) NOT NULL, related_issue_ids TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL, updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_issue_action_issue FOREIGN KEY (issue_id) REFERENCES ai_review_issue(id)
);
