CREATE TABLE ai_idempotency_record (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    endpoint VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    http_status INTEGER,
    content_type VARCHAR(200),
    response_body TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_idempotency_scope UNIQUE (tenant_id, user_id, endpoint, idempotency_key)
);

CREATE INDEX idx_idempotency_expiry ON ai_idempotency_record(expires_at);
