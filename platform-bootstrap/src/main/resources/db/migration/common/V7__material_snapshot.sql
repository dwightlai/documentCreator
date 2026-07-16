CREATE TABLE ai_writing_material_snapshot (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    task_id VARCHAR(32) NOT NULL,
    snapshot_version INTEGER NOT NULL,
    material_id VARCHAR(32) NOT NULL,
    material_version INTEGER,
    file_hash VARCHAR(64),
    selected_fragment TEXT,
    knowledge_entry_version INTEGER,
    retrieval_time TIMESTAMP WITH TIME ZONE,
    permission_check_result VARCHAR(10),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_material_snapshot_task FOREIGN KEY (task_id) REFERENCES ai_writing_task(id),
    CONSTRAINT fk_material_snapshot_material FOREIGN KEY (material_id) REFERENCES ai_writing_material(id),
    CONSTRAINT uq_material_snapshot UNIQUE (tenant_id, task_id, snapshot_version, material_id)
);
CREATE INDEX idx_material_snapshot_task ON ai_writing_material_snapshot(tenant_id, task_id, snapshot_version);
