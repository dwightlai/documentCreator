-- A2 一键套红：按输出模板（无已发布模板时用内置标准版式）排版生成的定稿稿。记录排版依据的正文版本，正文更新后提示重新套红；
-- 在线编辑（ONLYOFFICE）保存的新文件同样追加一条记录，parent_id 指向被编辑的定稿稿。
CREATE TABLE ai_document_typeset (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    document_version INTEGER NOT NULL,
    template_id VARCHAR(32),
    template_name VARCHAR(100) NOT NULL,
    field_values TEXT,
    deviations TEXT,
    deviation_count INTEGER NOT NULL DEFAULT 0,
    file_path VARCHAR(500) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    file_size INTEGER NOT NULL,
    source VARCHAR(10) NOT NULL,
    parent_id VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_typeset_document FOREIGN KEY (document_id) REFERENCES ai_document(id)
);
CREATE INDEX idx_typeset_document ON ai_document_typeset(tenant_id, document_id, created_at);
