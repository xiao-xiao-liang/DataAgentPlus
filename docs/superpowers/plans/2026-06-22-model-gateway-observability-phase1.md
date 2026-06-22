# 模型网关与全链路可观测性阶段 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立独立模型网关模块、统一执行上下文和 OpenTelemetry 本地观测基础设施，为后续模型切流与全链路埋点提供稳定地基。

**Architecture:** 新模块只定义与供应商无关的调用协议、错误语义、Prompt Registry 接口和 Reactor 上下文，不在本阶段调用真实模型。工作流入口生成 `runId`，关联当前 Trace，并通过 Reactor Context 传入图执行；运行快照改为按 `runId` 更新。Spring Boot Actuator 与 Micrometer Tracing 负责 OTLP 导出，本地 Docker 提供 Collector、Tempo、Prometheus 和 Grafana。

**Tech Stack:** Java 21、Spring Boot 3.5、Reactor、Micrometer Tracing、OpenTelemetry OTLP、MyBatis-Plus、MySQL、Docker Compose、Tempo、Prometheus、Grafana、JUnit 5、Mockito、AssertJ。

---

## 范围说明

本计划只实施设计文档的阶段 1。阶段 2 的单模型切流、阶段 3 的动态路由与熔断、阶段 4 的完整节点埋点与 Langfuse、阶段 5 的迁移收口，分别在前一阶段验收后生成独立计划。这样可以避免后续计划依赖尚未落地的接口细节。

所有 Java 类必须有中文 JavaDoc；注释、日志使用简体中文；关键方法按步骤注释；同时遵守《阿里巴巴 Java 开发手册》。禁止顺手修改无关文件。

## 文件结构

### 新模块

- `data-agent-model-gateway/pom.xml`：模型网关模块依赖。
- `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/`：调用请求、响应和网关接口。
- `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/context/`：执行上下文、工厂和 Reactor Context 工具。
- `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/error/`：结构化网关错误。
- `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/prompt/`：Prompt Registry 契约。

### 现有模块

- `data-agent-dal`：扩展工作流运行实体。
- `data-agent-workflow`：运行服务按 `runId` 持久化，图执行读取 Reactor Context。
- `data-agent-start`：创建上下文、接入 Micrometer/OTLP、提供配置和 Docker 运行入口。
- `docker/observability`：Collector、Tempo、Prometheus、Grafana 配置。

---

### Task 1: 创建独立模型网关 Maven 模块

**Files:**
- Modify: `pom.xml`
- Create: `data-agent-model-gateway/pom.xml`

- [ ] **Step 1: 在根 POM 注册模块和内部依赖版本**

在 `<modules>` 中把新模块放在 `data-agent-ai-core` 后、`data-agent-workflow` 前：

```xml
<module>data-agent-ai-core</module>
<module>data-agent-model-gateway</module>
<module>data-agent-workflow</module>
```

在内部模块 `dependencyManagement` 中增加：

```xml
<dependency>
    <groupId>com.liang.data.agent</groupId>
    <artifactId>data-agent-model-gateway</artifactId>
    <version>${revision}</version>
</dependency>
```

- [ ] **Step 2: 创建模块 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.liang.data.agent</groupId>
        <artifactId>liang-data-agent</artifactId>
        <version>${revision}</version>
    </parent>
    <artifactId>data-agent-model-gateway</artifactId>
    <name>Data Agent Model Gateway</name>
    <description>模型调用协议、上下文与治理能力</description>
    <dependencies>
        <dependency>
            <groupId>com.liang.data.agent</groupId>
            <artifactId>data-agent-common</artifactId>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 验证 Maven Reactor 能识别空模块**

Run: `mvn -pl data-agent-model-gateway -am -DskipTests package`

Expected: `data-agent-model-gateway` 与依赖模块均为 `SUCCESS`。

- [ ] **Step 4: 提交模块骨架**

```powershell
git add pom.xml data-agent-model-gateway/pom.xml
git commit -m "feat(model-gateway): 创建模型网关独立模块"
```

---

### Task 2: 定义供应商无关的网关调用协议

**Files:**
- Create: `data-agent-model-gateway/src/test/java/com/liang/data/agent/gateway/api/ModelGatewayRequestTest.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelCallMode.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelMessageRole.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelMessage.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelPrompt.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/GatewayConstraints.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelGatewayRequest.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelUsage.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelRoute.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/GatewayResult.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/GatewayChunk.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelGateway.java`

- [ ] **Step 1: 编写请求不可变性与校验测试**

```java
class ModelGatewayRequestTest {

    @Test
    void shouldCopyMessagesAndTags() {
        List<ModelMessage> messages = new ArrayList<>();
        messages.add(new ModelMessage(ModelMessageRole.USER, "查询销售额"));
        Map<String, String> tags = new HashMap<>();
        tags.put("feature", "nl2sql");

        ModelGatewayRequest request = new ModelGatewayRequest(
                "SQL_GENERATION",
                ModelPrompt.direct(messages),
                ModelCallMode.BLOCK,
                GatewayConstraints.defaults(),
                tags
        );
        messages.clear();
        tags.clear();

        assertThat(request.prompt().messages()).hasSize(1);
        assertThat(request.tags()).containsEntry("feature", "nl2sql");
    }

    @Test
    void shouldRejectBlankSceneCode() {
        assertThatThrownBy(() -> new ModelGatewayRequest(
                " ", ModelPrompt.direct(List.of(new ModelMessage(ModelMessageRole.USER, "问题"))),
                ModelCallMode.BLOCK, GatewayConstraints.defaults(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("场景编码");
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -pl data-agent-model-gateway -am -Dtest=ModelGatewayRequestTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，编译器分别提示 `ModelGatewayRequest`、`ModelPrompt` 和 `GatewayConstraints` 不存在。

- [ ] **Step 3: 实现请求核心类型**

```java
/** 模型调用方式。 */
public enum ModelCallMode { BLOCK, STREAM }

/** 模型消息角色。 */
public enum ModelMessageRole { SYSTEM, USER, ASSISTANT, TOOL }

/** 模型消息。 */
public record ModelMessage(ModelMessageRole role, String content) {
    public ModelMessage {
        Objects.requireNonNull(role, "消息角色不能为空");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
    }
}

/** Prompt 来源，模板模式与直接消息模式二选一。 */
public record ModelPrompt(String templateId, Map<String, Object> variables, List<ModelMessage> messages) {
    public ModelPrompt {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        messages = messages == null ? List.of() : List.copyOf(messages);
        boolean templateMode = templateId != null && !templateId.isBlank();
        if (templateMode == !messages.isEmpty()) {
            throw new IllegalArgumentException("模板标识与直接消息必须且只能提供一种");
        }
    }

    public static ModelPrompt direct(List<ModelMessage> messages) {
        return new ModelPrompt(null, Map.of(), messages);
    }

    public static ModelPrompt template(String templateId, Map<String, Object> variables) {
        return new ModelPrompt(templateId, variables, List.of());
    }
}

/** 单次网关调用约束。 */
public record GatewayConstraints(Duration timeout, Integer maxOutputTokens,
                                 BigDecimal budgetLimit, boolean allowFallback) {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public GatewayConstraints {
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("调用超时必须大于零");
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("最大输出Token必须大于零");
        }
        if (budgetLimit != null && budgetLimit.signum() < 0) {
            throw new IllegalArgumentException("预算上限不能小于零");
        }
    }

    public static GatewayConstraints defaults() {
        return new GatewayConstraints(DEFAULT_TIMEOUT, null, null, true);
    }
}

/** 模型网关请求。 */
public record ModelGatewayRequest(String sceneCode, ModelPrompt prompt, ModelCallMode mode,
                                  GatewayConstraints constraints, Map<String, String> tags) {
    public ModelGatewayRequest {
        if (sceneCode == null || sceneCode.isBlank()) {
            throw new IllegalArgumentException("场景编码不能为空");
        }
        Objects.requireNonNull(prompt, "Prompt不能为空");
        mode = mode == null ? ModelCallMode.BLOCK : mode;
        constraints = constraints == null ? GatewayConstraints.defaults() : constraints;
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}
```

按相同不可变规则实现响应类型：

```java
/** Token 使用量。 */
public record ModelUsage(long inputTokens, long outputTokens, long totalTokens) { }

/** 最终路由信息。 */
public record ModelRoute(String provider, String model, int attemptCount, boolean degraded) { }

/** 阻塞调用结果。 */
public record GatewayResult(String invocationId, String content, ModelUsage usage,
                            ModelRoute route, String finishReason) { }

/** 流式调用片段。 */
public record GatewayChunk(String invocationId, String content, boolean finished,
                           ModelUsage usage, ModelRoute route, String finishReason) { }

/** 模型网关统一入口。 */
public interface ModelGateway {
    Mono<GatewayResult> call(ModelGatewayRequest request);
    Flux<GatewayChunk> stream(ModelGatewayRequest request);
}
```

- [ ] **Step 4: 运行协议测试**

Run: `mvn -pl data-agent-model-gateway -am -Dtest=ModelGatewayRequestTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

- [ ] **Step 5: 提交调用协议**

```powershell
git add data-agent-model-gateway/src
git commit -m "feat(model-gateway): 定义统一模型调用协议"
```

---

### Task 3: 定义结构化错误与 Prompt Registry 契约

**Files:**
- Create: `data-agent-model-gateway/src/test/java/com/liang/data/agent/gateway/error/ModelGatewayErrorCodeTest.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/error/ModelGatewayErrorCode.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/error/ModelGatewayException.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/prompt/ResolvedPrompt.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/prompt/PromptTemplateRegistry.java`

- [ ] **Step 1: 编写错误语义测试**

```java
class ModelGatewayErrorCodeTest {
    @Test
    void shouldExposeRetryAndFallbackDecision() {
        assertThat(ModelGatewayErrorCode.PROVIDER_TIMEOUT.retryable()).isTrue();
        assertThat(ModelGatewayErrorCode.PROVIDER_TIMEOUT.degradable()).isTrue();
        assertThat(ModelGatewayErrorCode.AUTHENTICATION_FAILED.retryable()).isFalse();
        assertThat(ModelGatewayErrorCode.BUDGET_EXCEEDED.degradable()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -pl data-agent-model-gateway -am -Dtest=ModelGatewayErrorCodeTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，提示错误码类型不存在。

- [ ] **Step 3: 实现错误码、异常与 Registry 接口**

```java
/** 模型网关错误码。 */
public enum ModelGatewayErrorCode implements IErrorCode {
    INVALID_REQUEST("A020001", "模型调用参数错误", false, false),
    CONTEXT_TOO_LONG("A020002", "模型上下文超出限制", false, false),
    BUDGET_EXCEEDED("A020003", "模型调用预算不足", false, false),
    RATE_LIMITED("C020001", "模型调用被限流", true, true),
    PROVIDER_TIMEOUT("C020002", "模型供应商调用超时", true, true),
    PROVIDER_UNAVAILABLE("C020003", "模型供应商不可用", true, true),
    AUTHENTICATION_FAILED("C020004", "模型供应商认证失败", false, false),
    RESPONSE_INVALID("C020005", "模型响应格式错误", true, true),
    CALL_CANCELLED("B020001", "模型调用已取消", false, false);

    private final String code;
    private final String message;
    private final boolean retryable;
    private final boolean degradable;

    ModelGatewayErrorCode(String code, String message, boolean retryable, boolean degradable) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
        this.degradable = degradable;
    }

    public String code() { return code; }
    public String message() { return message; }
    public boolean retryable() { return retryable; }
    public boolean degradable() { return degradable; }
}

/** 模型网关结构化异常。 */
public class ModelGatewayException extends ServiceException {
    private final ModelGatewayErrorCode gatewayErrorCode;

    public ModelGatewayException(ModelGatewayErrorCode errorCode, String message, Throwable cause) {
        super(message, cause, errorCode);
        this.gatewayErrorCode = errorCode;
    }

    public ModelGatewayErrorCode getGatewayErrorCode() { return gatewayErrorCode; }
}

/** 已校验的 Prompt 快照。 */
public record ResolvedPrompt(String templateId, String version, List<ModelMessage> messages) {
    public ResolvedPrompt { messages = List.copyOf(messages); }
}

/** Prompt 模板注册中心契约。 */
public interface PromptTemplateRegistry {
    ResolvedPrompt resolve(String templateId, Map<String, Object> variables);
}
```

- [ ] **Step 4: 运行模块测试并提交**

Run: `mvn -pl data-agent-model-gateway -am test`

Expected: PASS。

```powershell
git add data-agent-model-gateway/src
git commit -m "feat(model-gateway): 定义错误语义与Prompt注册接口"
```

---

### Task 4: 建立执行上下文与 Reactor 传播工具

**Files:**
- Create: `data-agent-model-gateway/src/test/java/com/liang/data/agent/gateway/context/GatewayExecutionContextFactoryTest.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/context/TraceIdProvider.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/context/GatewayExecutionContext.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/context/GatewayExecutionContextFactory.java`
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/context/GatewayReactorContext.java`

- [ ] **Step 1: 编写上下文生成和 Reactor 读取测试**

```java
class GatewayExecutionContextFactoryTest {
    @Test
    void shouldCreateRunContextWithCurrentTrace() {
        GatewayExecutionContextFactory factory = new GatewayExecutionContextFactory(() -> "a".repeat(32));
        GatewayExecutionContext context = factory.create("session-1", 1L, 2, null);

        assertThat(context.runId()).isNotBlank();
        assertThat(context.traceId()).isEqualTo("a".repeat(32));
        assertThat(Mono.deferContextual(GatewayReactorContext::current)
                .contextWrite(GatewayReactorContext.with(context)).block()).isEqualTo(context);
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -pl data-agent-model-gateway -am -Dtest=GatewayExecutionContextFactoryTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，提示上下文类型不存在。

- [ ] **Step 3: 实现上下文类型**

```java
/** 当前 Trace 编号提供器。 */
@FunctionalInterface
public interface TraceIdProvider {
    String currentTraceId();
}

/** 一次工作流运行共享的执行上下文。 */
public record GatewayExecutionContext(String runId, String traceId, String sessionId,
                                      Long userId, Integer agentId, String tenantId) { }

/** 执行上下文工厂。 */
public class GatewayExecutionContextFactory {
    private final TraceIdProvider traceIdProvider;

    public GatewayExecutionContextFactory(TraceIdProvider traceIdProvider) {
        this.traceIdProvider = traceIdProvider;
    }

    public GatewayExecutionContext create(String sessionId, Long userId, Integer agentId, String tenantId) {
        // 1. 优先关联当前可观测链路。
        String traceId = traceIdProvider.currentTraceId();
        // 2. 在关闭追踪的环境生成仅用于业务关联的编号。
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        return new GatewayExecutionContext(UUID.randomUUID().toString(), traceId,
                sessionId, userId, agentId, tenantId);
    }
}

/** Reactor Context 读写工具。 */
public final class GatewayReactorContext {
    private static final Class<GatewayExecutionContext> KEY = GatewayExecutionContext.class;

    private GatewayReactorContext() { }

    public static Function<Context, Context> with(GatewayExecutionContext executionContext) {
        return context -> context.put(KEY, executionContext);
    }

    public static Mono<GatewayExecutionContext> current(ContextView contextView) {
        return contextView.hasKey(KEY)
                ? Mono.just(contextView.get(KEY))
                : Mono.error(new IllegalStateException("当前执行链路缺少网关上下文"));
    }
}
```

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -pl data-agent-model-gateway -am test`

Expected: PASS。

```powershell
git add data-agent-model-gateway/src
git commit -m "feat(model-gateway): 建立统一执行上下文"
```

---

### Task 5: 扩展工作流运行表与按 runId 更新的持久化服务

**Files:**
- Modify: `data-agent-start/src/main/resources/sql/schema.sql`
- Create: `data-agent-start/src/main/resources/sql/upgrade/20260622_observability_phase1.sql`
- Modify: `data-agent-dal/src/main/java/com/liang/data/agent/dal/entity/ChatWorkflowRunEntity.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/vo/WorkflowRunVO.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/service/WorkflowRunService.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/service/impl/WorkflowRunServiceImpl.java`
- Create: `data-agent-workflow/src/test/java/com/liang/data/agent/workflow/service/impl/WorkflowRunServiceImplTest.java`

- [ ] **Step 1: 编写 startRun 持久化身份测试**

```java
@ExtendWith(MockitoExtension.class)
class WorkflowRunServiceImplTest {
    @Mock private ChatWorkflowRunMapper mapper;
    private WorkflowRunServiceImpl service;

    @BeforeEach
    void setUp() { service = new WorkflowRunServiceImpl(mapper); }

    @Test
    void shouldPersistRunAndTraceIdentity() {
        GatewayExecutionContext context = new GatewayExecutionContext(
                "run-1", "trace-1", "session-1", 1L, 2, null);

        service.startRun(context, "查询销售额");

        ArgumentCaptor<ChatWorkflowRunEntity> captor = ArgumentCaptor.forClass(ChatWorkflowRunEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getRunId()).isEqualTo("run-1");
        assertThat(captor.getValue().getTraceId()).isEqualTo("trace-1");
        assertThat(captor.getValue().getStartTime()).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -pl data-agent-workflow -am -Dtest=WorkflowRunServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，提示新字段或方法签名不存在。

- [ ] **Step 3: 更新表结构与升级 SQL**

在 `chat_workflow_run` 增加：

```sql
run_id           VARCHAR(36) DEFAULT NULL COMMENT '单次工作流运行ID',
trace_id         VARCHAR(32) DEFAULT NULL COMMENT 'OpenTelemetry追踪ID',
start_time       TIMESTAMP NULL DEFAULT NULL COMMENT '开始时间',
end_time         TIMESTAMP NULL DEFAULT NULL COMMENT '结束时间',
duration_ms      BIGINT DEFAULT NULL COMMENT '运行耗时毫秒',
failed_node_name VARCHAR(128) DEFAULT NULL COMMENT '失败节点名称',
UNIQUE KEY uk_run_id (run_id),
INDEX idx_trace_id (trace_id),
```

升级脚本使用可重复执行前由运维确认一次的显式 DDL：

```sql
ALTER TABLE chat_workflow_run
    ADD COLUMN run_id VARCHAR(36) DEFAULT NULL COMMENT '单次工作流运行ID' AFTER id,
    ADD COLUMN trace_id VARCHAR(32) DEFAULT NULL COMMENT 'OpenTelemetry追踪ID' AFTER run_id,
    ADD COLUMN start_time TIMESTAMP NULL DEFAULT NULL COMMENT '开始时间' AFTER interrupt_reason,
    ADD COLUMN end_time TIMESTAMP NULL DEFAULT NULL COMMENT '结束时间' AFTER start_time,
    ADD COLUMN duration_ms BIGINT DEFAULT NULL COMMENT '运行耗时毫秒' AFTER end_time,
    ADD COLUMN failed_node_name VARCHAR(128) DEFAULT NULL COMMENT '失败节点名称' AFTER duration_ms,
    ADD UNIQUE KEY uk_run_id (run_id),
    ADD INDEX idx_trace_id (trace_id);
```

- [ ] **Step 4: 修改服务接口为按 runId 更新**

核心签名改为：

```java
void startRun(GatewayExecutionContext context, String query);
void markNodeCompleted(String runId, String nodeName, String nextNodeName, String checkpointId,
                       Map<String, Object> stateSnapshot, String accumulatedContent);
void markCompleted(String runId);
void markInterrupted(String runId, String reason);
void markFailed(String runId, String failedNodeName, String reason);
WorkflowRunVO findLatest(String sessionId);
```

`WorkflowRunServiceImpl` 使用 `runId` 定位记录；完成和失败时同时写 `endTime` 与 `durationMs`。耗时以数据库实体的 `startTime` 为基准，使用 `Duration.between(startTime, now).toMillis()`，不得使用 `System.currentTimeMillis()` 混合计算。

- [ ] **Step 5: 运行持久化测试**

Run: `mvn -pl data-agent-workflow -am -Dtest=WorkflowRunServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

- [ ] **Step 6: 提交运行身份持久化**

```powershell
git add data-agent-start/src/main/resources/sql data-agent-dal/src/main/java/com/liang/data/agent/dal/entity/ChatWorkflowRunEntity.java data-agent-workflow/src
git commit -m "feat(workflow): 持久化运行与追踪标识"
```

---

### Task 6: 在工作流入口创建、恢复并传播执行上下文

**Files:**
- Modify: `data-agent-start/pom.xml`
- Create: `data-agent-start/src/main/java/com/liang/data/agent/config/ModelGatewayContextConfiguration.java`
- Modify: `data-agent-start/src/main/java/com/liang/data/agent/controller/GraphController.java`
- Modify: `data-agent-start/src/test/java/com/liang/data/agent/controller/GraphControllerTest.java`
- Modify: `data-agent-workflow/pom.xml`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/service/GraphService.java`
- Modify: `data-agent-workflow/src/test/java/com/liang/data/agent/workflow/service/GraphServiceTest.java`

- [ ] **Step 1: 添加模块依赖并扩展 Controller 测试夹具**

`data-agent-start` 与 `data-agent-workflow` 显式依赖 `data-agent-model-gateway`。在 `GraphControllerTest` 增加：

```java
private final GatewayExecutionContextFactory contextFactory =
        new GatewayExecutionContextFactory(() -> "b".repeat(32));
```

构造 Controller 时传入该工厂，并新增测试：

```java
@Test
void newQueryShouldPersistAndPropagateRunContext() {
    GraphRequest request = GraphRequest.builder()
            .agentId("2").threadId("thread-run").query("query").build();
    when(chatSessionService.findBySessionId("thread-run"))
            .thenReturn(ChatSessionVO.builder().id("thread-run").agentId(2).build());
    when(chatMessageService.getMultiTurnContext("thread-run", 10)).thenReturn("(none)");
    when(graphService.chatStream(any(), anyString())).thenAnswer(invocation ->
            Flux.deferContextual(view -> Flux.just(
                    GatewayReactorContext.current(view)
                            .map(context -> GraphStreamChunk.content(context.runId(), "test"))
                            .block())));

    List<String> chunks = controller.chat(request).collectList().block();

    ArgumentCaptor<GatewayExecutionContext> captor = ArgumentCaptor.forClass(GatewayExecutionContext.class);
    verify(workflowRunService).startRun(captor.capture(), eq("query"));
    assertThat(captor.getValue().traceId()).isEqualTo("b".repeat(32));
    assertThat(chunks.getFirst()).contains(captor.getValue().runId());
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -pl data-agent-start -am -Dtest=GraphControllerTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，Controller 尚未注入或传播上下文。

- [ ] **Step 3: 实现 TraceIdProvider 配置**

```java
/** 模型网关上下文配置。 */
@Configuration
public class ModelGatewayContextConfiguration {
    @Bean
    GatewayExecutionContextFactory gatewayExecutionContextFactory(ObjectProvider<Tracer> tracerProvider) {
        return new GatewayExecutionContextFactory(() -> {
            Tracer tracer = tracerProvider.getIfAvailable();
            Span span = tracer == null ? null : tracer.currentSpan();
            return span == null ? null : span.context().traceId();
        });
    }
}
```

- [ ] **Step 4: 修改 Controller 的流构建**

新请求调用 `contextFactory.create(...)`；恢复请求从 `WorkflowRunVO` 的 runId/traceId 重建上下文。将原来的 `streamFlux` 改为：

```java
GatewayExecutionContext executionContext = resolveExecutionContext(
        finalInteractionType, finalSessionId, userId, finalAgentId);

Flux<GraphStreamChunk> streamFlux = Mono.fromRunnable(() -> {
            // 1. 新分析创建运行记录，恢复类交互复用原运行记录。
            if (finalInteractionType == InteractionType.NEW_QUERY) {
                workflowRunService.startRun(executionContext, request.getQuery());
            }
        })
        .thenMany(Flux.defer(() -> graphService.chatStream(request, finalMultiTurnContext)))
        // 2. 将运行身份写入 Reactor Context，供图节点和后续模型网关读取。
        .contextWrite(GatewayReactorContext.with(executionContext));
```

完成、失败和中断更新全部使用 `executionContext.runId()`。

- [ ] **Step 5: 修改 GraphService 按 runId 保存节点**

`chatStream` 外层使用 `Flux.deferContextual` 读取 `GatewayExecutionContext`，并把 `runId` 传给 `streamGraph`。`markNodeCompleted`、`markInterrupted` 和 `markFailed` 不再使用 `threadId` 定位运行记录。保留 `threadId` 仅用于图 checkpoint 和会话流上下文。

- [ ] **Step 6: 运行 Controller 与 GraphService 测试**

Run: `mvn -pl data-agent-start -am -Dtest=GraphControllerTest,GraphServiceTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

- [ ] **Step 7: 提交上下文传播**

```powershell
git add data-agent-start data-agent-workflow
git commit -m "feat(workflow): 传播运行链路上下文"
```

---

### Task 7: 接入 Micrometer Tracing、OTLP 与 Prometheus

**Files:**
- Modify: `pom.xml`
- Modify: `data-agent-start/pom.xml`
- Modify: `data-agent-start/src/main/resources/application.yml`

- [ ] **Step 1: 记录接入前的依赖树基线**

Run: `mvn -pl data-agent-start dependency:tree "-Dincludes=io.micrometer:*,io.opentelemetry:*"`

Expected: 输出中不存在 `micrometer-tracing-bridge-otel`、`opentelemetry-exporter-otlp` 和 `micrometer-registry-prometheus`，形成配置变更前基线。

- [ ] **Step 2: 对齐依赖管理**

删除根 POM 中未被代码使用的 `opentelemetry.version` 和显式 `opentelemetry-bom`，统一使用 Spring Boot 3.5 的依赖管理，避免 Micrometer Bridge 与旧 OTel BOM 版本错配。在 `data-agent-start/pom.xml` 增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

- [ ] **Step 3: 增加可观测配置**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
  tracing:
    enabled: ${OBSERVABILITY_TRACING_ENABLED:true}
    sampling:
      probability: ${OBSERVABILITY_SAMPLING_PROBABILITY:1.0}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
  metrics:
    tags:
      application: liang-data-agent
```

生产环境默认采样率必须通过环境变量降低；本地默认 1.0 便于验证。

- [ ] **Step 4: 验证依赖树与应用构建**

Run: `mvn -pl data-agent-start dependency:tree "-Dincludes=io.micrometer:micrometer-tracing-bridge-otel,io.opentelemetry:opentelemetry-exporter-otlp,io.micrometer:micrometer-registry-prometheus"`

Expected: 三个依赖均出现且每个只有一个选定版本，不出现 `omitted for conflict` 指向旧 OTel 1.35 BOM。

Run: `mvn -pl data-agent-start -am -DskipTests package`

Expected: Reactor 全模块编译成功。

- [ ] **Step 5: 提交观测依赖和配置**

```powershell
git add pom.xml data-agent-start/pom.xml data-agent-start/src
git commit -m "feat(observability): 接入OTLP链路与Prometheus指标"
```

---

### Task 8: 提供本地可观测 Docker 编排与冒烟验证

**Files:**
- Create: `docker/observability/docker-compose.yml`
- Create: `docker/observability/otel-collector.yml`
- Create: `docker/observability/tempo.yml`
- Create: `docker/observability/prometheus.yml`
- Create: `docker/observability/grafana/provisioning/datasources/datasources.yml`
- Create: `docker/observability/grafana/provisioning/dashboards/dashboards.yml`
- Create: `docker/observability/grafana/dashboards/data-agent-overview.json`
- Create: `docker/observability/README.md`
- Create: `docker/observability/smoke-test.ps1`

- [ ] **Step 1: 先编写失败的冒烟脚本**

脚本依次请求 Collector 健康端点、Tempo ready、Prometheus ready 和 Grafana health；任一非 2xx 立即退出 1：

```powershell
$targets = @(
    'http://localhost:13133/',
    'http://localhost:3200/ready',
    'http://localhost:9090/-/ready',
    'http://localhost:3000/api/health'
)
foreach ($target in $targets) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $target -TimeoutSec 5
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "可观测组件未就绪：$target"
    }
}
Write-Output '可观测基础设施冒烟检查通过'
```

- [ ] **Step 2: 运行脚本并确认失败**

Run: `powershell -ExecutionPolicy Bypass -File docker/observability/smoke-test.ps1`

Expected: FAIL，组件尚未启动。

- [ ] **Step 3: 创建 Compose 与 Collector 配置**

Compose 使用以下固定镜像，并包含 `otel-collector`、`tempo`、`prometheus`、`grafana`：

```text
otel/opentelemetry-collector-contrib:0.123.0
grafana/tempo:2.7.1
prom/prometheus:v3.2.1
grafana/grafana:11.5.2
```

端口映射固定为 Collector `4317/4318/13133`、Tempo `3200`、Prometheus `9090`、Grafana `3000`。Collector 配置包含：

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318
processors:
  memory_limiter:
    check_interval: 1s
    limit_mib: 256
  batch: {}
exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true
  debug:
    verbosity: basic
extensions:
  health_check:
    endpoint: 0.0.0.0:13133
service:
  extensions: [health_check]
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp/tempo, debug]
```

Prometheus 抓取 `host.docker.internal:18080/actuator/prometheus`。Grafana预置 Prometheus 与 Tempo 数据源；Overview 看板至少显示 JVM 堆内存、HTTP 请求量和 HTTP P95。

- [ ] **Step 4: 校验 Compose 配置**

Run: `docker compose -f docker/observability/docker-compose.yml config`

Expected: 退出码 0，输出四个服务的标准化配置。

- [ ] **Step 5: 启动并执行冒烟检查**

Run: `docker compose -f docker/observability/docker-compose.yml up -d`

Run: `powershell -ExecutionPolicy Bypass -File docker/observability/smoke-test.ps1`

Expected: 输出 `可观测基础设施冒烟检查通过`。

- [ ] **Step 6: 启动应用并验证指标与 Trace**

Run: `mvn -pl data-agent-start -am spring-boot:run`

另一个终端执行：

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:18080/actuator/health
Invoke-WebRequest -UseBasicParsing http://localhost:18080/actuator/prometheus
```

Expected: health 返回 `UP`，Prometheus 内容包含 `http_server_requests_seconds`；Grafana Explore 能按应用名查询到 HTTP Trace。

- [ ] **Step 7: 验证观测后端故障不阻断应用**

Run: `docker compose -f docker/observability/docker-compose.yml down`

保持应用运行或重新启动应用，执行：

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:18080/actuator/health
```

Expected: 即使 OTLP exporter 无法连接 Collector，应用 health 仍返回 `UP`；日志可以记录导出失败，但请求线程不能抛出该异常。

- [ ] **Step 8: 提交本地监控基础设施**

```powershell
git add docker/observability
git commit -m "feat(observability): 提供本地监控基础设施"
```

---

### Task 9: 阶段 1 回归验证与文档收口

**Files:**
- Modify: `docs/superpowers/specs/2026-06-22-model-gateway-observability-design.md`（仅在实现与设计存在必要偏差时修改）
- Create: `docs/model_gateway_observability_guide.md`

- [ ] **Step 1: 编写使用与排障文档**

文档必须包含：模块职责、启动顺序、环境变量、端口、如何通过 `traceId` 查询 Tempo、如何关闭追踪、如何执行升级 SQL、数据隐私边界和常见故障。

- [ ] **Step 2: 运行网关与工作流测试**

Run: `mvn -pl data-agent-model-gateway,data-agent-workflow -am test`

Expected: 所有测试通过，失败数为 0。

- [ ] **Step 3: 运行启动模块定向测试**

Run: `mvn -pl data-agent-start -am -Dtest=GraphControllerTest,GraphServiceTest,WorkflowRunServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: 所有指定测试通过，失败数为 0。

- [ ] **Step 4: 运行全量构建与格式检查**

Run: `mvn clean verify`

Expected: Reactor Summary 全部 `SUCCESS`。

Run: `git diff --check`

Expected: 无输出，退出码 0。

- [ ] **Step 5: 检查网关绕行与敏感日志**

Run: `rg -n "apiKey|proxyPassword|完整 Prompt|完整响应" data-agent-model-gateway data-agent-start data-agent-workflow -g "*.java"`

Expected: 不存在把密钥或完整模型内容写入日志的新增代码。

- [ ] **Step 6: 提交阶段 1 文档**

```powershell
git add -f docs/model_gateway_observability_guide.md docs/superpowers/specs/2026-06-22-model-gateway-observability-design.md
git commit -m "docs(observability): 补充模型网关观测指南"
```

## 阶段 1 完成条件

- 新模块在 Maven Reactor 中独立编译和测试。
- 网关协议、错误语义、Prompt Registry 和执行上下文均有单元测试。
- 新请求拥有唯一 `runId`，运行记录关联当前 `traceId`，恢复执行复用原运行身份。
- 工作流状态更新按 `runId` 定位，不再依赖“会话最近一条记录”。
- OTLP Trace 可在 Tempo 查询，指标可由 Prometheus 抓取并在 Grafana 展示。
- 观测组件关闭时，应用仍可启动和执行工作流。
- 本阶段未接入真实模型调用、动态路由、Langfuse 或完整节点埋点。
