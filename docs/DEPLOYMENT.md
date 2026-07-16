# 生产部署与上线门禁

`compose.yaml` 是单机生产参考拓扑：PostgreSQL、Redis、MinIO、独立 PostgreSQL 支撑的 Keycloak、Java 17 后端和 Nginx 前端。生产环境仍应由外部负载均衡/WAF 提供 TLS，并将 PostgreSQL、对象存储和备份迁移到独立故障域。

## 上线步骤

1. 从 `.env.example` 复制出不入库的 `.env`，替换所有占位符。OIDC 公网地址、`PLATFORM_PUBLIC_URL` 和前端允许源必须是 HTTPS。
2. 执行 `ENV_FILE=.env bash deploy/ops/preflight.sh`。脚本检查占位符、密码长度与复用、HTTPS、浮动镜像标签和 Compose 解析。
3. 执行 `ENV_FILE=.env bash deploy/ops/deploy.sh`。脚本构建镜像、启动服务、等待 readiness，并在 `target/release-evidence/` 保存解析后的拓扑、镜像和构建号。
4. 执行 `BACKUP_ROOT=/independent/storage bash deploy/ops/backup.sh`，再在隔离恢复环境用 `restore.sh` 验证。未经实际恢复演练，不得把“备份文件存在”视为灾备达标。
5. 使用生产等价 PostgreSQL、Redis、MinIO、检索服务和批准模型执行浏览器、安全与性能验收，冻结代码构建号、规则、提示词、模型和测试集版本。

## 已强制的生产安全项

- 后端关闭本地身份请求头，必须验证 OIDC JWT；
- Swagger/OpenAPI 在 `prod` 配置中关闭；
- readiness/liveness 可匿名探测，但健康详情不外泄；
- MinIO 应用凭据与 root 管理凭据分离；
- Keycloak 禁止 `start-dev`，使用独立 PostgreSQL；
- realm 模板不包含用户或口令，预检按生产前端 origin 生成精确回调地址；首个管理员必须通过组织身份源或上线审批流程创建；
- 后端和前端容器只读运行，启用 `no-new-privileges`；
- Nginx 禁止 SSE 缓冲并设置 CSP、点击劫持、MIME、Referrer 和 Permissions Policy 响应头。

## 仍需目标环境确认

- 双应用实例、数据库主从/WAL 异地归档及实际 RTO≤4 小时、RPO≤5 分钟；
- WAF、TLS 证书、DNS、统一身份源及组织角色映射；
- 真实模型端点、GPU 容量、模型熔断切换；
- 500 用户容量、20 路审校、10 路文档解析及 100 QPS 检索压测；
- 监控告警、日志集中存储、季度灾备演练和变更审批。
