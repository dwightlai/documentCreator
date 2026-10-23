-- A7 修订模式：段落修订建议。保存提出时的原文快照和段落位置（不依赖可能被版本保留策略清理的段落行），
-- 接受时比对当前同一位置的段落是否仍是原文，已被修改则视为过期。
CREATE TABLE ai_document_suggestion (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    base_paragraph_id VARCHAR(32) NOT NULL,
    base_version INTEGER NOT NULL,
    outline_node_id VARCHAR(32),
    paragraph_order INTEGER NOT NULL,
    base_plain_text TEXT NOT NULL,
    base_rich_content TEXT,
    proposed_plain_text TEXT NOT NULL,
    proposed_rich_content TEXT,
    adopted_generation_ids TEXT,
    status VARCHAR(10) NOT NULL DEFAULT '待处理',
    proposer_id VARCHAR(32) NOT NULL,
    decided_by VARCHAR(32),
    decided_at TIMESTAMP WITH TIME ZONE,
    decision_note VARCHAR(500),
    accepted_version INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_suggestion_document FOREIGN KEY (document_id) REFERENCES ai_document(id)
);
CREATE INDEX idx_suggestion_document_status ON ai_document_suggestion(tenant_id, document_id, status);
