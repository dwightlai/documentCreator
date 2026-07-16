# 智能公文写作与政务智能审校平台

本仓库依据《详细设计文档 V1.6》实现，采用 Java 17、Spring Boot、MyBatis-Plus 与 Vue 3、TypeScript、Vite。当前代码以模块化单体方式组织；可变业务表包含租户、审计、逻辑删除和乐观锁字段，调用日志、审计日志、任务事件等追加型表按设计豁免逻辑删除和乐观锁。

## 已贯通的业务链路

- 写作任务创建、编辑、状态与多租户分页查询；
- 材料清单管理、DOCX/PDF/TXT 上传与文本提取、租户隔离原文件存储和 SHA-256、有效期和权限校验，以及可复用的材料版本快照；
- 文种推荐、大纲生成、结构检查和人工确认门禁；
- 写作思路生成、逐项编辑与版本确认，含结构化标题候选和风险提示；
- 按大纲节点生成正文、段落编辑/锁定、不可变版本与恢复；
- 文稿批注、批注解决及任意两个文稿版本的段落级差异比较；
- 正文续写、定向改写、压缩、扩写，以及输入/输出 SHA-256 生成审计记录；
- DOCX、HTML、PDF 导出；DOCX 包含 AI 辅助及文稿版本自定义属性，PDF 含版本页脚与页码；
- DOCX 输出模板安全检查、占位符解析、必填校验与模拟数据预览渲染，阻止宏、OLE、外链和异常 ZIP；
- 输出模板文件持久化、版本记录、测试后发布，以及页面/边距/字体/字号/行距/缩进/页眉页脚/占位符等十四维 L4 偏差比对；导入 DOCX 的偏差会写入审校任务；
- L1/L2 内置规则审校，以及 L3 报告夹带请示、请示一文一事/结束语、占位符残留检查；审校方案按层级与风险阈值运行时过滤；
- 四类内置及单位定制审校方案、页码/逻辑段落/字符区间定位、版本化审校报告与 PDF/DOCX 导出；
- 修改完成、转人工、低风险批量忽略和误报提交/管理员审核闭环；
- 审校问题批量采纳、人工确认后沉淀词库，以及基于当前文稿重新审校；
- 规则草稿、测试、审核、发布、停用及版本审批记录；
- 生效规则按租户、文种和有效期加载到审校引擎，标准词库与别名同步参与检测；
- 文种业务配置支持版本化测试、审核、发布和停用；平台内置 11 类常用法定公文与 12 类事务文书的只读默认配置，租户可用同编码配置覆盖，生效的推荐结构会直接驱动提纲生成；
- 提示词模板支持按场景、文种和模型匹配，完整提示词以兼容 CLOB 保存并经过测试、审核、发布后生效；
- 词库/词条维护和 UTF-8 CSV 批量导入；
- 知识库文档上传、DOCX/PDF/文本提取、切片哈希、权限前置过滤与来源化全文检索；
- 知识原文件和输出模板按租户隔离持久化，开发环境使用本地目录、生产环境使用 MinIO，数据库只保存对象键、哈希与提取文本；
- 基于权限知识范围的写作问答、来源快照，以及二次确认后插入正文并生成新版本；
- 模型配置、OpenAI-compatible 实际调用、按场景和数据级别路由、涉密拒绝、云端调用前脱敏、端点白名单、环境变量密钥引用、Token/费用额度及不可还原的追加型调用日志；正文生成会在存在生效端点和提示词时使用实际模型，否则保持可测试的规则降级；
- 全平台写操作审计日志，仅记录操作元数据，默认不记录请求正文和可还原模型内容；
- 写操作 `Idempotency-Key` 防重放：按租户、用户和接口隔离，响应以兼容 CLOB 保存至少 24 小时；
- 持久化异步任务、自动退避重试、死信与人工补偿，SSE 事件支持 `Last-Event-ID` 断点回放；
- 正文分段生成支持有界队列 SSE `start/delta/completed/error` 事件，前端可边接收边展示，最终以持久化文稿版本为准；
- 知识文档异步解析，以及任务运行中心的进度、事件和补偿操作；
- OA 审校接入、业务编号/外部文件关联、回调白名单与 HMAC-SHA256 签名；回调失败复用异步任务退避和死信机制；
- 审校建议采纳后自动生成文稿新版本；
- Vue 政务写作、材料权限快照、提纲编辑确认、正文段落编辑与版本保存的三栏工作区，以及审校问题处置、规则词库、输出模板、模型/提示词/文种/审计治理工作台。

## 本地开发

要求：JDK 17+、Maven 3.9+、Node.js 20.19+、pnpm 11+。

```powershell
.\mvnw.cmd -pl platform-bootstrap -am test
.\mvnw.cmd -pl platform-bootstrap -am package -DskipTests
java -jar platform-bootstrap/target/platform-bootstrap-0.1.0-SNAPSHOT.jar
```

本地后端默认使用 `./data/platform` 下的 H2 文件数据库，并允许通过 `X-Tenant-Id`、`X-User-Id` 请求头模拟身份。该模式只能用于开发。

```powershell
Set-Location platform-web
pnpm install
pnpm test
pnpm dev
```

前端地址为 `http://localhost:5175`，Vite 会将 `/api` 代理到 `http://localhost:18089`。OpenAPI 地址为 `http://localhost:18089/swagger-ui.html`。

后端启动后，可为前端默认租户初始化一套可重复执行的本地演示数据：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/ops/seed-local-demo.ps1
```

脚本使用 `local-government / local-user` 身份，初始化不同阶段的写作任务、审校任务、规则、词库、知识库文档、提示词模板、停用的模型配置，以及内置文种和审校方案。模型配置保持停用，不会误调用外部服务；重复运行不会复制相同样例。初始化完成后刷新 `http://localhost:5175` 即可。

## 容器部署基线

```powershell
Copy-Item .env.example .env
# 修改 .env 中的所有密码
docker compose build
docker compose up -d
```

默认入口为 `http://localhost:8088`。编排包含 PostgreSQL、Redis、MinIO、OIDC、后端与 Nginx 前端，并配置持久卷及健康检查。首次登录的临时管理员为 `platform-admin`；其临时密码仅用于首次启动，登录后必须更换。

生产上线前必须完成：替换演示 OIDC 配置、接入单位统一身份源、配置 HTTPS 与可信证书、将密码迁入密钥管理系统、执行备份恢复演练、完成等保与漏洞扫描，并依据验收数据集执行全量回归。

CI 工作流位于 `.github/workflows/ci.yml`，包含 Java 17 后端全量测试、PostgreSQL 16 真实迁移与逻辑 CLOB 兼容测试、前端类型/单测/构建、双镜像构建、高危漏洞/密钥/IaC 扫描和 CycloneDX SBOM 归档。备份、恢复和上线检查见 `docs/OPERATIONS_RUNBOOK.md`。

OA 回调仅允许 `OA_CALLBACK_ALLOWLIST` 中预登记的 HTTPS 源与路径，签名密钥由 `OA_SHARED_SECRET` 注入，不能写入源码或普通配置文件。回调体只包含业务编号、审校任务状态和状态地址，不包含文稿正文。

## 数据库兼容策略

设计文档中的结构化数组、规则配置、版本快照和大段文本统一按逻辑 `CLOB` 处理，不使用 PostgreSQL `JSONB`，也不依赖数据库 JSON 运算符。PostgreSQL/H2 物理映射为 `TEXT`，MySQL 映射为 `LONGTEXT`，Oracle/达梦映射为原生 `CLOB`；Java 侧由 `JsonClobTypeHandler` 负责 JSON 交换编码的序列化与反序列化。

## 工程结构

- `platform-common`：统一响应、异常、租户上下文、MyBatis-Plus 配置；
- `platform-writing`：写作任务、材料、大纲；
- `platform-document`：正文、版本、段落与导出；
- `platform-review`：审校任务、问题和处置；
- `platform-rule`：规则生命周期、版本测试发布、词库以及审校运行时规则加载；
- `platform-knowledge`：文档解析、切片、权限过滤、检索与来源化问答；
- `platform-model-gateway`：模型配置、数据级别路由、脱敏与调用日志；
- `platform-integration`：平台审计与外部系统适配边界；
- `platform-job`：幂等、防重放、持久化任务、重试补偿与 SSE 事件；
- `platform-bootstrap`：启动、安全、配置、Flyway 与集成测试；
- `platform-web`：Vue 3 前端。

## 质量门禁

```powershell
.\mvnw.cmd -pl platform-bootstrap -am test
Set-Location platform-web
pnpm typecheck
pnpm test
pnpm build
```

任何数据库变更必须新增 Flyway 版本脚本，不得修改已发布迁移；任何租户数据访问必须经过租户拦截器，跨租户 API 行为必须有集成测试。
