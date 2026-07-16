CREATE TABLE ai_async_job (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    business_id VARCHAR(100),
    payload TEXT,
    status VARCHAR(20) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    result_summary TEXT,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    lock_version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_async_job_queue ON ai_async_job(status, next_retry_at, created_at);
CREATE INDEX idx_async_job_tenant_created ON ai_async_job(tenant_id, created_at);

CREATE TABLE ai_async_job_event (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    job_id VARCHAR(32) NOT NULL,
    event_sequence INTEGER NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    event_data TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_async_job_event UNIQUE (tenant_id, job_id, event_sequence),
    CONSTRAINT fk_async_job_event_job FOREIGN KEY (job_id) REFERENCES ai_async_job(id)
);

CREATE INDEX idx_async_job_event_replay ON ai_async_job_event(tenant_id, job_id, event_sequence);
