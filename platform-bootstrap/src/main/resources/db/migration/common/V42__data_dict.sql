CREATE TABLE ai_data_dict_type (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    type_code VARCHAR(50) NOT NULL,
    type_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_data_dict_type_code UNIQUE (tenant_id, type_code)
);
CREATE INDEX idx_data_dict_type_tenant ON ai_data_dict_type(tenant_id, sort_order);

CREATE TABLE ai_data_dict_item (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    type_code VARCHAR(50) NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    item_label VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    lock_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_data_dict_item_code UNIQUE (tenant_id, type_code, item_code)
);
CREATE INDEX idx_data_dict_item_type ON ai_data_dict_item(tenant_id, type_code, sort_order);
