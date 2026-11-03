ALTER TABLE ai_model_config DROP CONSTRAINT uq_model_config_code;
ALTER TABLE ai_model_config ADD CONSTRAINT uq_model_config_code UNIQUE (tenant_id, config_code, is_deleted);
