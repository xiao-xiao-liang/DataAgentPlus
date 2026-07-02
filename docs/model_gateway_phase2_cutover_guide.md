# 模型网关阶段 2 单模型切流指南

本文面向阶段 2 单模型切流后的开发、联调、回归验证和线上排障。阶段 2 的目标是把业务侧大模型调用统一收口到 `LlmService` 与 `ModelGateway`，并补齐调用明细、基础指标和隐私边界；本阶段不实现动态路由、多模型候选或自动治理能力。

## 阶段 2 调用链

阶段 2 的生产调用链固定为：

```text
业务/节点
  -> LlmService
  -> GatewayBackedLlmService
  -> ModelGateway
  -> OpenAI 兼容 Provider
  -> AiModelRegistry/ChatClient
```

各层职责如下：

| 层级 | 作用 |
| --- | --- |
| 业务/节点 | 负责构造业务 Prompt，并通过带 `sceneCode` 的 `LlmService` 显式入口发起调用。 |
| `LlmService` | 保留旧兼容接口，同时提供显式场景编码重载。 |
| `GatewayBackedLlmService` | 将旧接口和显式接口统一转换为 `ModelGatewayRequest`。 |
| `ModelGateway` | 负责编排单次调用生命周期、Invocation/Attempt 记录、超时映射和 Micrometer 指标。 |
| OpenAI 兼容 Provider | 将网关消息转发给 Spring AI `ChatClient`，并把响应转换为统一结果或流式片段。 |
| `AiModelRegistry/ChatClient` | 从已配置的 CHAT 模型构建当前可用的 `ChatClient`。 |

业务生产代码不得绕过 `LlmService` 直接访问 `ChatClient`、`ChatModel` 或 `.prompt()`。只有模型网关内部适配层和历史兼容实现可以保留底层调用细节。

## `LlmService` 兼容接口与显式 `sceneCode`

`LlmService` 仍保留以下旧入口，用于兼容既有调用点：

```java
Flux<ChatResponse> call(String system, String user);
Flux<ChatResponse> callSystem(String system);
Flux<ChatResponse> callUser(String user);
```

阶段 2 推荐业务代码使用显式场景入口：

```java
llmService.call(ModelGatewayScenes.SQL_GENERATION, systemPrompt, userPrompt);
llmService.callSystem(ModelGatewayScenes.PYTHON_ANALYZE, systemPrompt);
llmService.callUser(ModelGatewayScenes.REPORT_GENERATION, userPrompt);
```

显式 `sceneCode` 会进入 Invocation 明细和 Micrometer 指标，用于按业务场景排查延迟、错误和 Token 用量。旧入口在 `GatewayBackedLlmService` 中会回落到 `legacy.*` 场景，仅作为兼容路径，不建议新增业务调用继续使用。

主要 `ModelGatewayScenes` 包括：

| 场景常量 | 场景编码 | 用途 |
| --- | --- | --- |
| `DIAGNOSTIC_CHAT` | `diagnostic.chat` | 诊断聊天。 |
| `INTENT_RECOGNITION` | `workflow.intent_recognition` | 工作流意图识别。 |
| `EVIDENCE_RECALL` | `workflow.evidence_recall` | 证据召回。 |
| `QUERY_ENHANCE` | `workflow.query_enhance` | 查询增强。 |
| `FEASIBILITY_ASSESSMENT` | `workflow.feasibility_assessment` | 可行性评估。 |
| `PLANNER` | `workflow.planner` | 规划生成。 |
| `SQL_GENERATION` | `workflow.sql_generation` | SQL 生成。 |
| `SQL_REPAIR` | `workflow.sql_repair` | SQL 修复。 |
| `SCHEMA_MIX_SELECT` | `workflow.schema_mix_select` | Schema 混合选择。 |
| `SEMANTIC_CONSISTENCY` | `workflow.semantic_consistency` | 语义一致性检查。 |
| `DATA_VIEW_ANALYZE` | `workflow.data_view_analyze` | 数据可视化分析。 |
| `PYTHON_GENERATION` | `workflow.python_generation` | Python 代码生成。 |
| `PYTHON_ANALYZE` | `workflow.python_analyze` | Python 分析。 |
| `REPORT_GENERATION` | `workflow.report_generation` | 报告生成。 |
| `CLARIFICATION_NORMALIZE` | `workflow.clarification_normalize` | 澄清内容归一化。 |
| `JSON_REPAIR` | `workflow.json_repair` | JSON 修复。 |
| `HUMAN_FEEDBACK_INTENT` | `workflow.human_feedback_intent` | 人工反馈意图识别。 |
| `SESSION_TITLE` | `service.session_title` | 会话标题生成。 |
| `KNOWLEDGE_CHUNK_NAME` | `service.knowledge_chunk_name` | 知识分块命名。 |
| `AI_SIMULATED_EXECUTION` | `ai_core.ai_simulated_execution` | AI 模拟执行。 |
| `LEGACY_SYSTEM_USER` | `legacy.system_user` | 旧系统消息 + 用户消息兼容入口。 |
| `LEGACY_SYSTEM_ONLY` | `legacy.system_only` | 旧系统消息兼容入口。 |
| `LEGACY_USER_ONLY` | `legacy.user_only` | 旧用户消息兼容入口。 |

## Invocation / Attempt 表字段与隐私边界

阶段 2 通过 `model_gateway_invocation` 记录一次模型网关调用，通过 `model_gateway_attempt` 记录该调用下的 Provider 尝试。当前阶段固定单模型、单次尝试，但表结构保留了排障所需的调用生命周期字段。

当前实现中，`DefaultModelGateway.startLifecycle` 在 Provider 返回前创建 Attempt，因此 Attempt 的 `provider` 与 `model` 以 `unknown` 占位写入；`finishAttempt` 只更新尝试状态、结束时间、耗时、错误码和错误摘要，不回填 Provider 与模型。排障时应优先查看成功 Invocation 的 `provider`、`model` 路由字段，以及 Prometheus 指标中的 `provider`、`model` 标签。

### `model_gateway_invocation`

| 字段 | 说明 | 隐私边界 |
| --- | --- | --- |
| `invocation_id` | 一次网关调用的唯一标识。 | 可用于排障，不写入 Prompt 或响应正文。 |
| `run_id` | 工作流运行 ID。 | 低敏关联字段，不进入指标标签。 |
| `trace_id` | OpenTelemetry Trace ID。 | 低敏关联字段，不进入指标标签。 |
| `session_id` | 会话 ID。 | 仅用于业务关联，不记录会话原始内容。 |
| `user_id` | 用户 ID。 | 仅用于内部审计，不进入日志正文或指标标签。 |
| `agent_id` | 智能体 ID。 | 可用于定位配置来源。 |
| `tenant_id` | 租户 ID。 | 仅记录租户标识，不记录租户敏感数据。 |
| `scene_code` | 显式调用场景编码。 | 允许进入日志和指标标签。 |
| `call_mode` | `BLOCK` 或 `STREAM`。 | 低敏枚举。 |
| `status` | `RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED` 等状态。 | 低敏枚举。 |
| `provider` | 实际 Provider。 | 低敏路由字段，不包含凭证。 |
| `model` | 实际模型名称。 | 低敏路由字段，不包含配置密钥。 |
| `start_time` / `end_time` / `duration_ms` | 调用起止时间与耗时。 | 仅用于性能排查。 |
| `input_tokens` / `output_tokens` / `total_tokens` | Token 用量。 | 只记录数量，不记录文本。 |
| `error_code` | 结构化错误码。 | 低敏枚举。 |
| `error_message` | 错误摘要。 | 只允许默认错误文案或脱敏摘要，不写入完整异常正文。 |

### `model_gateway_attempt`

| 字段 | 说明 | 隐私边界 |
| --- | --- | --- |
| `attempt_id` | 一次 Provider 尝试的唯一标识。 | 可用于排障，不写入 Prompt 或响应正文。 |
| `invocation_id` | 关联主调用。 | 与 Invocation 关联排查。 |
| `attempt_no` | 尝试序号。 | 阶段 2 固定为单次尝试。 |
| `provider` | 尝试开始时写入的 Provider 占位值；阶段 2 当前为 `unknown`，不会在结束时回填。 | 不包含凭证；实际 Provider 优先看成功 Invocation 的路由字段和指标标签。 |
| `model` | 尝试开始时写入的模型占位值；阶段 2 当前为 `unknown`，不会在结束时回填。 | 不包含配置密钥；实际模型优先看成功 Invocation 的路由字段和指标标签。 |
| `status` | 尝试状态。 | 低敏枚举。 |
| `start_time` / `end_time` / `duration_ms` | 尝试起止时间与耗时。 | 仅用于性能排查。 |
| `http_status` | Provider HTTP 状态码预留字段；阶段 2 当前网关结束 Attempt 时传入 `null`，暂未填充。 | 低敏状态字段；当前排障不要依赖该字段。 |
| `error_code` | 结构化错误码；数据库保存 `errorCode.code()`，例如 `C020004`、`C020002`。 | 低敏错误码；Prometheus 标签使用枚举名。 |
| `error_message` | 错误摘要。 | 不允许包含完整 Prompt、完整响应或凭证。 |

Invocation / Attempt、Span、Metric 和日志均不得记录或泄露以下内容：

- 完整 Prompt、完整响应、未脱敏用户输入。
- `apiKey`、访问 Token、`proxyPassword`、数据库密码、对象存储密钥等凭证。
- 可直接识别个人身份或业务敏感信息的原文。

## 通过 `invocationId`、`runId`、`traceId` 排查调用

推荐按以下顺序定位问题：

1. 从业务返回、工作流运行记录或日志中获取 `runId`、`traceId`，或从模型网关错误日志关联 `invocationId`。
2. 使用 `runId` 查询工作流运行记录，确认失败节点、运行状态、当前 checkpoint 和业务上下文。
3. 使用 `invocationId` 查询 `model_gateway_invocation`，确认 `scene_code`、`call_mode`、`status`、`provider`、`model`、耗时、Token 用量和 `error_code`。
4. 使用 `invocation_id` 查询 `model_gateway_attempt`，确认单次尝试生命周期、状态、耗时和错误码；阶段 2 当前 Attempt 的 `provider`、`model` 为 `unknown` 占位，`http_status` 为预留字段且暂未填充。
5. 使用 `traceId` 在 Grafana Tempo Explore 或 Tempo API 中查看 Span 树，确认 HTTP 入口、工作流节点和模型网关调用的时间关系。
6. 将 `scene_code`、`provider`、`model`、`status`、`error_code` 与 Prometheus 指标聚合结果对齐，判断是单点调用失败还是场景级异常升高；注意数据库 `error_code` 保存错误码值，Prometheus `error_code` 标签保存错误枚举名。

常用 SQL 示例：

```sql
SELECT *
FROM model_gateway_invocation
WHERE invocation_id = '替换为调用ID';

SELECT *
FROM model_gateway_invocation
WHERE run_id = '替换为运行ID'
ORDER BY start_time DESC;

SELECT *
FROM model_gateway_invocation
WHERE trace_id = '替换为链路ID'
ORDER BY start_time DESC;

SELECT *
FROM model_gateway_attempt
WHERE invocation_id = '替换为调用ID'
ORDER BY attempt_no ASC;

SELECT invocation_id, scene_code, status, error_code, error_message, start_time, end_time
FROM model_gateway_invocation
WHERE error_code = 'C020004'
ORDER BY start_time DESC;
```

Tempo API 示例：

```text
http://localhost:3200/api/traces/{traceId}
```

## 资源门控繁忙与 `RATE_LIMITED`

当资源门控判断大模型资源繁忙时，`ResourceGatedLlmService` 会返回结构化模型网关异常：

| 字段 | 语义 |
| --- | --- |
| 错误枚举 | `ModelGatewayErrorCode.RATE_LIMITED` |
| 错误码 | `C020001` |
| 默认文案 | `模型调用被限流` |
| 业务繁忙文案 | `大模型资源繁忙，请稍后重试` |
| 是否可重试 | `true` |
| 是否可降级 | `true`，但阶段 2 不提供自动降级实现。 |

调用方应按结构化错误码识别繁忙语义，并由上层决定提示用户稍后重试、排队或人工介入。阶段 2 不自动切换 Provider、不自动降级到其他模型，也不隐式重试多模型候选。

## Prometheus / Micrometer 指标

模型网关通过 Micrometer 记录基础指标，并由 `/actuator/prometheus` 暴露给 Prometheus 抓取。

| 指标名 | 类型 | 说明 |
| --- | --- | --- |
| `model_gateway_invocations_total` | Counter | 模型网关调用次数。 |
| `model_gateway_invocation_duration_seconds` | Timer | 模型网关调用耗时。 |
| `model_gateway_tokens_total` | Counter | 成功调用的总 Token 数。 |
| `model_gateway_errors_total` | Counter | 模型网关错误次数。 |

指标标签固定为：

| 标签 | 说明 |
| --- | --- |
| `scene_code` | 调用场景编码，例如 `workflow.sql_generation`。 |
| `provider` | Provider 标识；失败时无法确定路由可为 `unknown`。 |
| `model` | 模型名称；失败时无法确定路由可为 `unknown`。 |
| `status` | 调用状态，例如 `SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| `error_code` | Prometheus 错误标签使用错误枚举名；成功时为 `none`。数据库明细表的 `error_code` 使用错误码值。 |

指标标签不得包含 `invocationId`、`runId`、`traceId`、用户输入、Prompt、响应正文或任何凭证，避免高基数和敏感信息泄露。

Prometheus 查询示例：

```promql
sum by (scene_code, status) (increase(model_gateway_invocations_total[5m]))
sum by (scene_code) (rate(model_gateway_invocation_duration_seconds_sum[5m]))
  / sum by (scene_code) (rate(model_gateway_invocation_duration_seconds_count[5m]))
max by (scene_code, provider, model) (model_gateway_invocation_duration_seconds_max)
sum by (scene_code, provider, model) (increase(model_gateway_tokens_total[1h]))
sum by (scene_code, error_code) (increase(model_gateway_errors_total[5m]))
```

当前 `Timer.builder` 未显式启用 percentile histogram，因此默认不保证存在 bucket 序列。若需要 P95/P99 等分位查询，应先在 Micrometer/Prometheus 配置中启用 percentile histogram，再使用分位函数。

## 阶段 2 不支持项边界

阶段 2 明确不支持以下能力：

- 动态路由。
- Langfuse。
- 预算控制。
- 熔断。
- 自动降级。
- 多模型候选。
- Nacos Prompt 热更新。
- 管理 UI。

如果排障时发现需要上述能力，应记录为后续阶段需求，不要在阶段 2 通过临时代码绕过 `LlmService` 或 `ModelGateway`。

## 常见故障

### 未配置 CHAT 模型

现象：首次模型调用时 `AiModelRegistry` 无法创建 `ChatClient`，业务侧收到 Provider 不可用或初始化失败。

排查：

1. 确认数据库或配置中心存在启用状态的 CHAT 类型模型配置。
2. 确认模型配置包含 Provider、模型名称、基础地址和必要认证信息。
3. 清理 `AiModelRegistry` 缓存后重试，确认下一次调用可重新初始化。

### Provider 认证失败

现象：Invocation 状态为 `FAILED`，数据库 `error_code` 为 `C020004`，对应错误枚举 `AUTHENTICATION_FAILED`；Prometheus `error_code` 标签为 `AUTHENTICATION_FAILED`。Attempt 主要用于确认尝试状态、耗时和错误码，`http_status` 当前暂未填充，不能依赖它判断 401 或 403。

排查：

1. 核对 Provider 认证配置是否存在且未过期。
2. 检查代理配置是否需要认证。
3. 日志和表中不得输出 `apiKey`、`proxyPassword` 或完整 Provider 响应正文。

### 超时

现象：Invocation 状态为 `FAILED`，数据库 `error_code` 为 `C020002`，对应错误枚举 `PROVIDER_TIMEOUT`；Prometheus `error_code` 标签为 `PROVIDER_TIMEOUT`，耗时接近默认超时配置。

排查：

1. 查看 `model_gateway_invocation_duration_seconds` 是否在特定 `scene_code`、`provider`、`model` 上升高。
2. 检查 Provider 可用性、网络代理和本地资源门控状态。
3. 确认调用方没有把大输入原文写入日志，只保留长度、Token 数或脱敏摘要。

### 调用明细未写入

现象：业务调用已发生，但 `model_gateway_invocation` 或 `model_gateway_attempt` 缺少记录。

排查：

1. 确认已执行 `V20260625_01__model_gateway_invocation_attempt.sql`。
2. 确认应用已启用持久化记录器 Bean，且 Mapper 扫描正常。
3. 检查应用日志中的“模型网关记录器调用失败”告警；该告警只记录阶段和异常类型，不应记录完整异常正文、Prompt 或响应。

### Prometheus 指标为空

现象：`/actuator/prometheus` 或 Prometheus 查询不到 `model_gateway_*` 指标。

排查：

1. 先触发一次真实模型网关调用；未发生调用时不会产生相关指标。
2. 确认应用暴露 `/actuator/prometheus`，Prometheus Targets 中 `data-agent` 为 `UP`。
3. 确认应用进程存在可用的 `MeterRegistry`，并且调用没有绕过 `GatewayBackedLlmService` 和 `ModelGateway`。
4. 检查是否只发生了启动或健康检查请求；这些请求不会增加模型网关指标。

## 阶段 2 验证记录

- 核心模块测试：`mvn -pl data-agent-model-gateway,data-agent-ai-core,data-agent-workflow,data-agent-service -am test`，结果为 `BUILD SUCCESS`。
- 启动模块定向测试：`mvn -pl data-agent-start -am "-Dtest=GraphControllerTest,GraphServiceTest,WorkflowRunServiceImplTest,ModelGatewaySchemaTest,ModelGatewayCutoverScanTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`，结果为 `BUILD SUCCESS`。
- 全量验证：`mvn clean verify` 当前失败于既有无关基线 `AgentKnowledgeChunkEntityTest.migrationShouldAddTaskVersionAndProcessingLease`，原因是缺少 `sql/migration/V20260606_02__knowledge_chunk_task_version.sql`，本阶段未修复该无关基线。
- 敏感信息与直连出口扫描已执行，命中内容为既有配置、测试、基础设施或禁止泄露语境；`ModelGatewayCutoverScanTest` 已通过。
- 文档格式检查：`git diff --check` 已通过。
