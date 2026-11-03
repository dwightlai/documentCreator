# OA 系统对接接口（v1）

本接口用于第三方系统发起写作任务、上传本地 DOCX 审校，以及读取已提交的文稿和审校结果。写作、问题处理、报告确认等人工操作继续在平台页面完成。所有路径均以 `/api/v1/integrations/oa` 为前缀。

## 接入身份与隔离

- 生产环境使用 OAuth 2.0 `client_credentials` 获取 Bearer access token。令牌须包含 `oa.integration` scope、`tenant_id` claim，且 `azp`（或 `client_id`）须在后端 `OA_ALLOWED_CLIENT_IDS` 白名单内。未配置白名单时，OA 专用接口默认拒绝调用。
- 给每个 OA 租户分配独立机密客户端，并在身份提供方将固定租户编号写入 `tenant_id`。客户端密钥存放在密钥管理系统，不放入代码或前端。OA 机器令牌不能调用平台的通用写作、审校和管理接口。
- 本地开发环境可使用 `X-Tenant-Id`、`X-User-Id` 模拟身份；生产环境不接受这些头作为身份依据。
- 请在网关只开放本文件列出的 OA 路径给第三方。人工页面通过用户登录访问，例如 `/writing/{taskId}/editor`。

## 通用约定

- 创建请求必须发送 `Idempotency-Key`，长度 8–128，只能使用字母、数字、点、下划线、冒号和连字符。同一租户、接口与键的相同请求会重放第一次响应；相同键对应不同请求返回 `409`。
- `businessId` 是 OA 单据编号，同一租户内的 OA 写作任务、OA 审校任务分别检查重复编号。再次发起同类任务使用新键但相同编号时返回 `409`。查询时必须同时提供任务 ID 和 `businessId`。
- JSON 响应采用平台统一包装，业务字段位于 `data`。创建写作返回 HTTP `201`，发起审校返回 HTTP `202`；当前审校在请求内完成，回调和报告投递仍为异步。失败请求可能返回 `400`、`401`、`403`、`404`、`409`、`413` 或 `422`。
- DOCX 上传字段名为 `file`，最大 50 MB。只接受有效 DOCX。带幂等键的 multipart 整体请求上限为 60 MB，幂等指纹包含各表单字段和文件内容。

## 1. 发起写作

`POST /writing-tasks`，`Content-Type: application/json`

```json
{
  "businessId": "OA-2026-00128",
  "documentType": "通知",
  "taskName": "关于开展安全检查的通知",
  "writingTopic": "部署安全检查",
  "writingPurpose": "明确检查范围和责任",
  "draftingDepartment": "办公室",
  "materials": [{"materialName": "检查方案", "content": "检查范围为重点单位。"}],
  "callbackUrl": "https://oa.example.gov.cn/callback/review/result"
}
```

返回 `data.writingTask.id`、`data.materialCount` 和 OA 专用 `data.statusUrl`。OA 将用户带到 `/writing/{id}/editor` 完成写作。查询进度：

`GET /writing-tasks/{id}?businessId=OA-2026-00128`

若只保存了 OA 单据编号，也可用 `GET /writing-tasks?businessId=OA-2026-00128` 找回任务。

返回写作任务状态和 `latestSubmission`。文稿必须由平台提交门禁检查通过后，才能通过人工操作“提交 OA”；提交时自动回调。只可读取真正提交过的版本：

`GET /writing-tasks/{id}/document/versions/{version}?businessId=OA-2026-00128`

返回结构化文稿 JSON，含标题和段落。未提交的版本返回 `404`。

若提交关联了审校报告，可读取报告版本及文件：

- `GET /writing-tasks/{id}/reviews/{reviewId}/reports?businessId=OA-2026-00128`
- `GET /writing-tasks/{id}/reviews/{reviewId}/reports/{version}/export?businessId=OA-2026-00128&format=PDF`（也支持 `DOCX`）

## 2. 审校平台写作任务

`POST /review-tasks`，`Content-Type: application/json`

```json
{
  "businessId": "OA-REVIEW-001",
  "writingTaskId": "平台写作任务 ID",
  "reviewPlanId": "可选的审校方案 ID",
  "callbackUrl": "https://oa.example.gov.cn/callback/review/result"
}
```

## 3. 审校 OA 已有 DOCX

`POST /review-tasks/upload`，`Content-Type: multipart/form-data`

表单字段：`businessId`、`documentTypeCode`、可选 `reviewPlanId`、可选 `outputTemplateId`、可选 `callbackUrl`，以及 DOCX 文件 `file`。无需先在平台创建写作任务。

```bash
curl -X POST "$BASE/api/v1/integrations/oa/review-tasks/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: oa-review-2026-001" \
  -F "businessId=OA-REVIEW-001" \
  -F "documentTypeCode=请示" \
  -F "file=@待审请示.docx"
```

以上两种审校入口均返回 `data.reviewTask.id`、`data.statusUrl` 和可选 `data.callbackJobId`。完整结果（任务状态、问题位置与建议、报告版本）：

`GET /review-tasks/{id}?businessId=OA-REVIEW-001`

也可用 `GET /review-tasks?businessId=OA-REVIEW-001` 按 OA 单据编号查询。

导出报告：

`GET /review-tasks/{id}/reports/{version}/export?businessId=OA-REVIEW-001&format=PDF`

## 回调

设置 `callbackUrl` 前，管理员须将其 HTTPS 源和路径加入 `OA_CALLBACK_ALLOWLIST`，并配置 `OA_SHARED_SECRET`。回调使用后台任务重试；接收方应以 `submissionId` / `reviewTaskId` 去重，并在收到后调用状态接口获取最终事实。

- 审校完成事件 `REVIEW_COMPLETED`：`businessId`、`reviewTaskId`、`status`、`totalIssues`、`pendingIssues`、`statusUrl`。
- 文稿提交事件 `DOCUMENT_SUBMITTED`：`businessId`、`writingTaskId`、`submissionId`、`documentVersion`、`reviewTaskId`、`documentUrl`、`reportUrl` 和 AI 使用比例。
- 回调不携带正文。请求头 `X-Callback-Timestamp` 为 Unix 秒，`X-Callback-Nonce` 为随机串，`X-Callback-Signature` 为小写十六进制 HMAC-SHA256。签名输入为 `timestamp + "\n" + nonce + "\n" + 原始 JSON 请求体字节`。接收方校验签名、时间窗和 nonce 防重放。

部署参数见根目录 `.env.example`。生产环境需在 OIDC 中为 OA 客户端授权 `oa.integration` scope 和固定 `tenant_id` claim；客户端密钥由 OIDC 管理，不使用 `OA_SHARED_SECRET` 作为登录密钥。
