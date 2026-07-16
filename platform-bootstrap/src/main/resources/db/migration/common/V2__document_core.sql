CREATE TABLE ai_document (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    task_id VARCHAR(32) NOT NULL,
    title VARCHAR(500),
    content_type VARCHAR(10) NOT NULL DEFAULT 'DOCX',
    content_storage_path VARCHAR(500),
    plain_text_cache TEXT,
    total_word_count INTEGER NOT NULL DEFAULT 0,
    security_level VARCHAR(10),
    current_version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_document_task FOREIGN KEY (task_id) REFERENCES ai_writing_task(id),
    CONSTRAINT uq_document_task UNIQUE (tenant_id, task_id)
);
CREATE INDEX idx_document_security ON ai_document(tenant_id, security_level);

CREATE TABLE ai_document_version (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    version_number INTEGER NOT NULL,
    version_type VARCHAR(20) NOT NULL,
    change_source VARCHAR(20),
    change_description TEXT,
    content_storage_path VARCHAR(500),
    word_count INTEGER NOT NULL DEFAULT 0,
    is_retained BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_document_version_document FOREIGN KEY (document_id) REFERENCES ai_document(id),
    CONSTRAINT uq_document_version UNIQUE (tenant_id, document_id, version_number)
);

CREATE TABLE ai_document_paragraph (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    version_number INTEGER NOT NULL,
    outline_node_id VARCHAR(32),
    paragraph_order INTEGER NOT NULL,
    content_source_level VARCHAR(20),
    source_material_ids TEXT,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    plain_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_paragraph_document FOREIGN KEY (document_id) REFERENCES ai_document(id),
    CONSTRAINT fk_paragraph_outline_node FOREIGN KEY (outline_node_id) REFERENCES ai_writing_outline_node(id)
);
CREATE INDEX idx_paragraph_order ON ai_document_paragraph(tenant_id, document_id, version_number, paragraph_order);

CREATE TABLE ai_document_citation (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    document_id VARCHAR(32) NOT NULL,
    paragraph_id VARCHAR(32) NOT NULL,
    material_id VARCHAR(32),
    citation_type VARCHAR(20),
    cited_fragment TEXT,
    position_start INTEGER,
    position_end INTEGER,
    display_mode VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_citation_document FOREIGN KEY (document_id) REFERENCES ai_document(id),
    CONSTRAINT fk_citation_paragraph FOREIGN KEY (paragraph_id) REFERENCES ai_document_paragraph(id),
    CONSTRAINT fk_citation_material FOREIGN KEY (material_id) REFERENCES ai_writing_material(id)
);
CREATE INDEX idx_citation_paragraph ON ai_document_citation(tenant_id, document_id, paragraph_id);
