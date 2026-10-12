-- §31.1 从 OA 发起写作：记录来源系统、业务单据编号、拟稿部门与回传地址。
ALTER TABLE ai_writing_task ADD COLUMN source_system VARCHAR(20);
ALTER TABLE ai_writing_task ADD COLUMN business_id VARCHAR(100);
ALTER TABLE ai_writing_task ADD COLUMN drafting_department VARCHAR(100);
ALTER TABLE ai_writing_task ADD COLUMN callback_url VARCHAR(500);
CREATE INDEX idx_writing_task_business ON ai_writing_task(tenant_id, source_system, business_id);

-- 审校时的文稿版本，用于判断提交时审校结论是否对应最新文稿。
ALTER TABLE ai_review_task ADD COLUMN document_version INTEGER;

-- §31.3 提交门禁：按单位（租户）配置。
CREATE TABLE ai_submission_gate_config (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    require_review BOOLEAN NOT NULL DEFAULT TRUE,
    require_latest_review BOOLEAN NOT NULL DEFAULT TRUE,
    block_on_severe BOOLEAN NOT NULL DEFAULT TRUE,
    require_important_processed BOOLEAN NOT NULL DEFAULT TRUE,
    require_report BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_submission_gate_tenant UNIQUE (tenant_id)
);

-- 文稿提交记录（追加型）：提交时的版本、门禁结论快照与回传任务。
CREATE TABLE ai_document_submission (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    writing_task_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    document_version INTEGER NOT NULL,
    review_task_id VARCHAR(32),
    target_system VARCHAR(20) NOT NULL,
    business_id VARCHAR(100),
    gate_result TEXT NOT NULL,
    callback_job_id VARCHAR(32),
    submitted_by VARCHAR(32) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_document_submission_task ON ai_document_submission(tenant_id, writing_task_id, submitted_at);
