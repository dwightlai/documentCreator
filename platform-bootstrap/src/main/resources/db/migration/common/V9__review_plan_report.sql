CREATE TABLE ai_review_plan (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,plan_code VARCHAR(50) NOT NULL,plan_name VARCHAR(200) NOT NULL,
    applicable_scenarios TEXT,review_levels TEXT NOT NULL,rule_set_version VARCHAR(20),knowledge_scope TEXT,
    allow_external_model BOOLEAN NOT NULL DEFAULT FALSE,risk_threshold VARCHAR(10) NOT NULL DEFAULT '一般',
    check_attachments BOOLEAN NOT NULL DEFAULT TRUE,allow_auto_fix BOOLEAN NOT NULL DEFAULT FALSE,
    generate_report BOOLEAN NOT NULL DEFAULT TRUE,is_custom BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,updated_by VARCHAR(32) NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_review_plan_code UNIQUE(tenant_id,plan_code)
);
CREATE INDEX idx_review_plan_tenant ON ai_review_plan(tenant_id,is_custom);

CREATE TABLE ai_review_report (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,review_task_id VARCHAR(32) NOT NULL,report_version INTEGER NOT NULL,
    total_issues INTEGER NOT NULL DEFAULT 0,severe_count INTEGER NOT NULL DEFAULT 0,important_count INTEGER NOT NULL DEFAULT 0,
    normal_count INTEGER NOT NULL DEFAULT 0,tip_count INTEGER NOT NULL DEFAULT 0,adopted_count INTEGER NOT NULL DEFAULT 0,
    ignored_count INTEGER NOT NULL DEFAULT 0,pending_count INTEGER NOT NULL DEFAULT 0,report_storage_path VARCHAR(500),
    generated_at TIMESTAMP WITH TIME ZONE,is_final BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,updated_by VARCHAR(32) NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_report_task FOREIGN KEY(review_task_id) REFERENCES ai_review_task(id),
    CONSTRAINT uq_review_report_version UNIQUE(tenant_id,review_task_id,report_version)
);
CREATE INDEX idx_review_report_task ON ai_review_report(tenant_id,review_task_id,report_version);
