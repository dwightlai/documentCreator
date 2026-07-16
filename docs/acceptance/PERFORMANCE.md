# 性能验收基线

执行命令：

```bash
cd platform-web
PERF_BASE_URL=http://127.0.0.1:18082 pnpm perf:baseline
```

脚本会创建隔离租户下的测试数据，覆盖以下 V1.6 指标，并输出 P50、P95、P99 和原始窗口数据：

- 50 个用户同时创建写作任务；
- 单段流式生成首个 `delta` 文本事件不超过 5 秒；
- 5000 字文稿基础审校 P99 不超过 10 秒；
- 知识检索连续窗口服务能力不低于 100 QPS。

默认结果写入 `target/acceptance/performance-baseline.json`。默认运行标记为 `LOCAL_BASELINE`，不能代替正式验收。正式验收必须固定生产等价规格，并至少设置：

```bash
STRICT_ACCEPTANCE=true \
PERF_APPLICATION_SPEC='2 x 8C16G, Java 17' \
PERF_DATABASE_SPEC='PostgreSQL 16, primary/replica, 8C16G' \
PERF_NETWORK_CONDITION='application-to-database RTT 1 ms' \
PERF_MODEL_VERSION='approved-model-version' \
PERF_DATASET_VERSION='frozen-acceptance-dataset-version' \
BUILD_SHA='<release commit>' \
ALLOW_MUTATING_LOAD_TEST=true \
PERF_BASE_URL='https://acceptance.example.gov.cn' \
pnpm perf:baseline
```

正式报告还需补充 10 万字 DOCX 解析、20 个审校任务并发、10 个文档解析并发、外部模型端到端耗时与平台自身开销。涉及真实模型、PostgreSQL、Redis、MinIO 和检索集群的结论必须在目标环境执行，不得由本机 H2 基线替代。

## 最近一次本地基线

执行时间：2026-07-16；运行时：Zulu OpenJDK 17.0.16；应用：本次全量验证生成的 Spring Boot JAR；数据库：进程内 H2 PostgreSQL 兼容模式。原始 JSON 写入 `target/acceptance/performance-baseline.json`。

| 场景 | 结果 | 关键数据 |
|---|---|---|
| 50 用户并发创建写作任务 | 通过 | 50/50 成功，P50 1247.14 ms，P95 1415.70 ms，P99 1456.44 ms |
| 单段生成首个文本事件 | 通过 | 350.03 ms，门槛 5000 ms |
| 5000 字基础审校 | 通过 | 10 次，P50 135.34 ms，P95/P99 283.14 ms，门槛 10000 ms |
| 知识检索服务能力 | 通过 | 500/500 成功，5 个窗口最低 219.14 QPS，P99 382.06 ms，门槛 100 QPS |

该结果只证明本机构建的功能与负载脚本可运行，不证明生产容量；正式验收仍须按上文严格模式执行。
