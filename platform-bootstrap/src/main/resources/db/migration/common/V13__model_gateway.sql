CREATE TABLE ai_model_config (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,config_code VARCHAR(50) NOT NULL,config_name VARCHAR(200) NOT NULL,
    provider VARCHAR(50) NOT NULL,model_name VARCHAR(100) NOT NULL,model_version VARCHAR(50),scene VARCHAR(50) NOT NULL,
    data_level VARCHAR(10) NOT NULL DEFAULT '普通',max_context_tokens INTEGER,timeout_ms INTEGER NOT NULL DEFAULT 60000,
    max_retries INTEGER NOT NULL DEFAULT 3,cost_limit_daily DECIMAL(10,2),sensitive_word_filter BOOLEAN NOT NULL DEFAULT TRUE,
    output_validation BOOLEAN NOT NULL DEFAULT TRUE,is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL,created_by VARCHAR(32) NOT NULL,updated_by VARCHAR(32) NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_model_config_code UNIQUE(tenant_id,config_code)
);
CREATE INDEX idx_model_config_scene ON ai_model_config(tenant_id,scene,is_active);
CREATE INDEX idx_model_config_provider ON ai_model_config(tenant_id,provider);
CREATE TABLE ai_model_call_log (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,task_type VARCHAR(20) NOT NULL,task_id VARCHAR(32) NOT NULL,
    model_config_id VARCHAR(32),prompt_template_id VARCHAR(32),prompt_template_version INTEGER,input_hash VARCHAR(64),
    input_data_level VARCHAR(10),input_summary VARCHAR(500),output_hash VARCHAR(64),token_count_input INTEGER,token_count_output INTEGER,
    duration_ms INTEGER,cost_amount DECIMAL(10,4),success BOOLEAN,error_message TEXT,called_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_model_call_config FOREIGN KEY(model_config_id) REFERENCES ai_model_config(id)
);
CREATE INDEX idx_model_call_time ON ai_model_call_log(tenant_id,called_at);
CREATE INDEX idx_model_call_task ON ai_model_call_log(tenant_id,task_type,task_id);
