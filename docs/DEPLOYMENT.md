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

## 可选：定稿稿在线编辑（ONLYOFFICE Docs）

写作工作区“套红排版”生成的定稿稿默认可下载 DOCX。需要在浏览器中直接微调版式时，可启用 ONLYOFFICE 在线编辑，默认关闭。

启用前确认：

- **许可**：社区版为 AGPL v3，限制同时编辑连接数，超出需购买企业版；平台只通过接口集成，不修改其源码。
- **信创**：ONLYOFFICE 为境外厂商产品，项目有信创要求时先确认是否允许使用；适配层与业务解耦，可改接 WPS Web Office。
- **字体**：仿宋_GB2312、方正小标宋简体等公文字体须取得授权后放入 `deploy/onlyoffice/fonts/`，否则编辑器以替代字体显示。

启用步骤：

1. 在 `.env` 中设置 `ONLYOFFICE_JWT_SECRET`（至少 32 字节的随机值，平台与文档服务器共用），可选 `ONLYOFFICE_VERSION`（默认 9.4.0，上线前按安全公告更新）。
2. 叠加启动：`docker compose -f compose.yaml -f deploy/onlyoffice/compose.onlyoffice.yaml --env-file .env up -d`。
3. 叠加文件会：启动 `onlyoffice` 服务；为后端开启 `PLATFORM_ONLYOFFICE_*` 配置；用 `deploy/onlyoffice/nginx.conf` 替换前端 nginx 配置，在 `/office/` 下同源代理文档服务器，因此无需放宽平台的内容安全策略。修改 `platform-web/nginx.conf` 时请同步该文件。

安全约束（已实现）：

- 编辑器配置由后端用共享密钥签名；文档服务器拉取文件、保存回调这两个入口不经 OIDC 登录，凭平台签发、24 小时有效、绑定租户与定稿稿的令牌访问，回调内容还须带有文档服务器的签名；
- 保存时只从配置的文档服务器地址下载文件（浏览器地址改写为内部地址），校验为安全的 DOCX 后追加为新的定稿稿，不覆盖原稿，也不回写正文段落；
- 正文更新后旧定稿稿标记为需重新套红，不能在线编辑。

## 仍需目标环境确认

- 双应用实例、数据库主从/WAL 异地归档及实际 RTO≤4 小时、RPO≤5 分钟；
- WAF、TLS 证书、DNS、统一身份源及组织角色映射；
- 真实模型端点、GPU 容量、模型熔断切换；
- 500 用户容量、20 路审校、10 路文档解析及 100 QPS 检索压测；
- 监控告警、日志集中存储、季度灾备演练和变更审批。
