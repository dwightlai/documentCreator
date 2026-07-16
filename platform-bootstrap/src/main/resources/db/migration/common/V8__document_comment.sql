CREATE TABLE ai_document_comment (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    paragraph_id VARCHAR(32),
    commenter_id VARCHAR(32) NOT NULL,
    comment_type VARCHAR(10),
    comment_text TEXT NOT NULL,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by VARCHAR(32),
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_comment_document FOREIGN KEY (document_id) REFERENCES ai_document(id),
    CONSTRAINT fk_comment_paragraph FOREIGN KEY (paragraph_id) REFERENCES ai_document_paragraph(id)
);
CREATE INDEX idx_comment_document_user ON ai_document_comment(tenant_id, document_id, commenter_id);
CREATE INDEX idx_comment_document_resolved ON ai_document_comment(tenant_id, document_id, is_resolved);
