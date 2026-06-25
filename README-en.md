# DataAgentPlus

[中文](README.md) | English

DataAgentPlus is an intelligent data analysis agent built on the Spring AI Alibaba ecosystem. It recreates and modularly refactors the core capabilities of [spring-ai-alibaba/DataAgent](https://github.com/spring-ai-alibaba/DataAgent), then extends them with stronger engineering boundaries, data security, knowledge governance, workflow admission control, Python sandboxing, model gateway foundations, and observability.

> Note: this repository is not a direct mirror of the upstream DataAgent project. It is an independent reconstruction and extension based on the original product ideas and workflow capabilities.

## What It Does

DataAgentPlus helps users ask data questions in natural language and receive SQL-backed analysis, charts, Python-based insights, and reports.

Main workflow:

```text
Natural language question -> Intent recognition -> Schema/knowledge recall
-> SQL generation and safety audit -> SQL execution -> Chart recommendation
-> Python analysis -> Report generation -> Knowledge accumulation
```

## Key Features

| Feature | Description |
| --- | --- |
| Text-to-SQL workflow | Multi-node workflow powered by Spring AI Alibaba Graph with SSE streaming. |
| Multi-database support | MySQL and PostgreSQL business data sources with dialect-aware SQL handling. |
| SQL safety audit | Read-only validation, unauthorized table/column blocking, large-query checks, and sensitive data masking. |
| Agent-level permissions | Each agent can bind data sources, selected tables, and allowed analytic columns. |
| Knowledge and memory | Business knowledge, agent knowledge, candidate knowledge, and clarification memory design. |
| Python sandbox | GraalPy and Docker-based Python execution strategies with production isolation in mind. |
| Charts and reports | SQL results can feed chart recommendations, Python analysis, and report generation. |
| Queue and resource gating | Workflow admission queue, resource limits, and queue position feedback. |
| Model gateway foundation | Provider-neutral model gateway protocol, execution context, and Prompt Registry contracts. |
| Observability | Micrometer Tracing, OTLP, Prometheus, plus local Grafana/Tempo/Prometheus compose stack. |

## Extensions Beyond the Original DataAgent

- Multi-module Maven architecture.
- Stronger SQL and data-permission boundaries.
- Workflow lifecycle hardening: SSE cleanup, run snapshots, admission queue, and resource gates.
- Knowledge governance: async knowledge jobs, chunk workbench, candidate review, and memory accumulation.
- Safer execution: Docker Python sandbox and restricted GraalPy host access.
- Observability: runId/traceId propagation, metrics, dashboards, and local tracing stack.
- React/Vite frontend for data center, knowledge center, chat tasks, and workflow progress.

## Tech Stack

| Layer | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.x, Spring MVC, Reactor, Spring AI, Spring AI Alibaba Graph |
| Data access | MyBatis-Plus, Druid, MySQL, PostgreSQL |
| AI and retrieval | OpenAI-compatible API, Milvus VectorStore, Elasticsearch reserved |
| Sandbox | Docker Java, GraalVM Polyglot / GraalPy |
| Messaging and storage | RocketMQ, MinIO |
| Observability | Micrometer Tracing, OpenTelemetry, Prometheus, Grafana, Tempo |
| Frontend | React 19, Vite, TypeScript, ECharts, Zustand, Radix UI |

## Repository Layout

```text
DataAgentPlus
├── data-agent-common          # Shared constants, exceptions, configuration, DTOs
├── data-agent-dal             # Entities, mappers, database access, SQL dialects
├── data-agent-ai-core         # Model registry, LLM service, prompts, vector retrieval, schema service
├── data-agent-model-gateway   # Gateway protocol, errors, context propagation, Prompt Registry contracts
├── data-agent-workflow        # Graph workflow nodes, dispatching, resource gates, run records
├── data-agent-service         # Business services
├── data-agent-start           # Spring Boot application, controllers, configuration, SQL scripts
├── data-agent-ui              # React frontend
├── docker                     # Python sandbox and observability compose files
└── docs                       # Architecture notes, implementation plans, and extension designs
```

## Quick Start

### Requirements

- JDK 21+
- Maven 3.9+
- Node.js 20+ or a compatible version for the frontend dependencies
- MySQL 8.x
- Docker Desktop or Docker Engine
- Milvus, MinIO, and RocketMQ as needed

### 1. Initialize MySQL

```sql
CREATE DATABASE data_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -u root -p data_agent < data-agent-start/src/main/resources/sql/schema.sql
```

Optional migration for workflow run identity and tracing fields:

```bash
mysql -u root -p data_agent < data-agent-start/src/main/resources/sql/migration/V20260623_01__workflow_run_trace_identity.sql
```

### 2. Configure Environment Variables

```bash
export OPENAI_API_KEY="your-api-key"
export OPENAI_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode"
export OPENAI_MODEL="qwen-plus"
```

Common variables:

| Variable | Default | Description |
| --- | --- | --- |
| `OPENAI_API_KEY` | `sk-placeholder` | OpenAI-compatible API key |
| `OPENAI_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode` | OpenAI-compatible base URL |
| `OPENAI_MODEL` | `qwen-plus` | Default chat model |
| `MILVUS_HOST` | `localhost` | Milvus host |
| `MILVUS_PORT` | `19530` | Milvus port |
| `MINIO_ENDPOINT` | `http://127.0.0.1:9000` | MinIO endpoint |
| `ROCKETMQ_NAME_SERVER` | `118.195.146.161:9876` | RocketMQ NameServer |
| `CHAT_WORKFLOW_QUEUE_REDIS_ENABLED` | `false` | Enable Redis-backed workflow queue |

### 3. Start Backend

```bash
mvn -pl data-agent-start -am spring-boot:run
```

Backend URL:

```text
http://localhost:18080
```

Health check:

```text
http://localhost:18080/actuator/health
```

### 4. Start Frontend

```bash
cd data-agent-ui
npm install
npm run dev
```

Default Vite URL is usually:

```text
http://localhost:5173
```

### 5. Optional Observability Stack

```bash
docker compose -f docker/observability/docker-compose.yml up -d
```

| Component | URL |
| --- | --- |
| Grafana | `http://localhost:3000` |
| Prometheus | `http://localhost:9090` |
| Tempo | `http://localhost:3200` |
| Application metrics | `http://localhost:18080/actuator/prometheus` |

See [Model Gateway Observability Guide](docs/model_gateway_observability_guide.md) for details.

## Documentation

| Document | Description |
| --- | --- |
| [Implementation Plan](docs/implementation_plan.md) | Reconstruction plan, architecture choices, and phased roadmap. |
| [SQL Executor Guide](docs/sql_executor_guide.md) | SQL execution, connection pool, dialects, and safety boundaries. |
| [Knowledge Memory Architecture](docs/knowledge_memory_architecture.md) | Clarification, candidate knowledge, formal knowledge, and review flow. |
| [Knowledge Memory Implementation Plan](docs/knowledge_memory_implementation_plan.md) | Implementation tasks for memory and knowledge review. |
| [Workflow Queue Plan](docs/chat_workflow_rate_limit_queue_plan.md) | Admission queue, resource gating, Redis scheduling, and observability. |
| [Model Gateway Observability Guide](docs/model_gateway_observability_guide.md) | Gateway context, OpenTelemetry, Prometheus, and Grafana usage. |
| [Superpowers Designs and Plans](docs/superpowers) | Extended feature designs, including field permissions, query safety, Python sandbox, and knowledge workbench. |

## Current Boundaries

- Elasticsearch dependencies are reserved, but Elasticsearch VectorStore auto-configuration is disabled by default. Milvus is the primary vector retrieval path.
- Model gateway phase 1 provides protocol, context propagation, and observability foundations. Dynamic routing, circuit breaking, and cost governance are planned for later phases.
- RocketMQ, MinIO, Milvus, and Docker sandboxing depend on external services and can be enabled incrementally during local development.
- Some historical planning documents may have encoding display issues, but they still serve as implementation context.

## Development

Run backend tests:

```bash
mvn test
```

Run tests for the start module and dependencies:

```bash
mvn -pl data-agent-start -am test
```

Build frontend:

```bash
cd data-agent-ui
npm run build
```

## License

Please add an explicit `LICENSE` file before formal release. This project references and extends ideas from [spring-ai-alibaba/DataAgent](https://github.com/spring-ai-alibaba/DataAgent); make sure the final distribution complies with the upstream license.
