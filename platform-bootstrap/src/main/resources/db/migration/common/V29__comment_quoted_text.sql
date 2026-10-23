-- A7 正文批注：记录批注针对的选中文字，便于在段落新版本中继续定位；为空表示针对整段。
ALTER TABLE ai_document_comment ADD COLUMN quoted_text TEXT;
