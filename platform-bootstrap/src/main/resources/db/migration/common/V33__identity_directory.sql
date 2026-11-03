CREATE TABLE platform_organization (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    org_code VARCHAR(64) NOT NULL,
    org_name VARCHAR(200) NOT NULL,
    parent_id VARCHAR(32),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_platform_org_code UNIQUE (tenant_id, org_code)
);

CREATE TABLE platform_role (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_platform_role_code UNIQUE (tenant_id, role_code)
);

CREATE TABLE platform_user (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    org_id VARCHAR(32),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_platform_username UNIQUE (tenant_id, username)
);

CREATE TABLE platform_user_role (
    tenant_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    role_id VARCHAR(32) NOT NULL,
    PRIMARY KEY (tenant_id, user_id, role_id)
);
CREATE INDEX idx_platform_user_org ON platform_user (tenant_id, org_id);
CREATE INDEX idx_platform_user_role_user ON platform_user_role (tenant_id, user_id);

-- 本地开发环境的初始身份；生产环境仍由 OIDC 令牌决定访问权限。
INSERT INTO platform_organization (id,tenant_id,org_code,org_name,parent_id,enabled,created_at,updated_at)
VALUES ('00000000000000000000000000000001','local-government','general-office','综合办公处',NULL,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO platform_role (id,tenant_id,role_code,role_name,description,enabled,created_at,updated_at)
VALUES ('00000000000000000000000000000002','local-government','platform.admin','管理员','管理平台配置和身份目录',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO platform_role (id,tenant_id,role_code,role_name,description,enabled,created_at,updated_at)
VALUES ('00000000000000000000000000000003','local-government','platform.reviewer','核稿人员','确认文稿和审校结果',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO platform_role (id,tenant_id,role_code,role_name,description,enabled,created_at,updated_at)
VALUES ('00000000000000000000000000000004','local-government','platform.user','普通用户','使用写作和审校功能',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO platform_user (id,tenant_id,username,display_name,org_id,enabled,created_at,updated_at)
VALUES ('00000000000000000000000000000005','local-government','local-user','本地管理员','00000000000000000000000000000001',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO platform_user_role (tenant_id,user_id,role_id)
VALUES ('local-government','00000000000000000000000000000005','00000000000000000000000000000002');
