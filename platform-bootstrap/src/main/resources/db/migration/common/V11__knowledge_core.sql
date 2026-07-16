CREATE TABLE ai_knowledge_source (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,source_code VARCHAR(50) NOT NULL,source_name VARCHAR(200) NOT NULL,
    category VARCHAR(20) NOT NULL,storage_type VARCHAR(20) NOT NULL,vector_model VARCHAR(100),vector_dimensions INTEGER,
    permission_scope TEXT,entry_count INTEGER NOT NULL DEFAULT 0,last_sync_time TIMESTAMP WITH TIME ZONE,is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL,created_by VARCHAR(32) NOT NULL,updated_by VARCHAR(32) NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_knowledge_source_code UNIQUE(tenant_id,source_code)
);
CREATE INDEX idx_knowledge_source_category ON ai_knowledge_source(tenant_id,category);
CREATE TABLE ai_knowledge_document (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,source_id VARCHAR(32) NOT NULL,document_title VARCHAR(500) NOT NULL,
    original_file_path VARCHAR(500),original_file_hash VARCHAR(64),parsed_status VARCHAR(10) NOT NULL DEFAULT '待解析',metadata TEXT,
    authority_level VARCHAR(20),effective_date DATE,expiry_date DATE,permission_level VARCHAR(10),chunk_count INTEGER NOT NULL DEFAULT 0,
    document_version INTEGER NOT NULL DEFAULT 1,extracted_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL,created_by VARCHAR(32) NOT NULL,updated_by VARCHAR(32) NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_knowledge_document_source FOREIGN KEY(source_id) REFERENCES ai_knowledge_source(id)
);
CREATE INDEX idx_knowledge_document_status ON ai_knowledge_document(tenant_id,source_id,parsed_status);
CREATE INDEX idx_knowledge_document_effective ON ai_knowledge_document(tenant_id,source_id,effective_date,expiry_date);
CREATE TABLE ai_knowledge_chunk (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,document_id VARCHAR(32) NOT NULL,chunk_order INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,chunk_text_hash VARCHAR(64),vector_id VARCHAR(100),keywords TEXT,start_position INTEGER,end_position INTEGER,page_number INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL,created_by VARCHAR(32) NOT NULL,updated_by VARCHAR(32) NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_knowledge_chunk_document FOREIGN KEY(document_id) REFERENCES ai_knowledge_document(id),
    CONSTRAINT uq_knowledge_chunk_order UNIQUE(tenant_id,document_id,chunk_order)
);
CREATE INDEX idx_knowledge_chunk_document ON ai_knowledge_chunk(tenant_id,document_id,chunk_order);
