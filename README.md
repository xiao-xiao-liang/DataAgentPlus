# DataAgentPlus

中文 | [English](README-en.md)

DataAgentPlus 是一个基于 Spring AI Alibaba 生态的智能数据分析 Agent。项目在复刻与重构 [spring-ai-alibaba/DataAgent](https://github.com/spring-ai-alibaba/DataAgent) 原有能力的基础上，进一步强化了工程分层、数据安全、知识沉淀、队列限流、Python 沙箱、模型网关与可观测性等能力。

> 说明：本仓库不是上游 DataAgent 的直接镜像，而是以其核心产品形态和工作流能力为参考进行的复刻、模块化重构与功能扩展。上游项目的官方说明请参考 [spring-ai-alibaba/DataAgent](https://github.com/spring-ai-alibaba/DataAgent)。

## 项目定位

DataAgentPlus 面向企业级自然语言数据分析场景，目标是让用户用自然语言完成数据查询、分析、解释和报告生成。

核心链路：

```text
自然语言问题 -> 意图识别 -> Schema/知识召回 -> SQL 生成与安全审计
-> SQL 执行 -> 图表推荐 -> Python 深度分析 -> 报告生成 -> 知识沉淀
```

## 核心能力

| 能力 | 说明 |
| --- | --- |
| Text-to-SQL 工作流 | 基于 Spring AI Alibaba Graph 编排多节点分析流程，支持流式 SSE 输出。 |
| 多数据源分析 | 支持 MySQL、PostgreSQL 业务数据源接入与方言级 SQL 处理。 |
| SQL 安全审计 | 基于 Druid AST 做只读 SQL 校验、越权表/字段拦截、大表查询风险控制和敏感字段脱敏。 |
| 智能体字段权限 | 每个智能体可绑定数据源、选择表与字段，SQL 执行前执行硬权限校验。 |
| 知识与记忆沉淀 | 支持业务知识、智能体知识、候选知识和澄清记忆的设计与部分实现。 |
| Python 分析沙箱 | 支持 GraalPy 与 Docker Python 执行策略，生产环境优先使用容器隔离。 |
| 图表与报告 | SQL 结果可进入图表推荐、Python 分析和报告生成链路。 |
| 队列与资源门控 | 支持工作流准入排队、资源限流和用户侧排队位次展示。 |
| 模型网关基础 | 新增供应商无关的模型网关协议、执行上下文和 Prompt Registry 契约。 |
| 可观测性 | 接入 Micrometer Tracing、OTLP、Prometheus，并提供本地 Grafana/Tempo/Prometheus 编排。 |

## 相比原 DataAgent 的扩展方向

- 多模块 Maven 重构：拆分 `common`、`dal`、`ai-core`、`model-gateway`、`workflow`、`service`、`start`。
- 数据安全加强：SQL 方言审计、只读约束、表/字段权限、敏感数据脱敏、最大返回行控制。
- 工作流稳定性：SSE 生命周期治理、断连清理、运行快照、排队准入、资源门控。
- 知识治理增强：知识文档异步任务、分块工作台、候选知识审核、澄清后沉淀为业务知识。
- 执行隔离：Python Docker 沙箱、GraalPy HostAccess 收敛、本地执行降级开关。
- 运维观测：模型网关上下文、traceId/runId 贯穿、Prometheus 指标、本地观测栈。
- 前端重构：基于 React/Vite 的数据中心、知识中心、聊天任务和工作流过程展示。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21, Spring Boot 3.5.x, Spring MVC, Reactor, Spring AI, Spring AI Alibaba Graph |
| 数据访问 | MyBatis-Plus, Druid, MySQL, PostgreSQL |
| AI 与检索 | OpenAI-compatible API, Milvus VectorStore, Elasticsearch 预留 |
| 沙箱 | Docker Java, GraalVM Polyglot / GraalPy |
| 消息与存储 | RocketMQ, MinIO |
| 可观测性 | Micrometer Tracing, OpenTelemetry, Prometheus, Grafana, Tempo |
| 前端 | React 19, Vite, TypeScript, ECharts, Zustand, Radix UI |

## 模块结构

```text
DataAgentPlus
├── data-agent-common          # 通用常量、异常、配置、DTO
├── data-agent-dal             # 实体、Mapper、数据库连接与 SQL 方言
├── data-agent-ai-core         # 模型注册、LLM 服务、Prompt、向量检索、Schema 服务
├── data-agent-model-gateway   # 模型网关协议、错误语义、执行上下文、Prompt Registry 契约
├── data-agent-workflow        # Graph 工作流节点、调度、资源门控、运行记录
├── data-agent-service         # 业务服务层
├── data-agent-start           # Spring Boot 启动模块、Controller、配置与 SQL 脚本
├── data-agent-ui              # React 前端
├── docker                     # Python 沙箱与可观测性编排
└── docs                       # 架构设计、实施计划、扩展能力文档
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 20+ 或兼容当前前端依赖的版本
- MySQL 8.x
- Docker Desktop 或 Docker Engine
- Milvus、MinIO、RocketMQ 按需启动

### 1. 初始化数据库

创建 MySQL 数据库，例如：

```sql
CREATE DATABASE data_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

导入基础表结构：

```bash
mysql -u root -p data_agent < data-agent-start/src/main/resources/sql/schema.sql
```

如需启用模型网关观测阶段的运行身份字段，请按环境评估后执行：

```bash
mysql -u root -p data_agent < data-agent-start/src/main/resources/sql/migration/V20260623_01__workflow_run_trace_identity.sql
```

### 2. 配置环境变量

最小启动配置示例：

```bash
export OPENAI_API_KEY="your-api-key"
export OPENAI_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode"
export OPENAI_MODEL="qwen-plus"
```

常用配置项：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OPENAI_API_KEY` | `sk-placeholder` | OpenAI 兼容模型 API Key |
| `OPENAI_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode` | OpenAI 兼容接口地址 |
| `OPENAI_MODEL` | `qwen-plus` | 默认对话模型 |
| `MILVUS_HOST` | `localhost` | Milvus 地址 |
| `MILVUS_PORT` | `19530` | Milvus 端口 |
| `MINIO_ENDPOINT` | `http://127.0.0.1:9000` | MinIO 地址 |
| `ROCKETMQ_NAME_SERVER` | `118.195.146.161:9876` | RocketMQ NameServer |
| `CHAT_WORKFLOW_QUEUE_REDIS_ENABLED` | `false` | 是否启用 Redis 工作流队列 |

### 3. 启动后端

```bash
mvn -pl data-agent-start -am spring-boot:run
```

默认端口：

```text
http://localhost:18080
```

健康检查：

```text
http://localhost:18080/actuator/health
```

### 4. 启动前端

```bash
cd data-agent-ui
npm install
npm run dev
```

Vite 会输出本地访问地址，通常是：

```text
http://localhost:5173
```

### 5. 启动本地可观测栈（可选）

```bash
docker compose -f docker/observability/docker-compose.yml up -d
```

常用地址：

| 组件 | 地址 |
| --- | --- |
| Grafana | `http://localhost:3000` |
| Prometheus | `http://localhost:9090` |
| Tempo | `http://localhost:3200` |
| 应用 Prometheus 指标 | `http://localhost:18080/actuator/prometheus` |

更多说明见 [模型网关观测指南](docs/model_gateway_observability_guide.md)。

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [DataAgent 复刻实施计划](docs/implementation_plan.md) | 项目复刻、重构路线、技术选型和阶段拆解。 |
| [SQL 执行器指南](docs/sql_executor_guide.md) | SQL 执行、连接池、方言、安全边界等说明。 |
| [SQL 执行器分步实现](docs/sql_executor_step_by_step.md) | SQL 执行器实现步骤与细节。 |
| [知识库与记忆沉淀架构](docs/knowledge_memory_architecture.md) | 澄清、候选知识、正式知识和审核发布链路。 |
| [知识记忆实施计划](docs/knowledge_memory_implementation_plan.md) | 知识候选、澄清恢复、前端卡片和审核 UI 的实施任务。 |
| [分析队列与限流计划](docs/chat_workflow_rate_limit_queue_plan.md) | 工作流排队、资源门控、Redis 公平调度和观测规划。 |
| [模型网关观测指南](docs/model_gateway_observability_guide.md) | 模型网关上下文、OpenTelemetry、Prometheus、Grafana 使用说明。 |
| [阶段 3 文档](docs/phase3_step1_shared_foundation.md) | AI 基础设施、模型注册、Prompt SPI、向量检索封装。 |
| [阶段 4 文档](docs/phase4_step1a_statekey_texttype.md) | 核心工作流状态、DTO、工具类、Prompt、节点和图装配。 |
| [Superpowers 设计与计划](docs/superpowers) | 后续功能增强的设计稿和实施计划，包括字段权限、SQL 安全、Python 沙箱、知识工作台等。 |

## 当前边界

- Elasticsearch 相关依赖已预留，但当前默认排除了 Elasticsearch VectorStore 自动配置，主检索链路以 Milvus 为主。
- 模型网关阶段 1 已落地调用协议、执行上下文和观测基础，完整动态路由、熔断、成本治理仍在后续阶段。
- RocketMQ、MinIO、Milvus、Docker 沙箱等能力依赖外部组件；本地开发可按功能逐步启用。
- 项目中部分历史规划文档存在编码显示问题，但仍可作为实现上下文参考；README 只收敛当前主要能力和入口。

## 开发与验证

运行后端相关测试：

```bash
mvn test
```

运行指定模块测试：

```bash
mvn -pl data-agent-start -am test
```

前端构建：

```bash
cd data-agent-ui
npm run build
```

## 许可证

本项目基于开源项目能力进行复刻重构与扩展。请在正式发布前补充本仓库明确的 License 文件，并遵守上游 [spring-ai-alibaba/DataAgent](https://github.com/spring-ai-alibaba/DataAgent) 的开源许可要求。
