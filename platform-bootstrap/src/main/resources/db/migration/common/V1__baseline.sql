CREATE TABLE ai_async_job (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    job_type character varying(50) NOT NULL,
    business_id character varying(100),
    payload text,
    status character varying(20) NOT NULL,
    progress integer DEFAULT 0 NOT NULL,
    result_summary text,
    error_message text,
    retry_count integer DEFAULT 0 NOT NULL,
    max_retries integer DEFAULT 5 NOT NULL,
    next_retry_at timestamp with time zone,
    cancel_requested boolean DEFAULT false NOT NULL,
    created_by character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_async_job_event (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    job_id character varying(32) NOT NULL,
    event_sequence integer NOT NULL,
    event_type character varying(20) NOT NULL,
    event_data text,
    occurred_at timestamp with time zone NOT NULL
);
CREATE TABLE ai_audit_log (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    audit_type character varying(20) NOT NULL,
    operator_id character varying(32) NOT NULL,
    operator_role character varying(500),
    target_type character varying(30) NOT NULL,
    target_id character varying(32) NOT NULL,
    action character varying(30) NOT NULL,
    detail text,
    data_level character varying(10),
    ip_address character varying(50),
    occurred_at timestamp with time zone NOT NULL
);
CREATE TABLE ai_data_dict_item (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    type_code character varying(50) NOT NULL,
    item_code character varying(50) NOT NULL,
    item_label character varying(100) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_data_dict_type (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    type_code character varying(50) NOT NULL,
    type_name character varying(100) NOT NULL,
    description character varying(500),
    sort_order integer DEFAULT 0 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_document (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_id character varying(32) NOT NULL,
    title character varying(500),
    content_type character varying(10) DEFAULT 'DOCX'::character varying NOT NULL,
    content_storage_path character varying(500),
    plain_text_cache text,
    total_word_count integer DEFAULT 0 NOT NULL,
    security_level character varying(10),
    current_version integer DEFAULT 1 NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_document_citation (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    document_id character varying(32) NOT NULL,
    paragraph_id character varying(32) NOT NULL,
    material_id character varying(32),
    citation_type character varying(20),
    cited_fragment text,
    position_start integer,
    position_end integer,
    display_mode character varying(20),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    knowledge_document_id character varying(32),
    knowledge_chunk_id character varying(32),
    knowledge_document_version integer,
    source_file_hash character varying(64),
    source_excerpt text,
    review_status character varying(20),
    confirmed_by character varying(32),
    confirmed_at timestamp with time zone
);
CREATE TABLE ai_document_comment (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    document_id character varying(32) NOT NULL,
    paragraph_id character varying(32),
    commenter_id character varying(32) NOT NULL,
    comment_type character varying(10),
    comment_text text NOT NULL,
    is_resolved boolean DEFAULT false NOT NULL,
    resolved_by character varying(32),
    resolved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    quoted_text text
);
CREATE TABLE ai_document_paragraph (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    document_id character varying(32) NOT NULL,
    version_number integer NOT NULL,
    outline_node_id character varying(32),
    paragraph_order integer NOT NULL,
    content_source_level character varying(20),
    source_material_ids text,
    is_locked boolean DEFAULT false NOT NULL,
    plain_text text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    content_origin character varying(20),
    rich_content text,
    source_knowledge_ids text
);
CREATE TABLE ai_document_submission (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    writing_task_id character varying(32) NOT NULL,
    document_id character varying(32) NOT NULL,
    document_version integer NOT NULL,
    review_task_id character varying(32),
    target_system character varying(20) NOT NULL,
    business_id character varying(100),
    gate_result text NOT NULL,
    callback_job_id character varying(32),
    submitted_by character varying(32) NOT NULL,
    submitted_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_document_suggestion (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    document_id character varying(32) NOT NULL,
    base_paragraph_id character varying(32) NOT NULL,
    base_version integer NOT NULL,
    outline_node_id character varying(32),
    paragraph_order integer NOT NULL,
    base_plain_text text NOT NULL,
    base_rich_content text,
    proposed_plain_text text NOT NULL,
    proposed_rich_content text,
    adopted_generation_ids text,
    status character varying(10) DEFAULT '待处理'::character varying NOT NULL,
    proposer_id character varying(32) NOT NULL,
    decided_by character varying(32),
    decided_at timestamp with time zone,
    decision_note character varying(500),
    accepted_version integer,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_document_type_config (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    type_code character varying(20) NOT NULL,
    type_name character varying(50) NOT NULL,
    document_category character varying(10) NOT NULL,
    applicable_scenario text,
    direction character varying(10),
    required_fields text,
    recommended_structure text,
    forbidden_patterns text,
    closing_patterns text,
    current_version integer DEFAULT 1 NOT NULL,
    lifecycle_status character varying(10) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_document_type_config_version (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    config_id character varying(32) NOT NULL,
    version_number integer NOT NULL,
    full_config text NOT NULL,
    change_description text,
    test_results text,
    approved_by character varying(32),
    approved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_document_typeset (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    document_id character varying(32) NOT NULL,
    document_version integer NOT NULL,
    template_id character varying(32),
    template_name character varying(100) NOT NULL,
    field_values text,
    deviations text,
    deviation_count integer DEFAULT 0 NOT NULL,
    file_path character varying(500) NOT NULL,
    file_hash character varying(64) NOT NULL,
    file_size integer NOT NULL,
    source character varying(10) NOT NULL,
    parent_id character varying(32),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_document_version (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    document_id character varying(32) NOT NULL,
    version_number integer NOT NULL,
    version_type character varying(20) NOT NULL,
    change_source character varying(20),
    change_description text,
    content_storage_path character varying(500),
    word_count integer DEFAULT 0 NOT NULL,
    is_retained boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    is_protected boolean DEFAULT false NOT NULL,
    retention_marked_at timestamp with time zone,
    content_purged_at timestamp with time zone
);
CREATE TABLE ai_generation_record (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_id character varying(32) NOT NULL,
    document_id character varying(32),
    paragraph_id character varying(32),
    generation_type character varying(20) NOT NULL,
    prompt_template_id character varying(32) NOT NULL,
    prompt_template_version integer NOT NULL,
    model_config_id character varying(32) NOT NULL,
    model_version character varying(50),
    material_snapshot_version integer,
    input_context_hash character varying(64),
    output_text text,
    output_hash character varying(64),
    duration_ms integer,
    token_count_input integer,
    token_count_output integer,
    is_streaming boolean DEFAULT false NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_idempotency_record (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    user_id character varying(32) NOT NULL,
    endpoint character varying(500) NOT NULL,
    idempotency_key character varying(128) NOT NULL,
    request_hash character varying(64) NOT NULL,
    status character varying(20) NOT NULL,
    http_status integer,
    content_type character varying(200),
    response_body text,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone
);
CREATE TABLE ai_knowledge_chunk (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    document_id character varying(32) NOT NULL,
    chunk_order integer NOT NULL,
    chunk_text text NOT NULL,
    chunk_text_hash character varying(64),
    vector_id character varying(100),
    keywords text,
    start_position integer,
    end_position integer,
    page_number integer,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    embedding text
);
CREATE TABLE ai_knowledge_document (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    source_id character varying(32) NOT NULL,
    document_title character varying(500) NOT NULL,
    original_file_path character varying(500),
    original_file_hash character varying(64),
    parsed_status character varying(10) DEFAULT '待解析'::character varying NOT NULL,
    metadata text,
    authority_level character varying(20),
    effective_date date,
    expiry_date date,
    permission_level character varying(10),
    chunk_count integer DEFAULT 0 NOT NULL,
    document_version integer DEFAULT 1 NOT NULL,
    extracted_text text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    usage_type character varying(20) DEFAULT '待分类'::character varying NOT NULL,
    completeness character varying(20) DEFAULT '来源索引'::character varying NOT NULL,
    verification_status character varying(20) DEFAULT '待核验'::character varying NOT NULL,
    validity_status character varying(20) DEFAULT '待核验'::character varying NOT NULL,
    publisher character varying(200),
    document_number character varying(100),
    issued_date date,
    applicable_region character varying(100),
    applicable_audience character varying(200),
    applicable_scope character varying(500),
    reviewed_by character varying(32),
    reviewed_at timestamp with time zone
);
CREATE TABLE ai_knowledge_document_revision (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    document_id character varying(32) NOT NULL,
    document_version integer NOT NULL,
    snapshot_json text NOT NULL,
    captured_by character varying(32) NOT NULL,
    captured_at timestamp with time zone NOT NULL
);
CREATE TABLE ai_knowledge_source (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    source_code character varying(50) NOT NULL,
    source_name character varying(200) NOT NULL,
    category character varying(100) NOT NULL,
    storage_type character varying(20) NOT NULL,
    vector_model character varying(100),
    vector_dimensions integer,
    permission_scope text,
    entry_count integer DEFAULT 0 NOT NULL,
    last_sync_time timestamp with time zone,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_model_call_log (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_type character varying(20) NOT NULL,
    task_id character varying(32) NOT NULL,
    model_config_id character varying(32),
    prompt_template_id character varying(32),
    prompt_template_version integer,
    input_hash character varying(64),
    input_data_level character varying(10),
    input_summary character varying(500),
    output_hash character varying(64),
    token_count_input integer,
    token_count_output integer,
    duration_ms integer,
    cost_amount numeric(10,4),
    success boolean,
    error_message text,
    called_at timestamp with time zone NOT NULL,
    input_content text,
    output_content text
);
CREATE TABLE ai_model_config (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    config_code character varying(50) NOT NULL,
    config_name character varying(200) NOT NULL,
    provider character varying(50) NOT NULL,
    model_name character varying(100) NOT NULL,
    model_version character varying(50),
    scene character varying(50) NOT NULL,
    data_level character varying(10) DEFAULT '普通'::character varying NOT NULL,
    max_context_tokens integer,
    timeout_ms integer DEFAULT 60000 NOT NULL,
    max_retries integer DEFAULT 3 NOT NULL,
    cost_limit_daily numeric(10,2),
    sensitive_word_filter boolean DEFAULT true NOT NULL,
    output_validation boolean DEFAULT true NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    endpoint_url character varying(500),
    credential_ref character varying(100),
    input_cost_per_1k numeric(10,6),
    output_cost_per_1k numeric(10,6),
    qps_limit integer,
    daily_token_limit integer
);
CREATE TABLE ai_output_template (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    template_code character varying(50) NOT NULL,
    template_name character varying(200) NOT NULL,
    document_type_code character varying(20) NOT NULL,
    template_file_path character varying(500) NOT NULL,
    template_file_hash character varying(64),
    has_red_header boolean DEFAULT false NOT NULL,
    has_footer_mark boolean DEFAULT false NOT NULL,
    placeholder_map text NOT NULL,
    direction character varying(10),
    description text,
    current_version integer DEFAULT 1 NOT NULL,
    lifecycle_status character varying(10) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_output_template_version (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    template_id character varying(32) NOT NULL,
    version_number integer NOT NULL,
    template_file_path character varying(500) NOT NULL,
    template_file_hash character varying(64),
    change_note character varying(500),
    lifecycle_status character varying(10) NOT NULL,
    approved_by character varying(32),
    approved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_prompt_template (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    template_code character varying(50) NOT NULL,
    template_name character varying(200) NOT NULL,
    applicable_scene character varying(50) NOT NULL,
    document_type_code character varying(20),
    applicable_models text,
    system_role text,
    document_type_method text,
    input_fields text,
    material_citation_rule text,
    anti_fabrication_rule text,
    output_format text,
    risk_constraint text,
    model_parameters text,
    current_version integer DEFAULT 1 NOT NULL,
    lifecycle_status character varying(10) NOT NULL,
    test_sample_ids text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_prompt_template_version (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    template_id character varying(32) NOT NULL,
    version_number integer NOT NULL,
    system_role text,
    full_prompt text NOT NULL,
    change_description text,
    test_results text,
    approved_by character varying(32),
    approved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_review_dictionary (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    dict_code character varying(50) NOT NULL,
    dict_name character varying(200) NOT NULL,
    dict_type character varying(20) NOT NULL,
    source_description text,
    effective_date date,
    expiry_date date,
    current_version integer DEFAULT 1 NOT NULL,
    entry_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_dictionary_dates CHECK (((effective_date IS NULL) OR (expiry_date IS NULL) OR (expiry_date >= effective_date)))
);
CREATE TABLE ai_review_false_positive (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    issue_id character varying(32),
    rule_id character varying(32),
    original_text text NOT NULL,
    suggested_fix text,
    reason text NOT NULL,
    submitter_id character varying(32) NOT NULL,
    review_status character varying(10) DEFAULT '待审核'::character varying NOT NULL,
    reviewed_by character varying(32),
    optimization_suggestion text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_review_issue (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    review_task_id character varying(32) NOT NULL,
    issue_number character varying(20) NOT NULL,
    issue_category character varying(20) NOT NULL,
    risk_level character varying(10) NOT NULL,
    page_number integer,
    paragraph_id character varying(32),
    char_position_start integer,
    char_position_end integer,
    original_text text NOT NULL,
    suggested_fix text,
    issue_explanation text,
    rule_basis text,
    detection_method character varying(200) NOT NULL,
    confidence_score numeric(3,2),
    processing_status character varying(10) DEFAULT '待处理'::character varying NOT NULL,
    processing_note text,
    processed_by character varying(32),
    processed_at timestamp with time zone,
    merged_group_id character varying(32),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    paragraph_number integer
);
CREATE TABLE ai_review_issue_action (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    issue_id character varying(32) NOT NULL,
    action_type character varying(20) NOT NULL,
    action_detail text,
    operator_id character varying(32) NOT NULL,
    related_issue_ids text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_review_plan (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    plan_code character varying(50) NOT NULL,
    plan_name character varying(200) NOT NULL,
    applicable_scenarios text,
    review_levels text NOT NULL,
    rule_set_version character varying(20),
    knowledge_scope text,
    allow_external_model boolean DEFAULT false NOT NULL,
    risk_threshold character varying(10) DEFAULT '一般'::character varying NOT NULL,
    check_attachments boolean DEFAULT true NOT NULL,
    allow_auto_fix boolean DEFAULT false NOT NULL,
    generate_report boolean DEFAULT true NOT NULL,
    is_custom boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_review_report (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    review_task_id character varying(32) NOT NULL,
    report_version integer NOT NULL,
    total_issues integer DEFAULT 0 NOT NULL,
    severe_count integer DEFAULT 0 NOT NULL,
    important_count integer DEFAULT 0 NOT NULL,
    normal_count integer DEFAULT 0 NOT NULL,
    tip_count integer DEFAULT 0 NOT NULL,
    adopted_count integer DEFAULT 0 NOT NULL,
    ignored_count integer DEFAULT 0 NOT NULL,
    pending_count integer DEFAULT 0 NOT NULL,
    report_storage_path character varying(500),
    generated_at timestamp with time zone,
    is_final boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_review_rule (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    rule_code character varying(50) NOT NULL,
    rule_name character varying(200) NOT NULL,
    category character varying(20) NOT NULL,
    applicable_document_types text,
    risk_level character varying(10) NOT NULL,
    detection_method character varying(20) NOT NULL,
    rule_expression text,
    basis text,
    lifecycle_status character varying(10) NOT NULL,
    effective_date date,
    expiry_date date,
    current_version integer DEFAULT 1 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_rule_dates CHECK (((effective_date IS NULL) OR (expiry_date IS NULL) OR (expiry_date >= effective_date)))
);
CREATE TABLE ai_review_rule_version (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    rule_id character varying(32) NOT NULL,
    version_number integer NOT NULL,
    rule_expression text,
    change_description text,
    approved_by character varying(32),
    approved_at timestamp with time zone,
    test_results text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_review_task (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    source_system character varying(20) DEFAULT 'internal'::character varying NOT NULL,
    business_id character varying(100),
    document_id character varying(32),
    document_file_id character varying(100),
    document_type_code character varying(20),
    review_plan_id character varying(32),
    writing_task_id character varying(32),
    output_template_id character varying(32),
    status character varying(20) NOT NULL,
    initiator_id character varying(32) NOT NULL,
    callback_url character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    document_file_hash character varying(64),
    document_version integer,
    source_file_name character varying(255),
    edited_file_id character varying(512),
    edit_version integer DEFAULT 0,
    review_title character varying(200)
);
CREATE TABLE ai_review_term (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    dictionary_id character varying(32) NOT NULL,
    term_text character varying(500) NOT NULL,
    standard_form character varying(500),
    aliases text,
    category_tag character varying(50),
    risk_level character varying(10),
    source_reference text,
    effective_date date,
    expiry_date date,
    usage_note text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_term_dates CHECK (((effective_date IS NULL) OR (expiry_date IS NULL) OR (expiry_date >= effective_date)))
);
CREATE TABLE ai_submission_gate_config (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    require_review boolean DEFAULT true NOT NULL,
    require_latest_review boolean DEFAULT true NOT NULL,
    block_on_severe boolean DEFAULT true NOT NULL,
    require_important_processed boolean DEFAULT true NOT NULL,
    require_report boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    required_submit_role character varying(50)
);
CREATE TABLE ai_writing_idea (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_id character varying(32) NOT NULL,
    version integer NOT NULL,
    main_theme text,
    core_viewpoints text,
    writing_sequence text,
    must_express_facts text,
    optional_content text,
    missing_materials text,
    risk_warnings text,
    is_confirmed boolean DEFAULT false NOT NULL,
    confirmed_by character varying(32),
    confirmed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_writing_knowledge_selection (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_id character varying(32) NOT NULL,
    outline_node_id character varying(32) NOT NULL,
    source_id character varying(32) NOT NULL,
    knowledge_document_id character varying(32) NOT NULL,
    chunk_id character varying(32) NOT NULL,
    document_version integer NOT NULL,
    usage_type character varying(20) NOT NULL,
    document_title character varying(500) NOT NULL,
    excerpt text NOT NULL,
    file_hash character varying(64),
    selection_reason character varying(500) NOT NULL,
    selected_by character varying(32) NOT NULL,
    selected_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    completeness character varying(20) DEFAULT '原文摘录'::character varying NOT NULL
);
CREATE TABLE ai_writing_material (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_id character varying(32) NOT NULL,
    material_name character varying(500) NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id character varying(32),
    authority_level character varying(20) NOT NULL,
    usage_mode character varying(20) NOT NULL,
    citation_purpose character varying(20) NOT NULL,
    effective_date date,
    expiry_date date,
    permission_status character varying(10) DEFAULT '允许'::character varying NOT NULL,
    selected_fragment text,
    is_locked boolean DEFAULT false NOT NULL,
    file_hash character varying(64),
    file_path character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    outline_node_id character varying(32),
    CONSTRAINT ck_material_dates CHECK (((effective_date IS NULL) OR (expiry_date IS NULL) OR (expiry_date >= effective_date)))
);
CREATE TABLE ai_writing_material_snapshot (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_id character varying(32) NOT NULL,
    snapshot_version integer NOT NULL,
    material_id character varying(32) NOT NULL,
    material_version integer,
    file_hash character varying(64),
    selected_fragment text,
    knowledge_entry_version integer,
    retrieval_time timestamp with time zone,
    permission_check_result character varying(10),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_writing_outline (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_id character varying(32) NOT NULL,
    outline_version integer NOT NULL,
    is_confirmed boolean DEFAULT false NOT NULL,
    confirmed_by character varying(32),
    confirmed_at timestamp with time zone,
    structure_check_result text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_writing_outline_node (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    outline_id character varying(32) NOT NULL,
    parent_node_id character varying(32),
    node_order integer NOT NULL,
    node_title character varying(500) NOT NULL,
    writing_purpose text,
    key_facts text,
    referenced_material_ids text,
    target_word_count integer,
    is_required boolean DEFAULT true NOT NULL,
    generation_status character varying(10) DEFAULT '待生成'::character varying NOT NULL,
    human_note text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_writing_qa (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_id character varying(32) NOT NULL,
    question_text text NOT NULL,
    answer_text text NOT NULL,
    source_references text,
    answer_type character varying(20) NOT NULL,
    inserted boolean DEFAULT false NOT NULL,
    inserted_document_id character varying(32),
    inserted_paragraph_id character varying(32),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL
);
CREATE TABLE ai_writing_task (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    task_name character varying(200) NOT NULL,
    document_type_code character varying(20) NOT NULL,
    document_category character varying(20) NOT NULL,
    writing_topic character varying(500) NOT NULL,
    writing_purpose text,
    direction character varying(20),
    target_audience character varying(200),
    primary_recipient character varying(200),
    writing_tone character varying(20),
    expected_length_min integer,
    expected_length_max integer,
    target_date date,
    allow_external_knowledge boolean DEFAULT false NOT NULL,
    security_level character varying(10) DEFAULT '普通'::character varying NOT NULL,
    output_template_id character varying(32),
    status character varying(20) NOT NULL,
    outline_confirmed_version integer,
    current_document_id character varying(32),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by character varying(32) NOT NULL,
    updated_by character varying(32) NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    lock_version integer DEFAULT 0 NOT NULL,
    source_system character varying(20),
    business_id character varying(100),
    drafting_department character varying(100),
    callback_url character varying(500),
    CONSTRAINT ck_writing_length CHECK (((expected_length_min IS NULL) OR (expected_length_max IS NULL) OR (expected_length_max >= expected_length_min)))
);
CREATE TABLE official_review_pack_install (
    tenant_id character varying(32) NOT NULL,
    pack_code character varying(64) NOT NULL,
    pack_version character varying(32) NOT NULL,
    installed_at timestamp with time zone NOT NULL,
    installed_by character varying(32) NOT NULL
);
CREATE TABLE platform_organization (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    org_code character varying(64) NOT NULL,
    org_name character varying(200) NOT NULL,
    parent_id character varying(32),
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
CREATE TABLE platform_role (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    role_code character varying(64) NOT NULL,
    role_name character varying(100) NOT NULL,
    description character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
CREATE TABLE platform_session (
    token_hash character varying(64) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    user_id character varying(32) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE TABLE platform_tenant (
    id character varying(32) NOT NULL,
    tenant_code character varying(32) NOT NULL,
    tenant_name character varying(200) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
CREATE TABLE platform_user (
    id character varying(32) NOT NULL,
    tenant_id character varying(32) NOT NULL,
    username character varying(100) NOT NULL,
    display_name character varying(100) NOT NULL,
    org_id character varying(32),
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    password_hash character varying(100)
);
CREATE TABLE platform_user_role (
    tenant_id character varying(32) NOT NULL,
    user_id character varying(32) NOT NULL,
    role_id character varying(32) NOT NULL
);
CREATE TABLE public_source_pack_entry (
    tenant_id character varying(32) NOT NULL,
    entry_code character varying(50) NOT NULL,
    document_id character varying(32) NOT NULL,
    installed_version character varying(50) NOT NULL
);
CREATE TABLE public_source_pack_install (
    tenant_id character varying(32) NOT NULL,
    pack_code character varying(50) NOT NULL,
    pack_version character varying(50) NOT NULL,
    installed_at timestamp with time zone NOT NULL,
    installed_by character varying(32) NOT NULL
);
CREATE TABLE public_source_pack_source (
    tenant_id character varying(32) NOT NULL,
    source_code character varying(50) NOT NULL,
    source_id character varying(32) NOT NULL
);
ALTER TABLE ai_async_job_event
    ADD CONSTRAINT ai_async_job_event_pkey PRIMARY KEY (id);
ALTER TABLE ai_async_job
    ADD CONSTRAINT ai_async_job_pkey PRIMARY KEY (id);
ALTER TABLE ai_audit_log
    ADD CONSTRAINT ai_audit_log_pkey PRIMARY KEY (id);
ALTER TABLE ai_data_dict_item
    ADD CONSTRAINT ai_data_dict_item_pkey PRIMARY KEY (id);
ALTER TABLE ai_data_dict_type
    ADD CONSTRAINT ai_data_dict_type_pkey PRIMARY KEY (id);
ALTER TABLE ai_document_citation
    ADD CONSTRAINT ai_document_citation_pkey PRIMARY KEY (id);
ALTER TABLE ai_document_comment
    ADD CONSTRAINT ai_document_comment_pkey PRIMARY KEY (id);
ALTER TABLE ai_document_paragraph
    ADD CONSTRAINT ai_document_paragraph_pkey PRIMARY KEY (id);
ALTER TABLE ai_document
    ADD CONSTRAINT ai_document_pkey PRIMARY KEY (id);
ALTER TABLE ai_document_submission
    ADD CONSTRAINT ai_document_submission_pkey PRIMARY KEY (id);
ALTER TABLE ai_document_suggestion
    ADD CONSTRAINT ai_document_suggestion_pkey PRIMARY KEY (id);
ALTER TABLE ai_document_type_config
    ADD CONSTRAINT ai_document_type_config_pkey PRIMARY KEY (id);
ALTER TABLE ai_document_type_config_version
    ADD CONSTRAINT ai_document_type_config_version_pkey PRIMARY KEY (id);
ALTER TABLE ai_document_typeset
    ADD CONSTRAINT ai_document_typeset_pkey PRIMARY KEY (id);
ALTER TABLE ai_document_version
    ADD CONSTRAINT ai_document_version_pkey PRIMARY KEY (id);
ALTER TABLE ai_generation_record
    ADD CONSTRAINT ai_generation_record_pkey PRIMARY KEY (id);
ALTER TABLE ai_idempotency_record
    ADD CONSTRAINT ai_idempotency_record_pkey PRIMARY KEY (id);
ALTER TABLE ai_knowledge_chunk
    ADD CONSTRAINT ai_knowledge_chunk_pkey PRIMARY KEY (id);
ALTER TABLE ai_knowledge_document
    ADD CONSTRAINT ai_knowledge_document_pkey PRIMARY KEY (id);
ALTER TABLE ai_knowledge_document_revision
    ADD CONSTRAINT ai_knowledge_document_revision_pkey PRIMARY KEY (id);
ALTER TABLE ai_knowledge_source
    ADD CONSTRAINT ai_knowledge_source_pkey PRIMARY KEY (id);
ALTER TABLE ai_model_call_log
    ADD CONSTRAINT ai_model_call_log_pkey PRIMARY KEY (id);
ALTER TABLE ai_model_config
    ADD CONSTRAINT ai_model_config_pkey PRIMARY KEY (id);
ALTER TABLE ai_output_template
    ADD CONSTRAINT ai_output_template_pkey PRIMARY KEY (id);
ALTER TABLE ai_output_template_version
    ADD CONSTRAINT ai_output_template_version_pkey PRIMARY KEY (id);
ALTER TABLE ai_prompt_template
    ADD CONSTRAINT ai_prompt_template_pkey PRIMARY KEY (id);
ALTER TABLE ai_prompt_template_version
    ADD CONSTRAINT ai_prompt_template_version_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_dictionary
    ADD CONSTRAINT ai_review_dictionary_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_false_positive
    ADD CONSTRAINT ai_review_false_positive_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_issue_action
    ADD CONSTRAINT ai_review_issue_action_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_issue
    ADD CONSTRAINT ai_review_issue_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_plan
    ADD CONSTRAINT ai_review_plan_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_report
    ADD CONSTRAINT ai_review_report_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_rule
    ADD CONSTRAINT ai_review_rule_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_rule_version
    ADD CONSTRAINT ai_review_rule_version_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_task
    ADD CONSTRAINT ai_review_task_pkey PRIMARY KEY (id);
ALTER TABLE ai_review_term
    ADD CONSTRAINT ai_review_term_pkey PRIMARY KEY (id);
ALTER TABLE ai_submission_gate_config
    ADD CONSTRAINT ai_submission_gate_config_pkey PRIMARY KEY (id);
ALTER TABLE ai_writing_idea
    ADD CONSTRAINT ai_writing_idea_pkey PRIMARY KEY (id);
ALTER TABLE ai_writing_knowledge_selection
    ADD CONSTRAINT ai_writing_knowledge_selection_pkey PRIMARY KEY (id);
ALTER TABLE ai_writing_material
    ADD CONSTRAINT ai_writing_material_pkey PRIMARY KEY (id);
ALTER TABLE ai_writing_material_snapshot
    ADD CONSTRAINT ai_writing_material_snapshot_pkey PRIMARY KEY (id);
ALTER TABLE ai_writing_outline_node
    ADD CONSTRAINT ai_writing_outline_node_pkey PRIMARY KEY (id);
ALTER TABLE ai_writing_outline
    ADD CONSTRAINT ai_writing_outline_pkey PRIMARY KEY (id);
ALTER TABLE ai_writing_qa
    ADD CONSTRAINT ai_writing_qa_pkey PRIMARY KEY (id);
ALTER TABLE ai_writing_task
    ADD CONSTRAINT ai_writing_task_pkey PRIMARY KEY (id);
ALTER TABLE official_review_pack_install
    ADD CONSTRAINT official_review_pack_install_pkey PRIMARY KEY (tenant_id, pack_code, pack_version);
ALTER TABLE platform_organization
    ADD CONSTRAINT platform_organization_pkey PRIMARY KEY (id);
ALTER TABLE platform_role
    ADD CONSTRAINT platform_role_pkey PRIMARY KEY (id);
ALTER TABLE platform_session
    ADD CONSTRAINT platform_session_pkey PRIMARY KEY (token_hash);
ALTER TABLE platform_tenant
    ADD CONSTRAINT platform_tenant_pkey PRIMARY KEY (id);
ALTER TABLE platform_user
    ADD CONSTRAINT platform_user_pkey PRIMARY KEY (id);
ALTER TABLE platform_user_role
    ADD CONSTRAINT platform_user_role_pkey PRIMARY KEY (tenant_id, user_id, role_id);
ALTER TABLE public_source_pack_entry
    ADD CONSTRAINT public_source_pack_entry_pkey PRIMARY KEY (tenant_id, entry_code);
ALTER TABLE public_source_pack_install
    ADD CONSTRAINT public_source_pack_install_pkey PRIMARY KEY (tenant_id, pack_code, pack_version);
ALTER TABLE public_source_pack_source
    ADD CONSTRAINT public_source_pack_source_pkey PRIMARY KEY (tenant_id, source_code);
ALTER TABLE ai_async_job_event
    ADD CONSTRAINT uk_async_job_event UNIQUE (tenant_id, job_id, event_sequence);
ALTER TABLE ai_idempotency_record
    ADD CONSTRAINT uk_idempotency_scope UNIQUE (tenant_id, user_id, endpoint, idempotency_key);
ALTER TABLE ai_output_template
    ADD CONSTRAINT uk_output_template_code UNIQUE (tenant_id, template_code);
ALTER TABLE ai_output_template_version
    ADD CONSTRAINT uk_output_template_version UNIQUE (tenant_id, template_id, version_number);
ALTER TABLE ai_data_dict_item
    ADD CONSTRAINT uq_data_dict_item_code UNIQUE (tenant_id, type_code, item_code);
ALTER TABLE ai_data_dict_type
    ADD CONSTRAINT uq_data_dict_type_code UNIQUE (tenant_id, type_code);
ALTER TABLE ai_review_term
    ADD CONSTRAINT uq_dictionary_term UNIQUE (tenant_id, dictionary_id, term_text, is_deleted);
ALTER TABLE ai_document
    ADD CONSTRAINT uq_document_task UNIQUE (tenant_id, task_id);
ALTER TABLE ai_document_type_config
    ADD CONSTRAINT uq_document_type_config_code UNIQUE (tenant_id, type_code, is_deleted);
ALTER TABLE ai_document_type_config_version
    ADD CONSTRAINT uq_document_type_config_version UNIQUE (tenant_id, config_id, version_number);
ALTER TABLE ai_document_version
    ADD CONSTRAINT uq_document_version UNIQUE (tenant_id, document_id, version_number);
ALTER TABLE ai_knowledge_chunk
    ADD CONSTRAINT uq_knowledge_chunk_order UNIQUE (tenant_id, document_id, chunk_order);
ALTER TABLE ai_knowledge_document_revision
    ADD CONSTRAINT uq_knowledge_revision UNIQUE (tenant_id, document_id, document_version);
ALTER TABLE ai_knowledge_source
    ADD CONSTRAINT uq_knowledge_source_code UNIQUE (tenant_id, source_code);
ALTER TABLE ai_writing_material_snapshot
    ADD CONSTRAINT uq_material_snapshot UNIQUE (tenant_id, task_id, snapshot_version, material_id);
ALTER TABLE ai_model_config
    ADD CONSTRAINT uq_model_config_code UNIQUE (tenant_id, config_code, is_deleted);
ALTER TABLE ai_writing_outline
    ADD CONSTRAINT uq_outline_version UNIQUE (tenant_id, task_id, outline_version);
ALTER TABLE platform_organization
    ADD CONSTRAINT uq_platform_org_code UNIQUE (tenant_id, org_code);
ALTER TABLE platform_role
    ADD CONSTRAINT uq_platform_role_code UNIQUE (tenant_id, role_code);
ALTER TABLE platform_tenant
    ADD CONSTRAINT uq_platform_tenant_code UNIQUE (tenant_code);
ALTER TABLE platform_user
    ADD CONSTRAINT uq_platform_username UNIQUE (tenant_id, username);
ALTER TABLE ai_prompt_template
    ADD CONSTRAINT uq_prompt_template_code UNIQUE (tenant_id, template_code, is_deleted);
ALTER TABLE ai_prompt_template_version
    ADD CONSTRAINT uq_prompt_template_version UNIQUE (tenant_id, template_id, version_number);
ALTER TABLE ai_review_issue
    ADD CONSTRAINT uq_review_issue_number UNIQUE (tenant_id, review_task_id, issue_number);
ALTER TABLE ai_review_plan
    ADD CONSTRAINT uq_review_plan_code UNIQUE (tenant_id, plan_code);
ALTER TABLE ai_review_report
    ADD CONSTRAINT uq_review_report_version UNIQUE (tenant_id, review_task_id, report_version);
ALTER TABLE ai_review_rule_version
    ADD CONSTRAINT uq_rule_version UNIQUE (tenant_id, rule_id, version_number);
ALTER TABLE ai_submission_gate_config
    ADD CONSTRAINT uq_submission_gate_tenant UNIQUE (tenant_id);
ALTER TABLE ai_writing_idea
    ADD CONSTRAINT uq_writing_idea_version UNIQUE (tenant_id, task_id, version);
CREATE INDEX idx_async_job_event_replay ON ai_async_job_event (tenant_id, job_id, event_sequence);
CREATE INDEX idx_async_job_queue ON ai_async_job (status, next_retry_at, created_at);
CREATE INDEX idx_async_job_tenant_created ON ai_async_job (tenant_id, created_at);
CREATE INDEX idx_audit_tenant_time ON ai_audit_log (tenant_id, occurred_at);
CREATE INDEX idx_audit_type_time ON ai_audit_log (tenant_id, audit_type, occurred_at);
CREATE INDEX idx_citation_paragraph ON ai_document_citation (tenant_id, document_id, paragraph_id);
CREATE INDEX idx_comment_document_resolved ON ai_document_comment (tenant_id, document_id, is_resolved);
CREATE INDEX idx_comment_document_user ON ai_document_comment (tenant_id, document_id, commenter_id);
CREATE INDEX idx_data_dict_item_type ON ai_data_dict_item (tenant_id, type_code, sort_order);
CREATE INDEX idx_data_dict_type_tenant ON ai_data_dict_type (tenant_id, sort_order);
CREATE INDEX idx_dictionary_code ON ai_review_dictionary (tenant_id, dict_code, is_deleted);
CREATE INDEX idx_dictionary_type ON ai_review_dictionary (tenant_id, dict_type);
CREATE INDEX idx_document_security ON ai_document (tenant_id, security_level);
CREATE INDEX idx_document_submission_task ON ai_document_submission (tenant_id, writing_task_id, submitted_at);
CREATE INDEX idx_document_type_config_filters ON ai_document_type_config (tenant_id, document_category, direction, lifecycle_status);
CREATE INDEX idx_document_version_retention ON ai_document_version (tenant_id, is_retained, retention_marked_at);
CREATE INDEX idx_false_positive_status ON ai_review_false_positive (tenant_id, review_status, created_at);
CREATE INDEX idx_generation_document ON ai_generation_record (tenant_id, document_id, created_at);
CREATE INDEX idx_generation_task_type ON ai_generation_record (tenant_id, task_id, generation_type);
CREATE INDEX idx_idempotency_expiry ON ai_idempotency_record (expires_at);
CREATE INDEX idx_knowledge_chunk_document ON ai_knowledge_chunk (tenant_id, document_id, chunk_order);
CREATE INDEX idx_knowledge_document_effective ON ai_knowledge_document (tenant_id, source_id, effective_date, expiry_date);
CREATE INDEX idx_knowledge_document_status ON ai_knowledge_document (tenant_id, source_id, parsed_status);
CREATE INDEX idx_knowledge_source_category ON ai_knowledge_source (tenant_id, category);
CREATE INDEX idx_material_snapshot_task ON ai_writing_material_snapshot (tenant_id, task_id, snapshot_version);
CREATE INDEX idx_material_task_node ON ai_writing_material (tenant_id, task_id, outline_node_id);
CREATE INDEX idx_material_task_source ON ai_writing_material (tenant_id, task_id, source_type);
CREATE INDEX idx_model_call_task ON ai_model_call_log (tenant_id, task_type, task_id);
CREATE INDEX idx_model_call_time ON ai_model_call_log (tenant_id, called_at);
CREATE INDEX idx_model_config_provider ON ai_model_config (tenant_id, provider);
CREATE INDEX idx_model_config_scene ON ai_model_config (tenant_id, scene, is_active);
CREATE INDEX idx_outline_node_order ON ai_writing_outline_node (tenant_id, outline_id, node_order);
CREATE INDEX idx_output_template_status ON ai_output_template (tenant_id, lifecycle_status);
CREATE INDEX idx_output_template_type ON ai_output_template (tenant_id, document_type_code);
CREATE INDEX idx_output_template_version ON ai_output_template_version (tenant_id, template_id, version_number);
CREATE INDEX idx_paragraph_order ON ai_document_paragraph (tenant_id, document_id, version_number, paragraph_order);
CREATE INDEX idx_platform_session_user ON platform_session (tenant_id, user_id);
CREATE INDEX idx_platform_user_org ON platform_user (tenant_id, org_id);
CREATE INDEX idx_platform_user_role_user ON platform_user_role (tenant_id, user_id);
CREATE INDEX idx_prompt_template_scene ON ai_prompt_template (tenant_id, applicable_scene, document_type_code, lifecycle_status);
CREATE INDEX idx_review_issue_filters ON ai_review_issue (tenant_id, review_task_id, issue_category, risk_level, processing_status);
CREATE INDEX idx_review_issue_locator ON ai_review_issue (tenant_id, review_task_id, page_number, paragraph_number);
CREATE INDEX idx_review_plan_tenant ON ai_review_plan (tenant_id, is_custom);
CREATE INDEX idx_review_report_task ON ai_review_report (tenant_id, review_task_id, report_version);
CREATE INDEX idx_review_task_document ON ai_review_task (tenant_id, document_id);
CREATE INDEX idx_review_task_status ON ai_review_task (tenant_id, status);
CREATE INDEX idx_rule_category_status ON ai_review_rule (tenant_id, category, lifecycle_status);
CREATE INDEX idx_rule_code ON ai_review_rule (tenant_id, rule_code, is_deleted);
CREATE INDEX idx_suggestion_document_status ON ai_document_suggestion (tenant_id, document_id, status);
CREATE INDEX idx_term_category ON ai_review_term (tenant_id, dictionary_id, category_tag);
CREATE INDEX idx_typeset_document ON ai_document_typeset (tenant_id, document_id, created_at);
CREATE INDEX idx_writing_idea_task ON ai_writing_idea (tenant_id, task_id, version);
CREATE INDEX idx_writing_knowledge_selection_task ON ai_writing_knowledge_selection (tenant_id, task_id, outline_node_id);
CREATE INDEX idx_writing_qa_task ON ai_writing_qa (tenant_id, task_id, created_at);
CREATE INDEX idx_writing_task_business ON ai_writing_task (tenant_id, source_system, business_id);
CREATE INDEX idx_writing_task_tenant_status ON ai_writing_task (tenant_id, status);
CREATE INDEX idx_writing_task_tenant_type ON ai_writing_task (tenant_id, document_type_code);
ALTER TABLE ai_async_job_event
    ADD CONSTRAINT fk_async_job_event_job FOREIGN KEY (job_id) REFERENCES ai_async_job(id);
ALTER TABLE ai_document_citation
    ADD CONSTRAINT fk_citation_document FOREIGN KEY (document_id) REFERENCES ai_document(id);
ALTER TABLE ai_document_citation
    ADD CONSTRAINT fk_citation_material FOREIGN KEY (material_id) REFERENCES ai_writing_material(id);
ALTER TABLE ai_document_citation
    ADD CONSTRAINT fk_citation_paragraph FOREIGN KEY (paragraph_id) REFERENCES ai_document_paragraph(id);
ALTER TABLE ai_document_comment
    ADD CONSTRAINT fk_comment_document FOREIGN KEY (document_id) REFERENCES ai_document(id);
ALTER TABLE ai_document_comment
    ADD CONSTRAINT fk_comment_paragraph FOREIGN KEY (paragraph_id) REFERENCES ai_document_paragraph(id);
ALTER TABLE ai_document
    ADD CONSTRAINT fk_document_task FOREIGN KEY (task_id) REFERENCES ai_writing_task(id);
ALTER TABLE ai_document_type_config_version
    ADD CONSTRAINT fk_document_type_config_version FOREIGN KEY (config_id) REFERENCES ai_document_type_config(id);
ALTER TABLE ai_document_version
    ADD CONSTRAINT fk_document_version_document FOREIGN KEY (document_id) REFERENCES ai_document(id);
ALTER TABLE ai_review_false_positive
    ADD CONSTRAINT fk_false_positive_issue FOREIGN KEY (issue_id) REFERENCES ai_review_issue(id);
ALTER TABLE ai_review_false_positive
    ADD CONSTRAINT fk_false_positive_rule FOREIGN KEY (rule_id) REFERENCES ai_review_rule(id);
ALTER TABLE ai_generation_record
    ADD CONSTRAINT fk_generation_document FOREIGN KEY (document_id) REFERENCES ai_document(id);
ALTER TABLE ai_generation_record
    ADD CONSTRAINT fk_generation_task FOREIGN KEY (task_id) REFERENCES ai_writing_task(id);
ALTER TABLE ai_review_issue_action
    ADD CONSTRAINT fk_issue_action_issue FOREIGN KEY (issue_id) REFERENCES ai_review_issue(id);
ALTER TABLE ai_knowledge_chunk
    ADD CONSTRAINT fk_knowledge_chunk_document FOREIGN KEY (document_id) REFERENCES ai_knowledge_document(id);
ALTER TABLE ai_knowledge_document
    ADD CONSTRAINT fk_knowledge_document_source FOREIGN KEY (source_id) REFERENCES ai_knowledge_source(id);
ALTER TABLE ai_writing_material_snapshot
    ADD CONSTRAINT fk_material_snapshot_material FOREIGN KEY (material_id) REFERENCES ai_writing_material(id);
ALTER TABLE ai_writing_material_snapshot
    ADD CONSTRAINT fk_material_snapshot_task FOREIGN KEY (task_id) REFERENCES ai_writing_task(id);
ALTER TABLE ai_writing_material
    ADD CONSTRAINT fk_material_task FOREIGN KEY (task_id) REFERENCES ai_writing_task(id);
ALTER TABLE ai_model_call_log
    ADD CONSTRAINT fk_model_call_config FOREIGN KEY (model_config_id) REFERENCES ai_model_config(id);
ALTER TABLE ai_writing_outline_node
    ADD CONSTRAINT fk_outline_node_outline FOREIGN KEY (outline_id) REFERENCES ai_writing_outline(id);
ALTER TABLE ai_writing_outline_node
    ADD CONSTRAINT fk_outline_node_parent FOREIGN KEY (parent_node_id) REFERENCES ai_writing_outline_node(id);
ALTER TABLE ai_writing_outline
    ADD CONSTRAINT fk_outline_task FOREIGN KEY (task_id) REFERENCES ai_writing_task(id);
ALTER TABLE ai_output_template_version
    ADD CONSTRAINT fk_output_template_version_template FOREIGN KEY (template_id) REFERENCES ai_output_template(id);
ALTER TABLE ai_document_paragraph
    ADD CONSTRAINT fk_paragraph_document FOREIGN KEY (document_id) REFERENCES ai_document(id);
ALTER TABLE ai_document_paragraph
    ADD CONSTRAINT fk_paragraph_outline_node FOREIGN KEY (outline_node_id) REFERENCES ai_writing_outline_node(id);
ALTER TABLE ai_prompt_template_version
    ADD CONSTRAINT fk_prompt_template_version FOREIGN KEY (template_id) REFERENCES ai_prompt_template(id);
ALTER TABLE ai_review_task
    ADD CONSTRAINT fk_review_document FOREIGN KEY (document_id) REFERENCES ai_document(id);
ALTER TABLE ai_review_issue
    ADD CONSTRAINT fk_review_issue_paragraph FOREIGN KEY (paragraph_id) REFERENCES ai_document_paragraph(id);
ALTER TABLE ai_review_issue
    ADD CONSTRAINT fk_review_issue_task FOREIGN KEY (review_task_id) REFERENCES ai_review_task(id);
ALTER TABLE ai_review_report
    ADD CONSTRAINT fk_review_report_task FOREIGN KEY (review_task_id) REFERENCES ai_review_task(id);
ALTER TABLE ai_review_task
    ADD CONSTRAINT fk_review_writing_task FOREIGN KEY (writing_task_id) REFERENCES ai_writing_task(id);
ALTER TABLE ai_review_rule_version
    ADD CONSTRAINT fk_rule_version_rule FOREIGN KEY (rule_id) REFERENCES ai_review_rule(id);
ALTER TABLE ai_document_suggestion
    ADD CONSTRAINT fk_suggestion_document FOREIGN KEY (document_id) REFERENCES ai_document(id);
ALTER TABLE ai_review_term
    ADD CONSTRAINT fk_term_dictionary FOREIGN KEY (dictionary_id) REFERENCES ai_review_dictionary(id);
ALTER TABLE ai_document_typeset
    ADD CONSTRAINT fk_typeset_document FOREIGN KEY (document_id) REFERENCES ai_document(id);
ALTER TABLE ai_writing_idea
    ADD CONSTRAINT fk_writing_idea_task FOREIGN KEY (task_id) REFERENCES ai_writing_task(id);
ALTER TABLE ai_writing_qa
    ADD CONSTRAINT fk_writing_qa_document FOREIGN KEY (inserted_document_id) REFERENCES ai_document(id);
ALTER TABLE ai_writing_qa
    ADD CONSTRAINT fk_writing_qa_paragraph FOREIGN KEY (inserted_paragraph_id) REFERENCES ai_document_paragraph(id);
ALTER TABLE ai_writing_qa
    ADD CONSTRAINT fk_writing_qa_task FOREIGN KEY (task_id) REFERENCES ai_writing_task(id);
INSERT INTO platform_tenant (id, tenant_code, tenant_name, enabled, created_at, updated_at) VALUES ('00000000000000000000000000000010', 'local-government', '本地机关', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO platform_organization (id, tenant_id, org_code, org_name, parent_id, enabled, created_at, updated_at) VALUES ('00000000000000000000000000000001', 'local-government', 'general-office', '综合办公处', NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO platform_role (id, tenant_id, role_code, role_name, description, enabled, created_at, updated_at) VALUES ('00000000000000000000000000000002', 'local-government', 'platform.admin', '管理员', '管理平台配置和身份目录', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO platform_role (id, tenant_id, role_code, role_name, description, enabled, created_at, updated_at) VALUES ('00000000000000000000000000000003', 'local-government', 'platform.reviewer', '核稿人员', '确认文稿和审校结果', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO platform_role (id, tenant_id, role_code, role_name, description, enabled, created_at, updated_at) VALUES ('00000000000000000000000000000004', 'local-government', 'platform.user', '普通用户', '使用写作和审校功能', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO platform_user (id, tenant_id, username, display_name, org_id, enabled, password_hash, created_at, updated_at) VALUES ('00000000000000000000000000000005', 'local-government', 'admin', '本地管理员', '00000000000000000000000000000001', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO platform_user_role (tenant_id, user_id, role_id) VALUES ('local-government', '00000000000000000000000000000005', '00000000000000000000000000000002');
