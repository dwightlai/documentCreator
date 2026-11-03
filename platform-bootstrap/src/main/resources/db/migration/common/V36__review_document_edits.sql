ALTER TABLE ai_review_task ADD COLUMN edited_file_id VARCHAR(512);
ALTER TABLE ai_review_task ADD COLUMN edit_version INTEGER DEFAULT 0;
ALTER TABLE ai_review_task ADD COLUMN review_title VARCHAR(200);
