-- Retire untouched reference-only entries installed by older official review packs.
-- User-created and subsequently edited rules or dictionaries are deliberately retained.
UPDATE ai_review_rule
SET is_deleted = TRUE, enabled = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = FALSE
  AND detection_method = '人工核验'
  AND current_version = 1
  AND updated_at = created_at
  AND EXISTS (
    SELECT 1 FROM ai_review_rule_version v
    WHERE v.tenant_id = ai_review_rule.tenant_id
      AND v.rule_id = ai_review_rule.id
      AND v.version_number = 1
      AND v.change_description LIKE '官方规范数据包 %'
  );

UPDATE ai_review_term
SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = FALSE
  AND updated_at = created_at
  AND EXISTS (
    SELECT 1 FROM ai_review_dictionary d
    JOIN official_review_pack_install i
      ON i.tenant_id = d.tenant_id
     AND i.installed_by = d.created_by
     AND i.installed_at = d.created_at
    WHERE d.id = ai_review_term.dictionary_id
      AND d.tenant_id = ai_review_term.tenant_id
      AND d.current_version = 1
      AND d.updated_at = d.created_at
      AND d.dict_code IN (
        'NAT-DOCUMENT-TYPES-2012', 'NAT-DOCUMENT-ELEMENTS-2012',
        'NAT-DOCUMENT-MARKS-2012', 'NAT-PUNCTUATION-2011',
        'NAT-NUMERAL-CONCEPTS-2011', 'GOV-AGENCIES-2023'
      )
  );

UPDATE ai_review_dictionary
SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = FALSE
  AND current_version = 1
  AND updated_at = created_at
  AND dict_code IN (
    'NAT-DOCUMENT-TYPES-2012', 'NAT-DOCUMENT-ELEMENTS-2012',
    'NAT-DOCUMENT-MARKS-2012', 'NAT-PUNCTUATION-2011',
    'NAT-NUMERAL-CONCEPTS-2011', 'GOV-AGENCIES-2023'
  )
  AND EXISTS (
    SELECT 1 FROM official_review_pack_install i
    WHERE i.tenant_id = ai_review_dictionary.tenant_id
      AND i.installed_by = ai_review_dictionary.created_by
      AND i.installed_at = ai_review_dictionary.created_at
  )
  AND NOT EXISTS (
    SELECT 1 FROM ai_review_term t
    WHERE t.tenant_id = ai_review_dictionary.tenant_id
      AND t.dictionary_id = ai_review_dictionary.id
      AND t.is_deleted = FALSE
  );

-- The local demo seed also created an unreferenced dictionary, a duplicate built-in rule,
-- and a draft rule. Retire only rows that still carry their demo names and seed identity.
UPDATE ai_review_rule
SET is_deleted = TRUE, enabled = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'local-government'
  AND created_by = 'local-user'
  AND rule_code IN ('DEMO-WORD-DEADLINE-001', 'DEMO-SECURITY-001')
  AND rule_name LIKE '演示：%'
  AND is_deleted = FALSE;

UPDATE ai_review_term
SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'local-government'
  AND is_deleted = FALSE
  AND dictionary_id IN (
    SELECT id FROM ai_review_dictionary
    WHERE tenant_id = 'local-government'
      AND created_by = 'local-user'
      AND dict_code = 'DEMO-COMMON-MISTAKES'
      AND dict_name LIKE '演示：%'
      AND is_deleted = FALSE
  );

UPDATE ai_review_dictionary
SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'local-government'
  AND created_by = 'local-user'
  AND dict_code = 'DEMO-COMMON-MISTAKES'
  AND dict_name LIKE '演示：%'
  AND is_deleted = FALSE;
