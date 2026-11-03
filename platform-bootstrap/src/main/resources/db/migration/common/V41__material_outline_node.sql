ALTER TABLE ai_writing_material ADD COLUMN outline_node_id VARCHAR(32);
CREATE INDEX idx_material_task_node ON ai_writing_material(tenant_id, task_id, outline_node_id);
