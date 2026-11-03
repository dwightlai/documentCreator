ALTER TABLE ai_knowledge_source ALTER COLUMN category VARCHAR(100);

ALTER TABLE ai_knowledge_document ADD COLUMN usage_type VARCHAR(20) NOT NULL DEFAULT '待分类';
ALTER TABLE ai_knowledge_document ADD COLUMN completeness VARCHAR(20) NOT NULL DEFAULT '来源索引';
ALTER TABLE ai_knowledge_document ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT '待核验';
ALTER TABLE ai_knowledge_document ADD COLUMN validity_status VARCHAR(20) NOT NULL DEFAULT '待核验';
ALTER TABLE ai_knowledge_document ADD COLUMN publisher VARCHAR(200);
ALTER TABLE ai_knowledge_document ADD COLUMN document_number VARCHAR(100);
ALTER TABLE ai_knowledge_document ADD COLUMN issued_date DATE;
ALTER TABLE ai_knowledge_document ADD COLUMN applicable_region VARCHAR(100);
ALTER TABLE ai_knowledge_document ADD COLUMN applicable_audience VARCHAR(200);
ALTER TABLE ai_knowledge_document ADD COLUMN applicable_scope VARCHAR(500);
ALTER TABLE ai_knowledge_document ADD COLUMN reviewed_by VARCHAR(32);
ALTER TABLE ai_knowledge_document ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;

-- 已保存的公开页全文在本地 extracted_text 和切片中；来源卡仍属于索引。
UPDATE ai_knowledge_document SET completeness='本地原文可用'
WHERE metadata LIKE '%"offlineContentType":"公开页正文"%' AND extracted_text IS NOT NULL;
UPDATE ai_knowledge_document SET completeness='原文摘录'
WHERE metadata LIKE '%"offlineContentType":"原文摘录"%' AND extracted_text IS NOT NULL;

CREATE TABLE ai_writing_knowledge_selection (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    task_id VARCHAR(32) NOT NULL,
    outline_node_id VARCHAR(32) NOT NULL,
    source_id VARCHAR(32) NOT NULL,
    knowledge_document_id VARCHAR(32) NOT NULL,
    chunk_id VARCHAR(32) NOT NULL,
    document_version INTEGER NOT NULL,
    usage_type VARCHAR(20) NOT NULL,
    document_title VARCHAR(500) NOT NULL,
    excerpt TEXT NOT NULL,
    file_hash VARCHAR(64),
    selection_reason VARCHAR(500) NOT NULL,
    selected_by VARCHAR(32) NOT NULL,
    selected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_writing_knowledge_selection_task ON ai_writing_knowledge_selection(tenant_id, task_id, outline_node_id);

ALTER TABLE ai_document_paragraph ADD COLUMN source_knowledge_ids TEXT;

ALTER TABLE ai_document_citation ADD COLUMN knowledge_document_id VARCHAR(32);
ALTER TABLE ai_document_citation ADD COLUMN knowledge_chunk_id VARCHAR(32);
ALTER TABLE ai_document_citation ADD COLUMN knowledge_document_version INTEGER;
ALTER TABLE ai_document_citation ADD COLUMN source_file_hash VARCHAR(64);
ALTER TABLE ai_document_citation ADD COLUMN source_excerpt TEXT;
ALTER TABLE ai_document_citation ADD COLUMN review_status VARCHAR(20);
ALTER TABLE ai_document_citation ADD COLUMN confirmed_by VARCHAR(32);
ALTER TABLE ai_document_citation ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE ai_knowledge_document_revision (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    document_version INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    captured_by VARCHAR(32) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_knowledge_revision UNIQUE (tenant_id, document_id, document_version)
);
