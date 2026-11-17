UPDATE ai_prompt_template
SET lifecycle_status = '生效'
WHERE lifecycle_status IN ('草稿', '测试', '审核');

UPDATE ai_output_template
SET lifecycle_status = '生效'
WHERE lifecycle_status IN ('草稿', '测试', '审核');

UPDATE ai_output_template_version
SET lifecycle_status = '生效'
WHERE lifecycle_status IN ('草稿', '测试', '审核');
