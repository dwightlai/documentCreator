-- V43 maps the old generic content category to 内容与逻辑.
-- These two existing rules have a more precise basis in policy and fact verification.
UPDATE ai_review_rule
SET category = '政策法规与事实依据'
WHERE rule_code IN ('STD-CONTENT-BASIS', 'STD-CONTENT-ACCURACY')
  AND category = '内容与逻辑';
