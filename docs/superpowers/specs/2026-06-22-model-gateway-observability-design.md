# 模型网关与全链路可观测性设计

## 1. 背景

当前项目的模型调用集中在 `LlmService`，但接口只接收原始系统消息和用户消息，无法表达调用场景、候选模型、预算、路由结果和降级路径。`AiModelRegistry` 只维护一个全局对话模型，`StreamLlmService` 内部固定 30 秒超时，`ResourceGatedLlmService` 则把资源繁忙包装为正常模型文本。这些实现可以支撑单模型调用，但不适合继续承载动态路由、熔断、成本治理和调用追踪。

项目已有工作流运行快照、资源门控、公平队列和结构化流式事件，可作为新模块的接入基础。项目路线文档同时要求追踪模型调用、向量召回、数据库查询、脚本执行和报告生成，因此模型网关与全链路观测需要一次完成整体设计，再按阶段实施。

## 2. 目标

1. 新增独立 Maven 模块 `data-agent-model-gateway`，当前随主应用同进程部署，接口边界支持未来拆分为独立服务。
2. 工作流和业务服务只提交调用场景、Prompt 信息和调用约束，不直接选择具体模型。
3. 网关统一治理超时、规则路由、预算、限流、熔断、重试和备用模型降级。
4. 使用 OpenTelemetry 建立贯穿工作流、模型、向量、SQL 和 Python 的统一追踪上下文。
5. 使用 Tempo/Grafana 查看完整链路，Prometheus/Grafana 查看趋势与告警，Langfuse 查看 LLM 专项数据。
6. 在业务数据库保留可检索的运行、路由和审计明细，同时避免复制完整 Trace 或默认保存完整 Prompt/响应。
7. 在管理端提供模型策略、网关状态、业务运行检索和外部链路跳转能力。

## 3. 非目标

1. 首期只支持 OpenAI 兼容协议，不同时建设多种厂商原生协议。
2. Prompt 治理只定义 `PromptTemplateRegistry` 接口，不实现 Nacos 监听、版本切换和回滚。
3. 路由采用可解释规则，历史指标只辅助人工调参，不自动改变模型权重。
4. 不交付生产级高可用可观测集群，也不绑定邮件、企业微信等具体通知渠道。
5. 不让网关负责工作流编排、SQL 执行、向量检索或 Python 执行本身。

## 4. 总体架构

依赖方向如下：

```text
data-agent-workflow / data-agent-service
              ↓
data-agent-model-gateway
              ↓
Provider SPI / 模型配置查询端口 / 观测端口
              ↓
OpenAI 兼容模型供应商、DAL、OpenTelemetry
```

`data-agent-model-gateway` 包含以下组件：

- `ModelGateway`：阻塞与流式调用的统一入口。
- 调用上下文：维护 trace、run、session、user、agent 和 tenant 关联信息。
- 策略流水线：校验、路由、预算、限流、熔断、调用、重试和降级。
- Provider SPI：隔离供应商协议，首期提供 OpenAI 兼容适配器。
- 配置快照：读取场景策略和模型配置，刷新失败时保留上一可用版本。
- 观测适配器：生成 Span、Metric 和脱敏业务事件。
- `LlmService` 兼容适配器：迁移期间把旧调用统一转入网关。
- `PromptTemplateRegistry`：只定义模板解析、变量校验和版本快照接口。

公共追踪上下文、通用错误码和低耦合观测接口放在 `data-agent-common`。业务模块不直接依赖具体的 OpenTelemetry、Tempo、Langfuse 或 Grafana API。

## 5. 调用协议

### 5.1 请求

`ModelGatewayRequest` 至少包含：

- `sceneCode`：如意图识别、SQL 生成、计划生成、报告生成。
- `prompt`：模板引用与变量；迁移期允许直接提交 Messages。
- `mode`：`BLOCK` 或 `STREAM`。
- `constraints`：总超时、最大输出 Token、预算上限、是否允许降级。
- `tags`：只允许低基数、非敏感业务标签。

调用上下文不要求在每个业务方法中手工传递。入口创建上下文后，通过 Reactor Context 传播，并在线程切换边界桥接 MDC。`tenantId` 在当前多租户能力尚未完成时允许为空。

### 5.2 响应

阻塞响应使用 `GatewayResult`，流式响应使用 `GatewayChunk`。两者共享以下元数据：

- `invocationId`：逻辑模型调用编号。
- 内容或流式片段。
- 输入、输出和总 Token。
- 最终供应商与模型、尝试次数、是否降级。
- 完成原因或结构化失败原因。

### 5.3 Invocation 与 Attempt

一次业务模型调用对应一个 Invocation；主模型首次调用、同模型重试和备用模型调用分别对应 Attempt。

```text
workflow.run
└─ workflow.node
   └─ model.gateway.invoke
      ├─ model.provider.attempt（主模型）
      ├─ model.provider.attempt（同模型重试）
      └─ model.provider.attempt（备用模型）
```

流式调用在订阅时申请许可并创建 Attempt。首个 Token 记录 TTFT，流完成后记录 Usage 和总耗时，异常或取消均释放许可并记录明确状态。业务明细异步批量落库，不阻塞 Token 流。

## 6. 路由与故障治理

### 6.1 场景策略

每个 `sceneCode` 绑定候选模型、优先级、最大上下文、总超时、单次超时、最大重试次数、是否允许降级、Token 上限和预算规则。候选模型先按启用状态、上下文容量和预算过滤，再按显式优先级与成本规则排序。

首期策略保存在业务数据库，并在网关中维护不可变只读快照。配置刷新失败时继续使用上一可用版本，禁止错误配置击穿全部工作流。

### 6.2 时间约束

时间约束必须满足：

```text
工作流总截止时间 > Invocation 总截止时间 > Attempt 单次超时
```

每次重试根据剩余时间重新计算，不允许多次重试无限放大总耗时。流式调用额外设置首 Token 超时和流空闲超时。

### 6.3 重试与降级

以下错误允许按策略重试或降级：

- 连接失败、请求超时。
- HTTP 429、供应商 5xx。
- 当前候选模型熔断器已打开。
- 响应为空或协议解析失败，且场景明确允许。

以下错误禁止盲目重试：

- 鉴权失败、参数非法、上下文超限。
- 预算或配额硬限制。
- 用户取消、工作流总超时。
- 流式响应已经输出首个 Token 后发生的异常。

流式响应输出首个 Token 后不得自动切换模型，避免把不同模型的内容拼接为一个答案。此时返回部分输出状态和结构化错误，由工作流决定如何解释。

### 6.4 限流与熔断

现有 ResourceGate 能力迁移到网关策略体系，并扩展用户、智能体、场景、供应商和模型等维度。熔断粒度为 `provider + model`。

资源繁忙、配额不足和熔断不再伪装成正常模型文本，而是返回结构化错误。工作流根据错误类型决定排队、提示、降级或终止。

### 6.5 成本

模型配置维护输入 Token 单价、输出 Token 单价和币种。调用前按估算 Token 做软预算预检，调用后根据实际 Usage 记账。首期执行单次调用与场景预算，用户、智能体和租户的周期配额在后续阶段启用。

## 7. 全链路可观测性

### 7.1 统一语义

OpenTelemetry 是唯一埋点与上下文传播标准。核心 Span 包括：

- `workflow.run`
- `workflow.node`
- `model.gateway.invoke`
- `model.provider.attempt`
- `vector.search`
- `db.query`
- `python.execute`

Span 属性遵循 OpenTelemetry GenAI 语义约定，并补充项目命名空间属性。所有自定义属性需要集中定义，禁止节点自行拼接不同命名。

### 7.2 数据分流

OTel Collector 负责统一接收并分发：

- Metrics → Prometheus → Grafana。
- 全链路 Traces → Tempo → Grafana。
- LLM 相关 Traces → Langfuse。

Langfuse 不替代通用可观测体系。它作为可选 Docker Profile，提供 Prompt/响应脱敏采样、Generation、Token、成本、会话、模型对比和后续评测能力。应用与 Langfuse 之间只通过 OpenTelemetry/OTLP 语义耦合。

### 7.3 指标

首批指标覆盖：

- 工作流和节点成功率、耗时分布、失败节点。
- 模型调用量、成功率、P50/P95/P99、TTFT。
- 输入/输出 Token、估算成本和实际成本。
- 重试次数、降级率、熔断状态和配额拒绝。
- 资源排队时长和当前占用。
- SQL 耗时、返回行数和安全拦截原因。
- 向量召回耗时、命中数量和持续为空次数。
- Python 执行耗时、失败类型和沙箱可用性。

指标标签只允许 `sceneCode`、`nodeType`、`provider`、`model`、`status`、`errorType` 和 `degraded` 等低基数维度。`traceId`、`runId`、`sessionId`、`userId`、`agentId`、SQL、Prompt 和异常原文不得作为指标标签。

### 7.4 隐私与采样

1. 默认只发送长度、Token、哈希、状态和脱敏摘要。
2. 仅对失败请求或配置比例的样本保留脱敏 Prompt/响应，并设置独立保留期。
3. API Key、密码、访问 Token 和个人敏感字段不得进入 Span、Metric 或 Baggage。
4. 脱敏在应用侧统一完成，再交给不同 exporter，避免只在某个后端遮罩。
5. 观测写入失败只触发内部告警，不影响主业务调用。

## 8. 业务数据模型

### 8.1 扩展 `chat_workflow_run`

新增 `run_id`、`trace_id`、开始时间、结束时间、耗时和失败节点等字段。`sessionId` 继续表示会话，`runId` 唯一表示会话中的一次分析执行。

### 8.2 新增 `workflow_node_run`

记录节点运行编号、runId、节点类型、开始与结束时间、耗时、状态、重试次数、输入/输出脱敏摘要或哈希、错误类型和脱敏 detail JSON。

### 8.3 新增 `model_invocation`

记录 invocationId、runId、nodeRunId、traceId、sceneCode、最终供应商与模型、Token、成本、TTFT、总耗时、尝试次数、降级标记、输入/输出哈希和最终错误类型。

### 8.4 新增 `model_attempt`

记录 invocationId、尝试序号、供应商、模型、开始与结束时间、耗时、状态、错误类型、重试原因、熔断状态和 Usage。

业务表只保存审计与运营所需字段，不保存完整 Trace，也不默认保存完整 Prompt 和响应。明细写入使用有界异步队列和批量提交；队列满时降级为指标计数并记录告警，不能无限占用内存。

## 9. 管理端

管理端新增以下能力：

1. 模型与场景策略：配置价格、上下文容量、场景候选、优先级、超时、重试、降级和预算。
2. 网关运行状态：查看候选模型健康度、当前并发、配额、熔断状态和最近错误，支持受权限控制的手动恢复。
3. 业务运行检索：按时间、状态、场景、模型和智能体查询运行、节点与调用明细。
4. 链路跳转：通过 traceId 跳转 Grafana/Tempo，通过 invocationId 跳转 Langfuse。

项目内页面聚焦业务运营和配置，不重复建设 Grafana 与 Langfuse 已具备的通用分析界面。

## 10. 看板与告警

首批 Grafana 看板包括：

- 工作流总览。
- 节点性能。
- 模型网关。
- Token 与成本。
- 失败、重试与降级。
- 资源队列。

首批告警规则包括：

- 模型错误率或 P95 超过阈值。
- 熔断器开启或降级率突升。
- 脚本沙箱不可用。
- 数据源连接连续失败。
- 向量召回持续为空。

首期只交付 Prometheus/Grafana 规则，不绑定具体通知渠道。

## 11. 分阶段实施

### 阶段 1：观测与协议地基

- 新建 `data-agent-model-gateway`。
- 定义 Gateway API、调用上下文、统一错误和 Prompt Registry 接口。
- 建立 traceId、runId 和 Reactor Context 传播。
- 接入 OTel 基础配置。
- 提供 OTel Collector、Tempo、Prometheus 和 Grafana 本地 Docker 编排。

### 阶段 2：单模型网关切流

- 实现 OpenAI 兼容 Provider。
- 实现 `LlmService` 兼容适配器。
- 建立 Invocation/Attempt 生命周期、超时和业务明细。
- 保证所有生产模型调用只有一个真实出口。

### 阶段 3：多模型治理

- 实现场景策略、候选模型和规则路由。
- 实现预算、分层限流、熔断、可控重试和备用模型降级。
- 提供模型策略和网关状态管理页面。

### 阶段 4：全链路与运营能力

- 为工作流节点、向量、SQL 和 Python 建立 Span 与指标。
- 提供 Grafana 看板和告警规则。
- 提供业务运行检索和链路跳转页面。
- 增加 Langfuse 可选 Docker Profile 和 Collector exporter。

### 阶段 5：迁移收口与硬化

- 所有调用点改为显式 sceneCode。
- 移除绕过网关的旧模型出口。
- 完成压力测试、故障演练、隐私审计和回滚验证。
- 补齐部署、使用和排障文档。

## 12. 测试策略

1. 单元测试：候选过滤与排序、总截止时间、预算、错误分类、脱敏和指标标签约束。
2. 契约测试：阻塞/流式调用、Usage、取消、首 Token 超时和旧 `LlmService` 适配器。
3. 集成测试：模拟 429、5xx、连接超时、空响应和流中断，验证重试、熔断、降级与许可释放。
4. 观测测试：使用内存 exporter 校验 Span 父子关系、属性、状态和敏感字段缺失。
5. 持久化降级测试：业务明细写入失败或队列满时，验证主调用不受影响。
6. 环境测试：冒烟检查 Collector、Tempo、Prometheus 和 Grafana；Langfuse Profile 单独验证。
7. 压力与故障演练：并发流式调用、下游慢响应、供应商全故障和观测后端不可用。

## 13. 验收标准

1. 任意工作流可通过 traceId 还原节点、模型、SQL、向量和脚本完整链路。
2. 不同场景按规则选择模型；主模型故障时按策略降级，并保留降级路径。
3. 能查看成功率、P95、TTFT、Token、成本、失败节点、重试与降级趋势。
4. 默认观测数据不包含密钥或未脱敏业务内容，高基数 ID 不进入指标标签。
5. 关闭或故障任一观测后端，不影响主工作流正确执行。
6. 不存在绕过网关直接访问 `ChatClient` 或 `ChatModel` 的生产调用路径。

## 14. 风险与回滚

- 网关策略错误可能放大模型故障。配置采用校验后的不可变快照，刷新失败保留上一版本。
- 重试可能放大下游压力。限制总截止时间、最大尝试次数，并结合熔断和配额。
- 观测数据量可能快速增长。采用采样、保留期、低基数指标和有界异步队列。
- Langfuse 自托管资源占用较高。默认作为可选 Profile，不作为主应用启动前置条件。
- 迁移期间存在双出口风险。旧 `LlmService` 只能作为网关适配器，禁止继续直接构建真实模型调用。
- 每个阶段保留独立开关。网关切流异常时可回退到网关内的单模型固定路由，但不恢复业务节点直连模型。

## 15. 编码约束

实施时遵循项目 `AGENTS.md`：代码注释和日志使用简体中文；每个类通过 JavaDoc 说明职责；关键方法按步骤添加中文注释；只修改当前阶段涉及的文件，不顺手整理无关注释、日志或中文信息。

## 16. 参考资料

- [OpenTelemetry GenAI 语义约定](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- [Langfuse OpenTelemetry 集成](https://langfuse.com/integrations/native/opentelemetry)
- [Langfuse Metrics](https://langfuse.com/docs/metrics/overview)
- [Langfuse Docker Compose 自托管](https://langfuse.com/self-hosting/deployment/docker-compose)
