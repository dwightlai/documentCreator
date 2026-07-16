ALTER TABLE ai_model_config ADD COLUMN endpoint_url VARCHAR(500);
ALTER TABLE ai_model_config ADD COLUMN credential_ref VARCHAR(100);
ALTER TABLE ai_model_config ADD COLUMN input_cost_per_1k DECIMAL(10,6);
ALTER TABLE ai_model_config ADD COLUMN output_cost_per_1k DECIMAL(10,6);
ALTER TABLE ai_model_config ADD COLUMN qps_limit INTEGER;
ALTER TABLE ai_model_config ADD COLUMN daily_token_limit INTEGER;
