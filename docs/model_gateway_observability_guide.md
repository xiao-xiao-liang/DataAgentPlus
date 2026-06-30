# 模型网关观测指南

本文面向阶段 1 的本地开发、联调和回归验证，说明模型网关、工作流运行身份、OpenTelemetry 链路追踪、Prometheus 指标与本地可观测栈的使用方式。

## 模块职责

- `data-agent-model-gateway`：定义与模型供应商无关的调用协议、错误语义、Prompt Registry 契约、执行上下文和 Reactor Context 传播工具。本阶段只交付协议和上下文基础，不接入真实模型调用。
- `data-agent-workflow`：在图执行过程中读取并传播 `GatewayExecutionContext`，使用 `runId` 定位工作流运行记录，写入节点完成、中断、失败和完成状态。
- `data-agent-start`：作为应用启动模块，创建或恢复运行上下文，接入 Micrometer Tracing、OTLP Trace 导出和 Prometheus 指标暴露，并提供 `/actuator/health`、`/actuator/prometheus` 等端点。
- `docker/observability`：提供本地 OpenTelemetry Collector、Tempo、Prometheus、Grafana 编排、数据源、默认仪表盘和冒烟检查脚本。

## 推荐启动顺序

1. 数据库升级 SQL：先备份目标库，在测试环境确认脚本可执行，再对目标环境执行 `data-agent-start/src/main/resources/sql/migration/V20260623_01__workflow_run_trace_identity.sql`。
2. 观测栈：在项目根目录执行 `docker compose -f docker/observability/docker-compose.yml up -d`，启动 Collector、Tempo、Prometheus 和 Grafana。
3. 应用环境变量：按需配置追踪开关、采样率和 OTLP Trace endpoint。
4. 应用启动：启动 `data-agent-start`，默认监听 `18080`。
5. 冒烟验证：执行 `powershell -ExecutionPolicy Bypass -File docker/observability/smoke-test.ps1`，再访问 `/actuator/health` 和 `/actuator/prometheus` 确认应用健康与指标可抓取。

## 环境变量

当前代码在 `data-agent-start/src/main/resources/application.yml` 中支持以下环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OBSERVABILITY_TRACING_ENABLED` | `true` | 是否启用 Spring Boot tracing。需要临时关闭追踪时设为 `false`。 |
| `OBSERVABILITY_SAMPLING_PROBABILITY` | `1.0` | Trace 采样率，范围通常为 `0.0` 到 `1.0`。本地默认全量采样便于验证，生产环境应降低。 |
| `OBSERVABILITY_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP HTTP traces 完整上报地址，必须包含 `/v1/traces`。 |

注意：本阶段应用配置使用 `OBSERVABILITY_OTLP_TRACES_ENDPOINT`，没有直接读取 `OTEL_EXPORTER_OTLP_ENDPOINT` 作为别名。如果部署环境统一下发 OTEL base URL，请在启动脚本中转换为完整 traces endpoint，例如把 `http://collector:4318` 转为 `http://collector:4318/v1/traces` 后赋值给 `OBSERVABILITY_OTLP_TRACES_ENDPOINT`。

示例：

```powershell
$env:OBSERVABILITY_TRACING_ENABLED='true'
$env:OBSERVABILITY_SAMPLING_PROBABILITY='0.1'
$env:OBSERVABILITY_OTLP_TRACES_ENDPOINT='http://localhost:4318/v1/traces'
```

## 本地端口

| 组件 | 端口 | 用途 |
| --- | --- | --- |
| OpenTelemetry Collector | `4317` | OTLP gRPC 接收端口 |
| OpenTelemetry Collector | `4318` | OTLP HTTP 接收端口 |
| OpenTelemetry Collector | `13133` | Collector 健康检查 |
| Tempo | `3200` | Tempo API 与 ready 检查 |
| Prometheus | `9090` | Prometheus 控制台和查询 API |
| Grafana | `3000` | Grafana 控制台 |
| Data Agent 应用 | `18080` | 应用服务和 actuator 端点 |

`docker/observability/docker-compose.yml` 默认将观测组件端口绑定到 `127.0.0.1`，只建议本机开发使用。

## 通过 traceId 查询链路

1. 触发一次工作流请求后，从业务日志、运行记录或接口返回上下文中获取 `traceId`。
2. 打开 Grafana：`http://localhost:3000`，默认账号密码为 `admin/admin`。
3. 进入 Explore，数据源选择 `Tempo`。
4. 选择 TraceID 查询方式，输入完整 `traceId` 后执行查询。
5. 打开查询结果查看 Span 树，结合 `runId`、`sceneCode`、状态、耗时等低敏字段定位请求链路。

也可以直接通过 Tempo API 按 traceId 查询：

```text
http://localhost:3200/api/traces/{traceId}
```

如果 Tempo 查不到 Trace，先确认应用已产生请求、`OBSERVABILITY_TRACING_ENABLED=true`、采样率大于 `0`、应用 OTLP endpoint 指向 Collector 的 `http://localhost:4318/v1/traces`，再查看 Collector 和 Tempo 容器日志。

## 阶段 2 单模型切流观测补充

阶段 2 单模型切流后，生产模型调用应从业务/节点进入 `LlmService` 显式 `sceneCode` 入口，再经 `GatewayBackedLlmService`、`ModelGateway`、OpenAI 兼容 Provider 和 `AiModelRegistry/ChatClient` 发起真实调用。链路排查入口如下：

1. 通过 `runId` 定位工作流运行记录，确认失败节点、运行状态和业务上下文。
2. 通过 `traceId` 在 Grafana Tempo Explore 或 `http://localhost:3200/api/traces/{traceId}` 查询完整 Span 树。
3. 通过 `invocationId` 查询 `model_gateway_invocation`，再用同一 `invocation_id` 查询 `model_gateway_attempt`，确认 `scene_code`、Provider、模型、状态、耗时、Token 用量和结构化错误码。
4. 通过 Prometheus 查询 `model_gateway_invocations_total`、`model_gateway_invocation_duration_seconds`、`model_gateway_tokens_total`、`model_gateway_errors_total`，按 `scene_code`、`provider`、`model`、`status`、`error_code` 聚合观察调用量、延迟、用量和错误。

阶段 2 的指标标签不得加入 `runId`、`traceId`、`invocationId` 等高基数字段。日志、Span、Metric、Invocation / Attempt 明细只允许记录排障所需的低敏字段和脱敏摘要，禁止记录或泄露完整 Prompt、完整响应、`apiKey`、`proxyPassword`、访问 Token 等凭证。更完整的单模型切流说明见 `docs/model_gateway_phase2_cutover_guide.md`。

## 关闭追踪与降低采样率

- 关闭追踪：启动应用前设置 `OBSERVABILITY_TRACING_ENABLED=false`。
- 降低采样率：设置 `OBSERVABILITY_SAMPLING_PROBABILITY`，例如 `0.1` 表示约 10% 采样，`0.01` 表示约 1% 采样。
- 临时停止本地观测栈：执行 `docker compose -f docker/observability/docker-compose.yml down`。OTLP 不可达时应用不应阻断主流程，最多记录导出失败或连接失败日志。

## 执行升级 SQL

升级脚本路径：

```text
data-agent-start/src/main/resources/sql/migration/V20260623_01__workflow_run_trace_identity.sql
```

执行前要求：

1. 先备份目标数据库，尤其是 `chat_workflow_run` 表。
2. 先在测试环境执行并验证应用启动、工作流创建、恢复和状态更新。
3. 执行前确认目标表尚未添加同名字段和索引；脚本是显式 DDL，重复执行会因字段或索引已存在而失败。

脚本对 `chat_workflow_run` 增加以下字段和索引：

| 字段或索引 | 作用 |
| --- | --- |
| `run_id` | 标识一次工作流运行，用于替代“按会话查最近记录”的更新方式。 |
| `trace_id` | 保存 OpenTelemetry Trace ID，便于从业务运行记录跳转到 Tempo/Grafana 查询链路。 |
| `start_time` | 保存运行开始时间，用于耗时计算和运行检索。 |
| `end_time` | 保存运行结束时间，用于完成、失败、中断后的审计。 |
| `duration_ms` | 保存运行耗时毫秒数，便于统计和排障。 |
| `failed_node_name` | 保存失败节点名称，便于快速定位失败位置。 |
| `uk_run_id` | 保证 `run_id` 唯一，避免多次运行身份冲突。 |
| `idx_trace_id` | 支持按 `trace_id` 检索业务运行记录和链路跳转。 |

## 数据隐私边界

观测与日志只允许记录排障所需的低敏字段，例如 `runId`、`traceId`、`sceneCode`、节点名、状态、错误类型、耗时、Token 数量或脱敏摘要。

禁止记录或写入 Span、Metric、日志、Baggage 的内容包括：

- 完整 Prompt、完整模型响应、未脱敏用户输入。
- API Key、访问 Token、代理密码、数据库密码、MinIO 密钥等凭证。
- 可直接识别个人身份或业务敏感信息的原文。

如后续阶段需要保留模型输入输出样本，必须先完成统一脱敏、采样、保留期和访问控制设计。

## 常见故障

- Docker Engine 未启动：执行 `docker version` 和 `docker info` 检查。Windows/macOS 先启动 Docker Desktop 并等待 Engine 就绪；Linux 确认 Docker 服务已启动。
- 端口占用：确认本机 `4317`、`4318`、`13133`、`3200`、`9090`、`3000`、`18080` 未被其他进程占用。
- Prometheus 抓不到 data-agent：确认应用已在本机 `18080` 启动，`/actuator/prometheus` 可访问；确认 Docker 可访问 `host.docker.internal:18080`；在 Prometheus Targets 页面检查 `data-agent` 目标。
- Tempo 无 Trace：确认采样率大于 `0`，应用 OTLP endpoint 为 `http://localhost:4318/v1/traces`，Collector 健康检查正常，Tempo `/ready` 返回成功。
- OTLP 不可达：Collector 停止或网络异常时，应用不应阻断请求；先验证 `/actuator/health` 是否仍为 `UP`，再查看应用日志和 Collector 连接配置。
- Grafana 默认账号：`admin/admin` 只限本机开发使用，不要暴露到公网；如需共享环境，请修改 `GF_SECURITY_ADMIN_USER` 和 `GF_SECURITY_ADMIN_PASSWORD` 并增加网络访问控制。

## 本阶段边界

阶段 1 只交付模型网关协议、工作流运行身份传播、基础追踪与本地观测栈。本阶段未接入真实模型调用，未实现动态路由，未接入 Langfuse，也未完成完整节点埋点。上述能力应在后续阶段按独立计划实施和验收。
