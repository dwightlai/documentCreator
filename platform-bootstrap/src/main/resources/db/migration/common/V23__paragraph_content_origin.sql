-- §33 AI 生成内容标识：段落内容来源类型（AI生成、AI改写、AI推荐、人工录入、引用原文），与证据层级 content_source_level 相互独立。
ALTER TABLE ai_document_paragraph ADD COLUMN content_origin VARCHAR(20);
UPDATE ai_document_paragraph SET content_origin = CASE
    WHEN content_source_level = '手工' THEN '人工录入'
    WHEN content_source_level = '有直接来源' THEN '引用原文'
    ELSE 'AI生成'
END;
