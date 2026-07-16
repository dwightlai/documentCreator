CREATE TABLE ai_review_false_positive (
    id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32) NOT NULL,issue_id VARCHAR(32),rule_id VARCHAR(32),
    original_text TEXT NOT NULL,suggested_fix TEXT,reason TEXT NOT NULL,submitter_id VARCHAR(32) NOT NULL,
    review_status VARCHAR(10) NOT NULL DEFAULT '待审核',reviewed_by VARCHAR(32),optimization_suggestion TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,updated_by VARCHAR(32) NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_false_positive_issue FOREIGN KEY(issue_id) REFERENCES ai_review_issue(id),
    CONSTRAINT fk_false_positive_rule FOREIGN KEY(rule_id) REFERENCES ai_review_rule(id)
);
CREATE INDEX idx_false_positive_status ON ai_review_false_positive(tenant_id,review_status,created_at);
