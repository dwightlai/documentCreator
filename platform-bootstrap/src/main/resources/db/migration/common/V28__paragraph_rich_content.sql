-- A7 富文本编辑：段落格式化内容（编辑器 JSON 文档，含加粗、列表、表格）。plain_text 仍是审校、字数统计和冲突比对的依据，由服务端从富文本推导；为空表示纯文本段落。
ALTER TABLE ai_document_paragraph ADD COLUMN rich_content TEXT;
