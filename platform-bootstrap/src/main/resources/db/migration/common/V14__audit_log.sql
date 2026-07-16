CREATE TABLE ai_audit_log (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,audit_type VARCHAR(20) NOT NULL,operator_id VARCHAR(32) NOT NULL,
    operator_role VARCHAR(500),target_type VARCHAR(30) NOT NULL,target_id VARCHAR(32) NOT NULL,action VARCHAR(30) NOT NULL,
    detail TEXT,data_level VARCHAR(10),ip_address VARCHAR(50),occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_tenant_time ON ai_audit_log(tenant_id,occurred_at);
CREATE INDEX idx_audit_type_time ON ai_audit_log(tenant_id,audit_type,occurred_at);
