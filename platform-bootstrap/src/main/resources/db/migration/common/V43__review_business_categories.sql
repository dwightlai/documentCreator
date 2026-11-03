-- Preserve rows and their identifiers. Flyway runs this migration once per database.
ALTER TABLE ai_review_issue ALTER COLUMN detection_method TYPE VARCHAR(200);

UPDATE ai_review_rule SET category = CASE
    WHEN category IN ('公文格式', '版式核验', '数字用法', '格式版式') THEN '格式与版式'
    WHEN category IN ('标点符号', '文字', '错别字', '规范', '规范用语', '词语规范') THEN '语言文字'
    WHEN category IN ('行文规则', '文种选用', '保密要求', '公文规则', '内容安全', '敏感词') THEN '公文业务规范'
    WHEN category IN ('事实数据', '政策法规') THEN '政策法规与事实依据'
    WHEN category IN ('政治表述') THEN '政治与重要表述'
    WHEN category IN ('内容审核', '逻辑语义', '逻辑') THEN '内容与逻辑'
    ELSE category END
WHERE category IN ('公文格式', '版式核验', '数字用法', '格式版式', '标点符号', '文字', '错别字',
    '规范', '规范用语', '词语规范', '行文规则', '文种选用', '保密要求', '公文规则', '内容安全',
    '敏感词', '事实数据', '政策法规', '政治表述', '内容审核', '逻辑语义', '逻辑');

UPDATE ai_review_issue SET issue_category = CASE
    WHEN issue_category IN ('公文格式', '版式核验', '数字用法', '格式版式') THEN '格式与版式'
    WHEN issue_category IN ('标点符号', '文字', '错别字', '规范', '规范用语', '词库', '词语规范') THEN '语言文字'
    WHEN issue_category IN ('行文规则', '文种选用', '保密要求', '公文规则', '内容安全', '敏感词') THEN '公文业务规范'
    WHEN issue_category IN ('事实数据', '政策法规') THEN '政策法规与事实依据'
    WHEN issue_category IN ('政治表述') THEN '政治与重要表述'
    WHEN issue_category IN ('内容审核', '逻辑语义', '逻辑') THEN '内容与逻辑'
    ELSE issue_category END
WHERE issue_category IN ('公文格式', '版式核验', '数字用法', '格式版式', '标点符号', '文字', '错别字',
    '规范', '规范用语', '词库', '词语规范', '行文规则', '文种选用', '保密要求', '公文规则',
    '内容安全', '敏感词', '事实数据', '政策法规', '政治表述', '内容审核', '逻辑语义', '逻辑');

-- Existing custom plans stored execution levels as JSON text. Convert them to business scopes.
UPDATE ai_review_plan SET review_levels =
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(review_levels,
        '"L1"', '"语言文字"'), '"L2"', '"语言文字"'),
        '"L3"', '"公文业务规范"'), '"L4"', '"格式与版式"'),
        '"L5"', '"政策法规与事实依据"'), '"L6"', '"内容与逻辑"'),
        '"L7"', '"政治与重要表述"')
WHERE review_levels LIKE '%"L1"%' OR review_levels LIKE '%"L2"%' OR review_levels LIKE '%"L3"%'
   OR review_levels LIKE '%"L4"%' OR review_levels LIKE '%"L5"%' OR review_levels LIKE '%"L6"%'
   OR review_levels LIKE '%"L7"%';

-- Dictionary types and term tags are data-object labels, so they remain unchanged.
