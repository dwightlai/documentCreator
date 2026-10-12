-- §14.4 版本保留策略：管理员保护标记、到期标记时间与正文内容清理时间（清理后仅保留版本元数据）。
ALTER TABLE ai_document_version ADD COLUMN is_protected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ai_document_version ADD COLUMN retention_marked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ai_document_version ADD COLUMN content_purged_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_document_version_retention ON ai_document_version(tenant_id, is_retained, retention_marked_at);
