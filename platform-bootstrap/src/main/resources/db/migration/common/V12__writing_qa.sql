CREATE TABLE ai_writing_qa (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,task_id VARCHAR(32) NOT NULL,question_text TEXT NOT NULL,
    answer_text TEXT NOT NULL,source_references TEXT,answer_type VARCHAR(20) NOT NULL,inserted BOOLEAN NOT NULL DEFAULT FALSE,
    inserted_document_id VARCHAR(32),inserted_paragraph_id VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL,created_by VARCHAR(32) NOT NULL,updated_by VARCHAR(32) NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_writing_qa_task FOREIGN KEY(task_id) REFERENCES ai_writing_task(id),
    CONSTRAINT fk_writing_qa_document FOREIGN KEY(inserted_document_id) REFERENCES ai_document(id),
    CONSTRAINT fk_writing_qa_paragraph FOREIGN KEY(inserted_paragraph_id) REFERENCES ai_document_paragraph(id)
);
CREATE INDEX idx_writing_qa_task ON ai_writing_qa(tenant_id,task_id,created_at);
