ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS input_content TEXT;
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS output_content TEXT;
