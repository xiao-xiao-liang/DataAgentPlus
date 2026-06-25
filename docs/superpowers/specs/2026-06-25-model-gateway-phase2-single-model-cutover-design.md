# 模型网关阶段 2：单模型切流设计

## 1. 背景

阶段 1 已完成模型网关协议、结构化错误、Prompt Registry 契约、执行上下文传播、`runId`/`traceId` 持久化、OTLP/Prometheus/Grafana 本地观测栈和使用指南。当前生产模型调用仍主要通过 `LlmService` 间接访问 `AiModelRegistry` 与 Spring AI `ChatClient`，调用方无法统一表达场景、调用身份、Provider 尝试、错误语义和调用明细。

阶段 2 的核心任务是先把所有生产模型调用统一收口到模型网关。动态路由、预算治理、熔断、备用模型降级、Langfuse 和 Prompt 热更新不在本阶段实现，避免把“切流正确性”和“策略治理正确性”混在一起。

## 2. 目标

1. 所有生产模型调用只有一个真实出口：`ModelGateway -> OpenAI 兼容 Provider -> Spring AI ChatClient/ChatModel`。
2. 旧 `LlmService` 保留为兼容接口，但默认实现改为网关适配器，不再直接调用 `AiModelRegistry.getChatClient()`。
3. 一次性收口工作流、服务层和 AI Core 中通过 `LlmService` 发起的生产模型调用。
4. 为主要调用点补齐显式 `sceneCode`，兼容场景只作为遗漏兜底。
5. 建立 Invocation / Attempt 生命周期和持久化明细，不保存完整 Prompt、完整响应和任何密钥。
6. 支持阻塞聚合调用和流式调用，保持现有业务调用形态和 SSE 行为不发生破坏性变化。
7. 将 Provider 异常、超时、限流、认证失败、空响应等统一转换为 `ModelGatewayException`。
8. 建立基础 Span / Metric，为阶段 3 动态路由和阶段 4 深度观测提供数据地基。

## 3. 非目标

1. 不实现多模型候选、动态路由、规则路由、预算控制、自动重试、熔断和备用模型降级。
2. 不接入 Langfuse。
3. 不实现 Nacos Prompt 热更新、模板版本切换和回滚。
4. 不建设管理端模型策略页面。
5. 不落库完整 Prompt、完整模型响应、API Key、访问 Token、代理密码或数据库密码。
6. 不提供业务节点绕过网关直连旧 `ChatClient` 的回退开关。

## 4. 总体架构

阶段 2 的生产调用链如下：

```text
业务节点 / 业务服务
  -> LlmService 兼容接口
  -> GatewayBackedLlmService
  -> ModelGateway
  -> OpenAI 兼容 Provider
  -> AiModelRegistry / Spring AI ChatClient
```

旧路径：

```text
业务节点 / 业务服务
  -> BlockLlmService / StreamLlmService
  -> AiModelRegistry
  -> Spring AI ChatClient
```

阶段 2 完成后旧路径不再作为生产 Bean 出口。旧实现可以暂时保留为测试夹具或迁移对照，但不得被默认自动配置注入到生产链路。

## 5. 组件设计

### 5.1 GatewayBackedLlmService

`GatewayBackedLlmService` 实现现有 `LlmService` 接口，负责把旧接口转换为 `ModelGatewayRequest`。

职责：

1. 根据调用方法和显式场景构建 `ModelMessage` 列表。
2. 根据 `data-agent.llm-service-mode` 选择 `ModelCallMode.BLOCK` 或 `ModelCallMode.STREAM`。
3. 调用 `ModelGateway.call` 或 `ModelGateway.stream`。
4. 将网关结果转换回现有 `Flux<ChatResponse>`，保证调用方不需要一次性重写。
5. 不记录完整系统消息、用户消息和模型响应。

### 5.2 显式场景调用接口

旧 `LlmService` 只有 `call(system, user)`、`callSystem(system)`、`callUser(user)`，无法准确表达业务场景。本阶段在兼容接口上增加默认方法或新接口，用于显式传入场景：

```java
Flux<ChatResponse> call(String sceneCode, String system, String user);
Flux<ChatResponse> callUser(String sceneCode, String user);
Flux<ChatResponse> callSystem(String sceneCode, String system);
```

旧方法仍保留，但只映射到兼容场景：

- `LEGACY_SYSTEM_USER`
- `LEGACY_USER_ONLY`
- `LEGACY_SYSTEM_ONLY`

主要生产调用点必须迁移到显式场景方法。

### 5.3 OpenAI 兼容 Provider

阶段 2 只提供一个 OpenAI 兼容 Provider，复用现有 `AiModelRegistry` 获取当前全局 ChatClient，继续沿用已有模型配置、baseUrl、completionsPath、temperature、maxTokens 和 streamUsage 行为。

职责：

1. 执行非流式聚合调用。
2. 执行流式调用。
3. 提取 provider、model、finishReason 和 Token 用量。
4. 将 Spring AI / Provider 异常映射为 `ModelGatewayErrorCode`。
5. 不感知场景路由，不选择多个候选模型。

### 5.4 DefaultModelGateway

`DefaultModelGateway` 是阶段 2 的网关实现。

职责：

1. 校验请求模式，`call` 只接受 `BLOCK`，`stream` 只接受 `STREAM`。
2. 创建 `invocationId`。
3. 从 `GatewayReactorContext` 读取 `runId`、`traceId`、`sessionId`、`userId`、`agentId` 和 `tenantId`；上下文缺失时生成兼容上下文，但记录中文警告。
4. 创建 Invocation / Attempt 生命周期记录。
5. 套用默认调用超时。
6. 调用 OpenAI 兼容 Provider。
7. 成功、失败、取消时更新调用明细。
8. 写入基础 Micrometer 指标和当前 Trace Span 属性。
9. 持久化失败不得阻断主调用。

## 6. 场景编码

阶段 2 建议建立常量类 `ModelGatewayScenes`，避免散落硬编码字符串。

首批场景：

- `INTENT_RECOGNITION`
- `QUERY_ENHANCE`
- `EVIDENCE_REWRITE`
- `FEASIBILITY_ASSESSMENT`
- `SCHEMA_MIX_SELECT`
- `SQL_GENERATION`
- `SQL_REPAIR`
- `SEMANTIC_CONSISTENCY`
- `PLANNER`
- `PYTHON_GENERATION`
- `PYTHON_ANALYZE`
- `REPORT_GENERATION`
- `JSON_REPAIR`
- `SESSION_TITLE`
- `KNOWLEDGE_CHUNK_NAME`
- `DATA_VIEW_ANALYZE`
- `HUMAN_FEEDBACK_INTENT`
- `AI_SIMULATED_EXECUTION`
- `LEGACY_SYSTEM_USER`
- `LEGACY_USER_ONLY`
- `LEGACY_SYSTEM_ONLY`

阶段 2 验收时，生产调用点应尽量使用业务场景编码；兼容场景只允许作为遗漏兜底和测试兼容。

## 7. 持久化设计

### 7.1 model_gateway_invocation

记录一次业务模型调用。

| 字段 | 说明 |
| --- | --- |
| `id` | 自增主键 |
| `invocation_id` | 网关调用编号，唯一 |
| `run_id` | 工作流运行编号，可为空但新工作流链路应填写 |
| `trace_id` | OpenTelemetry Trace ID |
| `session_id` | 会话编号 |
| `user_id` | 用户编号 |
| `agent_id` | 智能体编号 |
| `tenant_id` | 租户编号，当前可为空 |
| `scene_code` | 调用场景 |
| `call_mode` | `BLOCK` 或 `STREAM` |
| `status` | `RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED` |
| `provider` | 供应商标识 |
| `model` | 模型标识 |
| `start_time` | 开始时间 |
| `end_time` | 结束时间 |
| `duration_ms` | 耗时毫秒 |
| `input_tokens` | 输入 Token 数 |
| `output_tokens` | 输出 Token 数 |
| `total_tokens` | 总 Token 数 |
| `error_code` | 网关错误码 |
| `error_message` | 脱敏错误摘要 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

### 7.2 model_gateway_attempt

记录一次真实 Provider 调用尝试。

| 字段 | 说明 |
| --- | --- |
| `id` | 自增主键 |
| `attempt_id` | 尝试编号，唯一 |
| `invocation_id` | 关联网关调用编号 |
| `attempt_no` | 第几次尝试，阶段 2 固定为 1 |
| `provider` | 供应商标识 |
| `model` | 模型标识 |
| `status` | `RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED` |
| `start_time` | 开始时间 |
| `end_time` | 结束时间 |
| `duration_ms` | 耗时毫秒 |
| `http_status` | Provider HTTP 状态码，无法获取时为空 |
| `error_code` | 网关错误码 |
| `error_message` | 脱敏错误摘要 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

阶段 2 单模型固定路由通常每个 Invocation 只有一个 Attempt，但提前拆分表结构可以避免阶段 3 增加重试和降级时重构数据模型。

## 8. 错误语义

阶段 2 使用阶段 1 已定义的 `ModelGatewayErrorCode`。

建议映射：

- 参数缺失、非法模式、空 Prompt：`INVALID_REQUEST`
- 上下文超长：`CONTEXT_TOO_LONG`
- 调用超时：`PROVIDER_TIMEOUT`
- 429 或资源限制：`RATE_LIMITED`
- 401 / 403：`AUTHENTICATION_FAILED`
- 5xx、连接失败、DNS、连接重置：`PROVIDER_UNAVAILABLE`
- 空响应、响应结构不符合预期：`RESPONSE_INVALID`
- 订阅取消、客户端断连：`CALL_CANCELLED`

错误日志只记录 `invocationId`、`runId`、`traceId`、`sceneCode`、`provider`、`model`、错误码和脱敏错误摘要。

## 9. 观测设计

### 9.1 Trace

每次网关调用创建 Span 或在当前 Span 上添加属性。

建议属性：

- `model_gateway.invocation_id`
- `model_gateway.scene_code`
- `model_gateway.call_mode`
- `model_gateway.provider`
- `model_gateway.model`
- `model_gateway.status`
- `model_gateway.error_code`
- `model_gateway.input_tokens`
- `model_gateway.output_tokens`
- `model_gateway.total_tokens`

不得写入完整 Prompt、完整响应、apiKey、proxyPassword。

### 9.2 Metrics

首批指标：

- `model_gateway_invocations_total`
- `model_gateway_invocation_duration_seconds`
- `model_gateway_tokens_total`
- `model_gateway_errors_total`

标签只允许低基数字段，例如 `scene_code`、`provider`、`model`、`status`、`error_code`。不得把 `runId`、`traceId`、`invocationId` 放入指标标签。

## 10. 配置设计

新增配置建议：

```yaml
model:
  gateway:
    default-timeout: ${MODEL_GATEWAY_DEFAULT_TIMEOUT:30s}
    persistence-enabled: ${MODEL_GATEWAY_PERSISTENCE_ENABLED:true}
    metrics-enabled: ${MODEL_GATEWAY_METRICS_ENABLED:true}
```

不提供 `MODEL_GATEWAY_ENABLED=false` 之类的业务直连回退开关。若网关内部持久化失败，应记录中文警告并继续主调用；若 Provider 调用失败，应返回结构化网关错误。

## 11. 生产调用点迁移范围

阶段 2 需要覆盖以下已知生产调用点：

- `IntentRecognitionNode`
- `EvidenceRecallNode`
- `QueryEnhanceNode`
- `FeasibilityAssessmentNode`
- `PlannerNode`
- `Nl2SqlServiceImpl`
- `SqlExecuteNode` 中图表分析调用
- `PythonGenerateNode`
- `PythonAnalyzeNode`
- `ReportGeneratorNode`
- `ClarificationNormalizeNode`
- `JsonParseUtil`
- `HumanFeedbackIntentService`
- `SessionTitleService`
- `AiChunkNameGenerator`
- `AiSimulatedExecutor`

如果实施时发现新的 `LlmService` 生产调用点，也必须纳入阶段 2，除非确认只属于测试代码。

## 12. 回滚策略

阶段 2 不允许业务节点恢复直连旧 `ChatClient`。可接受的回滚方式是：

1. 回滚阶段 2 分支提交。
2. 或在网关内部保持固定单模型 Provider 行为，关闭非关键持久化和指标能力。
3. 或临时关闭调用明细持久化，保留统一模型出口。

这样可以避免切流后重新引入“双出口”。

## 13. 测试策略

1. 单元测试：`GatewayBackedLlmService` 能把三种旧调用和显式场景调用转换为正确 `ModelGatewayRequest`。
2. 单元测试：`DefaultModelGateway` 成功、失败、取消时均更新 Invocation / Attempt。
3. 单元测试：持久化失败不影响模型调用结果。
4. 单元测试：Provider block 调用成功返回 `GatewayResult`。
5. 单元测试：Provider stream 调用成功返回 `GatewayChunk`，完成片段携带用量和路由信息。
6. 单元测试：Provider 超时、认证失败、限流、5xx、空响应转换为结构化错误。
7. 扫描测试：生产代码中不存在直接调用 `ChatClient.prompt()` 或 `ChatModel.call()` 的模型出口。
8. 回归测试：现有 `GraphControllerTest`、`GraphServiceTest`、`WorkflowRunServiceImplTest`、关键节点测试继续通过。
9. 隐私测试：调用明细和日志不包含完整 Prompt、完整响应、API Key、代理密码。

## 14. 验收标准

1. 所有生产模型调用经由 `ModelGateway`。
2. `LlmService` 默认 Bean 为网关适配器。
3. 主要生产调用点使用显式 `sceneCode`。
4. 模型调用成功、失败、取消均有 Invocation / Attempt 明细。
5. 持久化失败不阻断模型调用。
6. Provider 异常统一转换为 `ModelGatewayException`。
7. Trace 与 Metrics 可按场景、供应商、模型、状态聚合查看。
8. 不新增完整 Prompt、完整响应、apiKey、proxyPassword 日志或落库。
9. 阶段 2 不引入动态路由、Langfuse、预算、熔断和自动降级。

## 15. 编码约束

1. 遵守项目 `AGENTS.md`：注释和日志信息使用简体中文；关键类有 JavaDoc；关键步骤注释写清步骤；不修改无关文件。
2. 遵守《阿里巴巴 Java 开发手册》：命名清晰、边界明确、异常语义稳定、避免魔法值、避免吞异常。
3. 不在日志、Span、Metric 和数据库中保存完整 Prompt、完整响应和凭证。
4. 新增配置、表字段、错误码和场景码必须有明确中文说明。
