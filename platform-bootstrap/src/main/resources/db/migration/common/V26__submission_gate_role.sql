-- §31.3 “必须由指定角色确认”：配置后，只有具备该角色的用户才能提交文稿（如 platform.reviewer 表示核稿人员）。
ALTER TABLE ai_submission_gate_config ADD COLUMN required_submit_role VARCHAR(50);
