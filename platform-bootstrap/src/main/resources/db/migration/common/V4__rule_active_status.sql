UPDATE ai_review_rule
SET lifecycle_status = '生效', enabled = TRUE
WHERE lifecycle_status IN ('草稿', '测试', '审核');
