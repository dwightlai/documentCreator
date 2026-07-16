ALTER TABLE ai_review_issue ADD COLUMN paragraph_number INTEGER;
CREATE INDEX idx_review_issue_locator ON ai_review_issue(tenant_id, review_task_id, page_number, paragraph_number);
