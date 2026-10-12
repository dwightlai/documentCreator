-- §26.2A 向量化：数据库向量存储模式下以逻辑 CLOB 保存切片向量（JSON 数组）；Milvus 模式下仅在 vector_id 记录外部主键。
ALTER TABLE ai_knowledge_chunk ADD COLUMN embedding TEXT;
