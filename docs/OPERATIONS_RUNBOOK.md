# 上线与灾备运行手册

## 1. 适用范围

本手册覆盖应用、PostgreSQL、Redis、MinIO 和 OIDC 的上线检查，以及 PostgreSQL/MinIO 备份恢复。`compose.yaml` 是单机部署基线，不等同于生产高可用架构；正式环境应由单位的数据库、对象存储、身份源、密钥管理和 TLS 基础设施承载。

目标与详细设计 V1.6 一致：核心服务 RTO 不超过 4 小时；采用主从/WAL 归档时业务数据 RPO 不超过 5 分钟；每日全量数据库备份，MinIO 每日增量同步至独立或异地存储；每季度至少演练一次。

## 2. 上线前强制检查

1. CI 的 backend、postgres-compatibility、frontend、browser-e2e、container-security 全部通过。
2. `.env.example` 中所有示例密码均已替换，实际密钥只由密钥管理系统注入；仓库、镜像和日志中不得出现密钥。
3. OIDC 已替换为单位统一身份源，强制 MFA/密码策略；演示账户已删除或禁用。
4. 外部入口仅开放 HTTPS，证书链、HSTS、反向代理请求体大小与超时已按安全基线配置。
5. 模型端点在精确白名单内；涉密数据路由拒绝、普通数据脱敏、调用日志不落原文均已复核。
6. PostgreSQL 已启用流复制和 WAL 归档，备库延迟告警阈值不高于 5 分钟；MinIO 已启用版本控制和异地复制。
7. 已执行一次恢复演练并保存记录；没有恢复验证的备份不得作为上线依据。
8. 冻结的验收数据、规则/提示词/模型版本、构建号和执行日志已归档。

## 3. 每日备份

备份目录必须位于应用数据卷之外，并由独立备份系统继续复制到异地存储。

```bash
export BACKUP_ROOT=/backup/document-platform
bash deploy/ops/backup.sh
```

产物包含 PostgreSQL custom-format dump、MinIO 对象镜像、镜像/构建元数据和 `SHA256SUMS`。备份任务失败必须告警，且不得覆盖最近一次成功备份。

## 4. 恢复演练

优先在隔离演练环境执行。恢复会停止前后端、重建应用数据库并使目标 MinIO bucket 与备份严格一致。

```bash
export RESTORE_FROM=/backup/document-platform/20260715T120000Z
export CONFIRM_RESTORE=RESTORE_DOCUMENT_PLATFORM
bash deploy/ops/restore.sh
```

恢复后至少验证：readiness 为 UP、Flyway 版本正确、租户隔离查询、文稿下载、材料下载、审校报告导出、OIDC 登录、异步任务和 SSE 重连。记录开始/结束时间以计算 RTO，并对比备份时间与最后业务事务时间计算 RPO。

## 5. 演练记录模板

| 字段 | 记录 |
|---|---|
| 演练编号/日期 |  |
| 环境与负责人 |  |
| 代码构建号 |  |
| 备份时间与校验结果 |  |
| 故障注入方式 |  |
| 恢复开始/完成时间 |  |
| 实测 RTO/RPO |  |
| 数据库记录抽检 |  |
| 对象文件抽检 |  |
| 登录/API/SSE 验证 |  |
| 问题、整改人与期限 |  |
| 复核人与结论 |  |

## 6. 回滚与事件处置

- 应用回滚使用上一份已通过安全扫描的不可变镜像；数据库迁移只允许前向修复，不修改已发布 Flyway 脚本。
- 发现跨租户访问、正文泄露、模型端点异常或密钥暴露时，立即隔离入口、吊销凭据并保全审计日志。
- 恢复失败时保持 backend/web 停止，禁止在数据状态未知时恢复写流量；先保存容器日志、数据库日志和备份校验结果再处置。
