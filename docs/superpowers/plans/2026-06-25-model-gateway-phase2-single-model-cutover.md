# 模型网关阶段 2：单模型切流 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将所有生产模型调用统一切到 `ModelGateway`，提供单模型 OpenAI 兼容 Provider、Invocation/Attempt 明细和显式 sceneCode。

**Architecture:** `data-agent-model-gateway` 继续保持协议与常量纯净；`data-agent-ai-core` 依赖网关协议并提供 `GatewayBackedLlmService`、`DefaultModelGateway`、OpenAI 兼容 Provider 和调用明细持久化实现。`data-agent-service` 的资源门控继续作为 `LlmService` 装饰器，但必须透传显式 sceneCode，不再把资源繁忙伪装成普通模型文本。业务节点和服务层通过显式 sceneCode 调用 `LlmService`，真实模型出口唯一落到 `ModelGateway -> Provider -> AiModelRegistry/ChatClient`。

**Tech Stack:** Java 21、Spring Boot 3.5、Spring AI、Reactor、Micrometer、MyBatis-Plus、MySQL、JUnit 5、Mockito、AssertJ。

---

## 范围说明

本计划只实现阶段 2：单模型网关切流。不实现动态路由、多模型候选、预算、熔断、自动重试、备用模型降级、Langfuse、Nacos Prompt 热更新或管理端页面。

所有 Java 类必须有中文 JavaDoc；注释、日志使用简体中文；关键方法按步骤注释；同时遵守《阿里巴巴 Java 开发手册》。禁止顺手修改无关文件。

当前主工作区已有未跟踪项 `.superpowers/` 和 `data-agent-ui/package-lock.json`，执行本计划时不得纳入提交。

## 文件结构

### 新增或修改的核心文件

- `data-agent-start/src/main/resources/sql/schema.sql`：增加模型网关调用明细表。
- `data-agent-start/src/main/resources/sql/migration/V20260625_01__model_gateway_invocation_attempt.sql`：升级 SQL。
- `data-agent-dal/src/main/java/com/liang/data/agent/dal/entity/ModelGatewayInvocationEntity.java`：Invocation 实体。
- `data-agent-dal/src/main/java/com/liang/data/agent/dal/entity/ModelGatewayAttemptEntity.java`：Attempt 实体。
- `data-agent-dal/src/main/java/com/liang/data/agent/dal/mapper/ModelGatewayInvocationMapper.java`：Invocation Mapper。
- `data-agent-dal/src/main/java/com/liang/data/agent/dal/mapper/ModelGatewayAttemptMapper.java`：Attempt Mapper。
- `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelGatewayScenes.java`：场景编码常量。
- `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/llm/LlmService.java`：增加显式 sceneCode 重载。
- `data-agent-service/src/main/java/com/liang/data/agent/service/ratelimit/ResourceGatedLlmService.java`：透传 sceneCode，并将资源繁忙转为结构化异常。
- `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/`：阶段 2 网关实现、Provider、持久化和观测类。
- `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/config/AiCoreAutoConfiguration.java`：注册网关 Bean 与网关版 `LlmService`。
- 工作流和服务调用点：将 `llmService.call*` 改为显式 sceneCode 调用。

---

### Task 1: 建立 Invocation / Attempt 持久化模型

**Files:**
- Modify: `data-agent-start/src/main/resources/sql/schema.sql`
- Create: `data-agent-start/src/main/resources/sql/migration/V20260625_01__model_gateway_invocation_attempt.sql`
- Create: `data-agent-dal/src/main/java/com/liang/data/agent/dal/entity/ModelGatewayInvocationEntity.java`
- Create: `data-agent-dal/src/main/java/com/liang/data/agent/dal/entity/ModelGatewayAttemptEntity.java`
- Create: `data-agent-dal/src/main/java/com/liang/data/agent/dal/mapper/ModelGatewayInvocationMapper.java`
- Create: `data-agent-dal/src/main/java/com/liang/data/agent/dal/mapper/ModelGatewayAttemptMapper.java`
- Create: `data-agent-start/src/test/java/com/liang/data/agent/gateway/ModelGatewaySchemaTest.java`

- [ ] **Step 1: 编写失败的 SQL 结构测试**

创建 `data-agent-start/src/test/java/com/liang/data/agent/gateway/ModelGatewaySchemaTest.java`：

```java
package com.liang.data.agent.gateway;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型网关调用明细表结构测试。
 */
class ModelGatewaySchemaTest {

    @Test
    void schemaShouldContainModelGatewayInvocationAndAttemptTables() throws Exception {
        String schema = readClasspathResource("sql/schema.sql");

        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS model_gateway_invocation");
        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS model_gateway_attempt");
        assertThat(schema).contains("UNIQUE KEY uk_invocation_id");
        assertThat(schema).contains("UNIQUE KEY uk_attempt_id");
        assertThat(schema).contains("INDEX idx_run_id");
        assertThat(schema).contains("INDEX idx_scene_status_time");
    }

    @Test
    void migrationShouldAddModelGatewayInvocationAndAttemptTables() throws Exception {
        String migration = readClasspathResource("sql/migration/V20260625_01__model_gateway_invocation_attempt.sql");

        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS model_gateway_invocation");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS model_gateway_attempt");
        assertThat(migration).doesNotContain("prompt");
        assertThat(migration).doesNotContain("response");
        assertThat(migration).doesNotContain("api_key");
        assertThat(migration).doesNotContain("proxy_password");
    }

    private String readClasspathResource(String path) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(inputStream).as("classpath resource %s", path).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl data-agent-start -am -Dtest=ModelGatewaySchemaTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，提示 `model_gateway_invocation` 或 migration 文件不存在。

- [ ] **Step 3: 在 schema.sql 中增加两张表**

在 `chat_workflow_run` 后或相邻位置增加：

```sql
-- ----------------------------
-- 11.2. 模型网关调用明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS model_gateway_invocation
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '调用记录主键',
    invocation_id VARCHAR(36)  NOT NULL COMMENT '模型网关调用ID',
    run_id        VARCHAR(36)  DEFAULT NULL COMMENT '工作流运行ID',
    trace_id      VARCHAR(32)  DEFAULT NULL COMMENT 'OpenTelemetry追踪ID',
    session_id    VARCHAR(36)  DEFAULT NULL COMMENT '会话ID',
    user_id       BIGINT       DEFAULT NULL COMMENT '用户ID',
    agent_id      INT          DEFAULT NULL COMMENT '智能体ID',
    tenant_id     VARCHAR(64)  DEFAULT NULL COMMENT '租户ID',
    scene_code    VARCHAR(64)  NOT NULL COMMENT '调用场景编码',
    call_mode     VARCHAR(16)  NOT NULL COMMENT '调用模式：BLOCK、STREAM',
    status        VARCHAR(16)  NOT NULL COMMENT '调用状态：RUNNING、SUCCEEDED、FAILED、CANCELLED',
    provider      VARCHAR(64)  DEFAULT NULL COMMENT '供应商标识',
    model         VARCHAR(128) DEFAULT NULL COMMENT '模型标识',
    start_time    TIMESTAMP    NOT NULL COMMENT '开始时间',
    end_time      TIMESTAMP    NULL DEFAULT NULL COMMENT '结束时间',
    duration_ms   BIGINT       DEFAULT NULL COMMENT '调用耗时毫秒',
    input_tokens  BIGINT       NOT NULL DEFAULT 0 COMMENT '输入Token数',
    output_tokens BIGINT       NOT NULL DEFAULT 0 COMMENT '输出Token数',
    total_tokens  BIGINT       NOT NULL DEFAULT 0 COMMENT '总Token数',
    error_code    VARCHAR(32)  DEFAULT NULL COMMENT '网关错误码',
    error_message VARCHAR(512) DEFAULT NULL COMMENT '脱敏错误摘要',
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_invocation_id (invocation_id),
    INDEX idx_run_id (run_id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_scene_status_time (scene_code, status, start_time),
    INDEX idx_provider_model_time (provider, model, start_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '模型网关调用明细表';

-- ----------------------------
-- 11.3. 模型网关供应商尝试明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS model_gateway_attempt
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '尝试记录主键',
    attempt_id    VARCHAR(36)  NOT NULL COMMENT '供应商尝试ID',
    invocation_id VARCHAR(36)  NOT NULL COMMENT '模型网关调用ID',
    attempt_no    INT          NOT NULL COMMENT '第几次尝试',
    provider      VARCHAR(64)  NOT NULL COMMENT '供应商标识',
    model         VARCHAR(128) NOT NULL COMMENT '模型标识',
    status        VARCHAR(16)  NOT NULL COMMENT '尝试状态：RUNNING、SUCCEEDED、FAILED、CANCELLED',
    start_time    TIMESTAMP    NOT NULL COMMENT '开始时间',
    end_time      TIMESTAMP    NULL DEFAULT NULL COMMENT '结束时间',
    duration_ms   BIGINT       DEFAULT NULL COMMENT '尝试耗时毫秒',
    http_status   INT          DEFAULT NULL COMMENT '供应商HTTP状态码',
    error_code    VARCHAR(32)  DEFAULT NULL COMMENT '网关错误码',
    error_message VARCHAR(512) DEFAULT NULL COMMENT '脱敏错误摘要',
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_attempt_id (attempt_id),
    INDEX idx_invocation_id (invocation_id),
    INDEX idx_provider_model_time (provider, model, start_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '模型网关供应商尝试明细表';
```

- [ ] **Step 4: 创建升级 SQL**

创建 `data-agent-start/src/main/resources/sql/migration/V20260625_01__model_gateway_invocation_attempt.sql`，内容与 Step 3 两张表一致。不得包含完整 Prompt、完整响应或密钥字段。

- [ ] **Step 5: 创建实体与 Mapper**

`ModelGatewayInvocationEntity`：

```java
package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型网关调用明细实体。
 *
 * <p>记录一次业务模型调用的场景、链路身份、路由结果、状态、耗时和Token用量，不保存完整Prompt和完整响应。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_gateway_invocation")
public class ModelGatewayInvocationEntity {
    /** 调用记录主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 模型网关调用ID */
    private String invocationId;
    /** 工作流运行ID */
    private String runId;
    /** OpenTelemetry追踪ID */
    private String traceId;
    /** 会话ID */
    private String sessionId;
    /** 用户ID */
    private Long userId;
    /** 智能体ID */
    private Integer agentId;
    /** 租户ID */
    private String tenantId;
    /** 调用场景编码 */
    private String sceneCode;
    /** 调用模式 */
    private String callMode;
    /** 调用状态 */
    private String status;
    /** 供应商标识 */
    private String provider;
    /** 模型标识 */
    private String model;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 调用耗时毫秒 */
    private Long durationMs;
    /** 输入Token数 */
    private Long inputTokens;
    /** 输出Token数 */
    private Long outputTokens;
    /** 总Token数 */
    private Long totalTokens;
    /** 网关错误码 */
    private String errorCode;
    /** 脱敏错误摘要 */
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

`ModelGatewayAttemptEntity`：

```java
package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型网关供应商尝试明细实体。
 *
 * <p>记录一次模型网关调用下的真实供应商尝试，阶段 2 固定为单次尝试，后续重试和降级可复用该结构。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_gateway_attempt")
public class ModelGatewayAttemptEntity {
    /** 尝试记录主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 供应商尝试ID */
    private String attemptId;
    /** 模型网关调用ID */
    private String invocationId;
    /** 第几次尝试 */
    private Integer attemptNo;
    /** 供应商标识 */
    private String provider;
    /** 模型标识 */
    private String model;
    /** 尝试状态 */
    private String status;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 尝试耗时毫秒 */
    private Long durationMs;
    /** 供应商HTTP状态码 */
    private Integer httpStatus;
    /** 网关错误码 */
    private String errorCode;
    /** 脱敏错误摘要 */
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

两个 Mapper：

```java
package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.ModelGatewayInvocationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型网关调用明细 Mapper。
 */
@Mapper
public interface ModelGatewayInvocationMapper extends BaseMapper<ModelGatewayInvocationEntity> {
}
```

Attempt Mapper：

```java
package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.ModelGatewayAttemptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型网关供应商尝试明细 Mapper。
 */
@Mapper
public interface ModelGatewayAttemptMapper extends BaseMapper<ModelGatewayAttemptEntity> {
}
```

- [ ] **Step 6: 运行测试并提交**

Run:

```powershell
mvn -pl data-agent-start -am -Dtest=ModelGatewaySchemaTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

Commit:

```powershell
git add data-agent-start/src/main/resources/sql data-agent-start/src/test/java/com/liang/data/agent/gateway/ModelGatewaySchemaTest.java data-agent-dal/src/main/java/com/liang/data/agent/dal/entity/ModelGatewayInvocationEntity.java data-agent-dal/src/main/java/com/liang/data/agent/dal/entity/ModelGatewayAttemptEntity.java data-agent-dal/src/main/java/com/liang/data/agent/dal/mapper/ModelGatewayInvocationMapper.java data-agent-dal/src/main/java/com/liang/data/agent/dal/mapper/ModelGatewayAttemptMapper.java
git commit -m "feat(model-gateway): 增加调用明细持久化结构"
```

---

### Task 2: 定义阶段 2 场景编码与 LlmService 显式场景接口

**Files:**
- Create: `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelGatewayScenes.java`
- Modify: `data-agent-ai-core/pom.xml`
- Modify: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/llm/LlmService.java`
- Modify: `data-agent-service/src/main/java/com/liang/data/agent/service/ratelimit/ResourceGatedLlmService.java`
- Modify: `data-agent-service/src/test/java/com/liang/data/agent/service/ratelimit/ResourceGatedLlmServiceTest.java`
- Create: `data-agent-ai-core/src/test/java/com/liang/data/agent/ai/llm/LlmServiceSceneMethodTest.java`

- [ ] **Step 1: 编写 LlmService 场景方法测试**

创建 `data-agent-ai-core/src/test/java/com/liang/data/agent/ai/llm/LlmServiceSceneMethodTest.java`：

```java
package com.liang.data.agent.ai.llm;

import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.gateway.api.ModelGatewayScenes;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 服务显式场景方法测试。
 */
class LlmServiceSceneMethodTest {

    @Test
    void sceneOverloadsShouldFallbackToLegacyMethodsByDefault() {
        RecordingLlmService service = new RecordingLlmService();

        String text = service.callUser(ModelGatewayScenes.SQL_GENERATION, "生成SQL")
                .map(ChatResponseUtil::getText)
                .blockFirst();

        assertThat(text).isEqualTo("ok");
        assertThat(service.calls).containsExactly("callUser:生成SQL");
    }

    private static class RecordingLlmService implements LlmService {
        private final List<String> calls = new ArrayList<>();

        @Override
        public Flux<ChatResponse> call(String system, String user) {
            calls.add("call:" + system + ":" + user);
            return Flux.just(ChatResponseUtil.createPureResponse("ok"));
        }

        @Override
        public Flux<ChatResponse> callSystem(String system) {
            calls.add("callSystem:" + system);
            return Flux.just(ChatResponseUtil.createPureResponse("ok"));
        }

        @Override
        public Flux<ChatResponse> callUser(String user) {
            calls.add("callUser:" + user);
            return Flux.just(ChatResponseUtil.createPureResponse("ok"));
        }
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl data-agent-ai-core -am -Dtest=LlmServiceSceneMethodTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，提示 `ModelGatewayScenes` 或 `callUser(String, String)` 不存在。

- [ ] **Step 3: 在 ai-core 增加 model-gateway 依赖**

在 `data-agent-ai-core/pom.xml` 内部模块依赖区增加：

```xml
<dependency>
    <groupId>com.liang.data.agent</groupId>
    <artifactId>data-agent-model-gateway</artifactId>
</dependency>
```

- [ ] **Step 4: 新增场景常量**

创建 `data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelGatewayScenes.java`：

```java
package com.liang.data.agent.gateway.api;

/**
 * 模型网关调用场景编码。
 *
 * <p>所有生产模型调用应优先使用显式业务场景编码，LEGACY_* 仅作为旧接口兼容兜底。</p>
 */
public final class ModelGatewayScenes {

    public static final String INTENT_RECOGNITION = "INTENT_RECOGNITION";
    public static final String QUERY_ENHANCE = "QUERY_ENHANCE";
    public static final String EVIDENCE_REWRITE = "EVIDENCE_REWRITE";
    public static final String FEASIBILITY_ASSESSMENT = "FEASIBILITY_ASSESSMENT";
    public static final String SCHEMA_MIX_SELECT = "SCHEMA_MIX_SELECT";
    public static final String SQL_GENERATION = "SQL_GENERATION";
    public static final String SQL_REPAIR = "SQL_REPAIR";
    public static final String SEMANTIC_CONSISTENCY = "SEMANTIC_CONSISTENCY";
    public static final String PLANNER = "PLANNER";
    public static final String PYTHON_GENERATION = "PYTHON_GENERATION";
    public static final String PYTHON_ANALYZE = "PYTHON_ANALYZE";
    public static final String REPORT_GENERATION = "REPORT_GENERATION";
    public static final String JSON_REPAIR = "JSON_REPAIR";
    public static final String SESSION_TITLE = "SESSION_TITLE";
    public static final String KNOWLEDGE_CHUNK_NAME = "KNOWLEDGE_CHUNK_NAME";
    public static final String DATA_VIEW_ANALYZE = "DATA_VIEW_ANALYZE";
    public static final String HUMAN_FEEDBACK_INTENT = "HUMAN_FEEDBACK_INTENT";
    public static final String AI_SIMULATED_EXECUTION = "AI_SIMULATED_EXECUTION";
    public static final String LEGACY_SYSTEM_USER = "LEGACY_SYSTEM_USER";
    public static final String LEGACY_USER_ONLY = "LEGACY_USER_ONLY";
    public static final String LEGACY_SYSTEM_ONLY = "LEGACY_SYSTEM_ONLY";

    private ModelGatewayScenes() {
    }
}
```

- [ ] **Step 5: 扩展 LlmService 默认方法**

在 `LlmService` 中增加：

```java
/**
 * 系统消息 + 用户消息，显式指定模型网关场景。
 *
 * @param sceneCode 场景编码
 * @param system 系统消息
 * @param user 用户消息
 * @return 模型响应流
 */
default Flux<ChatResponse> call(String sceneCode, String system, String user) {
    return call(system, user);
}

/**
 * 仅系统消息，显式指定模型网关场景。
 */
default Flux<ChatResponse> callSystem(String sceneCode, String system) {
    return callSystem(system);
}

/**
 * 仅用户消息，显式指定模型网关场景。
 */
default Flux<ChatResponse> callUser(String sceneCode, String user) {
    return callUser(user);
}
```

旧 `call`、`callSystem`、`callUser` 保持不变。

- [ ] **Step 6: 更新 ResourceGatedLlmService 透传场景**

在 `ResourceGatedLlmService` 中新增覆写：

```java
@Override
public Flux<ChatResponse> call(String sceneCode, String system, String user) {
    return callWithPermit(sceneCode, () -> delegate.call(sceneCode, system, user));
}

@Override
public Flux<ChatResponse> callSystem(String sceneCode, String system) {
    return callWithPermit(sceneCode, () -> delegate.callSystem(sceneCode, system));
}

@Override
public Flux<ChatResponse> callUser(String sceneCode, String user) {
    return callWithPermit(sceneCode, () -> delegate.callUser(sceneCode, user));
}
```

并将旧方法改为使用兼容场景：

```java
@Override
public Flux<ChatResponse> call(String system, String user) {
    return call(ModelGatewayScenes.LEGACY_SYSTEM_USER, system, user);
}
```

资源繁忙处理本阶段先保持测试兼容；Task 6 再改为结构化异常并调整测试。

- [ ] **Step 7: 运行测试并提交**

Run:

```powershell
mvn -pl data-agent-ai-core,data-agent-service -am -Dtest=LlmServiceSceneMethodTest,ResourceGatedLlmServiceTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

Commit:

```powershell
git add data-agent-model-gateway/src/main/java/com/liang/data/agent/gateway/api/ModelGatewayScenes.java data-agent-ai-core/pom.xml data-agent-ai-core/src/main/java/com/liang/data/agent/ai/llm/LlmService.java data-agent-ai-core/src/test/java/com/liang/data/agent/ai/llm/LlmServiceSceneMethodTest.java data-agent-service/src/main/java/com/liang/data/agent/service/ratelimit/ResourceGatedLlmService.java data-agent-service/src/test/java/com/liang/data/agent/service/ratelimit/ResourceGatedLlmServiceTest.java
git commit -m "feat(model-gateway): 定义模型调用场景编码"
```

---

### Task 3: 实现调用明细记录器

**Files:**
- Modify: `data-agent-common/src/main/java/com/liang/data/agent/common/config/DataAgentProperties.java`
- Create: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/ModelGatewayProperties.java`
- Create: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/ModelGatewayCallStatus.java`
- Create: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/ModelGatewayInvocationRecorder.java`
- Create: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/PersistentModelGatewayInvocationRecorder.java`
- Create: `data-agent-ai-core/src/test/java/com/liang/data/agent/ai/gateway/PersistentModelGatewayInvocationRecorderTest.java`

- [ ] **Step 1: 编写记录器测试**

创建 `PersistentModelGatewayInvocationRecorderTest`，用 Mockito mock 两个 Mapper，验证成功、失败和持久化异常不向外抛出。

核心测试结构：

```java
/**
 * 模型网关调用明细记录器测试。
 */
class PersistentModelGatewayInvocationRecorderTest {

    private final ModelGatewayInvocationMapper invocationMapper = mock(ModelGatewayInvocationMapper.class);
    private final ModelGatewayAttemptMapper attemptMapper = mock(ModelGatewayAttemptMapper.class);
    private final PersistentModelGatewayInvocationRecorder recorder =
            new PersistentModelGatewayInvocationRecorder(invocationMapper, attemptMapper, true);

    @Test
    void startInvocationShouldInsertRunningRecord() {
        GatewayExecutionContext context = new GatewayExecutionContext("run-1", "trace-1", "session-1", 1L, 2, null);

        recorder.startInvocation("inv-1", context, "SQL_GENERATION", ModelCallMode.BLOCK);

        ArgumentCaptor<ModelGatewayInvocationEntity> captor = ArgumentCaptor.forClass(ModelGatewayInvocationEntity.class);
        verify(invocationMapper).insert(captor.capture());
        assertThat(captor.getValue().getInvocationId()).isEqualTo("inv-1");
        assertThat(captor.getValue().getRunId()).isEqualTo("run-1");
        assertThat(captor.getValue().getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void recorderShouldSwallowPersistenceFailure() {
        doThrow(new RuntimeException("db down")).when(invocationMapper).insert(any());

        assertThatCode(() -> recorder.startInvocation(
                "inv-1",
                new GatewayExecutionContext("run-1", "trace-1", "session-1", 1L, 2, null),
                "SQL_GENERATION",
                ModelCallMode.BLOCK
        )).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl data-agent-ai-core -am -Dtest=PersistentModelGatewayInvocationRecorderTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，记录器类型不存在。

- [ ] **Step 3: 增加配置属性**

在 `DataAgentProperties` 中增加：

```java
/**
 * 模型网关配置。
 */
private ModelGatewayProperties modelGateway = new ModelGatewayProperties();
```

并在同类中增加内部类：

```java
@Getter
@Setter
public static class ModelGatewayProperties {
    /** 默认模型调用超时秒数 */
    private int defaultTimeoutSeconds = 30;
    /** 是否启用调用明细持久化 */
    private boolean persistenceEnabled = true;
    /** 是否启用模型网关指标 */
    private boolean metricsEnabled = true;
}
```

- [ ] **Step 4: 实现状态枚举与记录器接口**

`ModelGatewayCallStatus`：

```java
package com.liang.data.agent.ai.gateway;

/**
 * 模型网关调用状态。
 */
public enum ModelGatewayCallStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
```

`ModelGatewayInvocationRecorder`：

```java
package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.gateway.api.ModelCallMode;
import com.liang.data.agent.gateway.api.ModelRoute;
import com.liang.data.agent.gateway.api.ModelUsage;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;

/**
 * 模型网关调用明细记录器。
 */
public interface ModelGatewayInvocationRecorder {
    void startInvocation(String invocationId, GatewayExecutionContext context, String sceneCode, ModelCallMode mode);
    void finishInvocation(String invocationId, ModelGatewayCallStatus status, ModelRoute route, ModelUsage usage,
                          ModelGatewayErrorCode errorCode, String errorMessage);
    void startAttempt(String invocationId, String attemptId, int attemptNo, String provider, String model);
    void finishAttempt(String attemptId, ModelGatewayCallStatus status, Integer httpStatus,
                       ModelGatewayErrorCode errorCode, String errorMessage);
}
```

- [ ] **Step 5: 实现持久化记录器**

`PersistentModelGatewayInvocationRecorder` 要求：

1. 构造器接收两个 Mapper 和 `persistenceEnabled`。
2. `persistenceEnabled=false` 时所有方法直接返回。
3. 所有数据库异常只记录中文 warn，不向外抛出。
4. `errorMessage` 统一截断到 512 字符。
5. 结束时间用 `LocalDateTime.now()`，耗时用 `Duration.between(startTime, endTime).toMillis()`；若无法查到开始时间，允许 `durationMs` 为空。

可使用 `LambdaUpdateWrapper` 按 `invocationId` / `attemptId` 更新，不需要先查实体。

- [ ] **Step 6: 运行测试并提交**

Run:

```powershell
mvn -pl data-agent-ai-core -am -Dtest=PersistentModelGatewayInvocationRecorderTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

Commit:

```powershell
git add data-agent-common/src/main/java/com/liang/data/agent/common/config/DataAgentProperties.java data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway data-agent-ai-core/src/test/java/com/liang/data/agent/ai/gateway/PersistentModelGatewayInvocationRecorderTest.java
git commit -m "feat(model-gateway): 记录模型调用明细"
```

---

### Task 4: 实现 OpenAI 兼容 Provider

**Files:**
- Create: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/OpenAiCompatibleGatewayProvider.java`
- Create: `data-agent-ai-core/src/test/java/com/liang/data/agent/ai/gateway/OpenAiCompatibleGatewayProviderTest.java`

- [ ] **Step 1: 编写 Provider 测试**

使用 mock `AiModelRegistry` 和 mock `ChatClient` 链式调用较脆弱，优先为 Provider 抽一个包内可见执行器接口：

```java
interface ChatClientInvoker {
    ChatResponse call(List<ModelMessage> messages);
    Flux<ChatResponse> stream(List<ModelMessage> messages);
}
```

测试中注入 fake invoker，生产构造器用 `AiModelRegistry` 创建默认 invoker。

测试应覆盖：

1. block 成功返回 `GatewayResult`。
2. stream 成功返回增量 `GatewayChunk`，最后一个 finished chunk 带 usage 和 route。
3. invoker 抛异常时转换为 `ModelGatewayException`。
4. 空响应转换为 `RESPONSE_INVALID`。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl data-agent-ai-core -am -Dtest=OpenAiCompatibleGatewayProviderTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，Provider 不存在。

- [ ] **Step 3: 实现 Provider**

Provider 公共方法建议：

```java
/**
 * OpenAI 兼容模型供应商适配器。
 */
public class OpenAiCompatibleGatewayProvider {

    public GatewayResult call(String invocationId, ModelGatewayRequest request) {
        // 1. 调用 ChatClient 获取完整响应。
        // 2. 提取文本、用量和路由信息。
        // 3. 组装 GatewayResult。
    }

    public Flux<GatewayChunk> stream(String invocationId, ModelGatewayRequest request) {
        // 1. 调用 ChatClient 获取流式响应。
        // 2. 将每个响应转为增量 chunk。
        // 3. 在流结束时补一个 finished=true 的完成片段。
    }
}
```

用量提取规则：

1. 优先从 Spring AI `ChatResponse.getMetadata().getUsage()` 读取。
2. 若无法读取，返回 `new ModelUsage(0, 0, 0)`。
3. route 使用当前活动模型配置中的 `provider` 与 `modelName`。在 `AiModelRegistry` 增加脱敏快照方法，只返回路由需要的非敏感字段：

```java
/**
 * 获取当前激活的对话模型脱敏配置快照。
 *
 * <p>仅返回 provider、modelName 等路由展示字段，不返回 apiKey、proxyPassword 等敏感配置。</p>
 */
public Optional<ModelConfigEntity> getActiveChatConfigSnapshot() {
    return queryService.getActiveConfig(ModelType.CHAT)
            .map(this::copySafeChatConfig);
}

private ModelConfigEntity copySafeChatConfig(ModelConfigEntity source) {
    ModelConfigEntity snapshot = new ModelConfigEntity();
    snapshot.setProvider(source.getProvider());
    snapshot.setModelName(source.getModelName());
    snapshot.setModelType(source.getModelType());
    snapshot.setBaseUrl(source.getBaseUrl());
    return snapshot;
}
```

异常映射规则：

- `TimeoutException` 或 Reactor timeout：`PROVIDER_TIMEOUT`
- 401/403 字符串：`AUTHENTICATION_FAILED`
- 429 字符串：`RATE_LIMITED`
- 5xx、连接失败：`PROVIDER_UNAVAILABLE`
- 其他：`PROVIDER_UNAVAILABLE`

不得在日志中输出消息内容。

- [ ] **Step 4: 运行测试并提交**

Run:

```powershell
mvn -pl data-agent-ai-core -am -Dtest=OpenAiCompatibleGatewayProviderTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

Commit:

```powershell
git add data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/OpenAiCompatibleGatewayProvider.java data-agent-ai-core/src/test/java/com/liang/data/agent/ai/gateway/OpenAiCompatibleGatewayProviderTest.java data-agent-ai-core/src/main/java/com/liang/data/agent/ai/model/AiModelRegistry.java
git commit -m "feat(model-gateway): 实现OpenAI兼容Provider"
```

---

### Task 5: 实现 DefaultModelGateway 编排

**Files:**
- Create: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/DefaultModelGateway.java`
- Create: `data-agent-ai-core/src/test/java/com/liang/data/agent/ai/gateway/DefaultModelGatewayTest.java`

- [ ] **Step 1: 编写网关编排测试**

测试场景：

1. `call` 要求 `ModelCallMode.BLOCK`，传入 STREAM 抛异常。
2. `stream` 要求 `ModelCallMode.STREAM`，传入 BLOCK 抛异常。
3. 成功调用时按顺序 startInvocation、startAttempt、finishAttempt、finishInvocation。
4. Provider 失败时 finish 状态为 FAILED，并向外返回 `ModelGatewayException`。
5. 持久化记录器抛异常时不影响 Provider 结果。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl data-agent-ai-core -am -Dtest=DefaultModelGatewayTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，`DefaultModelGateway` 不存在。

- [ ] **Step 3: 实现 DefaultModelGateway**

关键结构：

```java
/**
 * 默认模型网关实现。
 *
 * <p>阶段 2 使用固定单模型 Provider，不做动态路由、预算、熔断和重试。</p>
 */
@Slf4j
public class DefaultModelGateway implements ModelGateway {

    private final OpenAiCompatibleGatewayProvider provider;
    private final ModelGatewayInvocationRecorder recorder;
    private final Duration defaultTimeout;
    private final MeterRegistry meterRegistry;

    @Override
    public Mono<GatewayResult> call(ModelGatewayRequest request) {
        request.requireMode(ModelCallMode.BLOCK);
        return Mono.deferContextual(contextView -> {
            GatewayExecutionContext context = resolveContext(contextView);
            String invocationId = UUID.randomUUID().toString();
            String attemptId = UUID.randomUUID().toString();
            // 1. 创建调用与尝试记录。
            // 2. 在默认超时内调用 Provider。
            // 3. 成功时记录用量、路由和耗时。
            // 4. 失败时转换结构化错误并更新记录。
        });
    }
}
```

上下文缺失时：

```java
private GatewayExecutionContext fallbackContext() {
    String traceId = UUID.randomUUID().toString().replace("-", "");
    return new GatewayExecutionContext(UUID.randomUUID().toString(), traceId, "model-gateway-standalone", null, null, null);
}
```

日志必须中文且不包含 Prompt/响应正文。

- [ ] **Step 4: 增加基础指标**

在成功/失败结束处记录：

- `model_gateway_invocations_total`
- `model_gateway_invocation_duration_seconds`
- `model_gateway_tokens_total`
- `model_gateway_errors_total`

标签限制为 `scene_code`、`provider`、`model`、`status`、`error_code`。不得包含 `runId`、`traceId`、`invocationId`。

- [ ] **Step 5: 运行测试并提交**

Run:

```powershell
mvn -pl data-agent-ai-core -am -Dtest=DefaultModelGatewayTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

Commit:

```powershell
git add data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/DefaultModelGateway.java data-agent-ai-core/src/test/java/com/liang/data/agent/ai/gateway/DefaultModelGatewayTest.java
git commit -m "feat(model-gateway): 编排单模型调用生命周期"
```

---

### Task 6: 切换 LlmService 默认 Bean 为网关适配器

**Files:**
- Create: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/GatewayBackedLlmService.java`
- Modify: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/config/AiCoreAutoConfiguration.java`
- Modify: `data-agent-service/src/main/java/com/liang/data/agent/service/ratelimit/ResourceGatedLlmService.java`
- Modify: `data-agent-service/src/test/java/com/liang/data/agent/service/ratelimit/ResourceGatedLlmServiceTest.java`
- Create: `data-agent-ai-core/src/test/java/com/liang/data/agent/ai/gateway/GatewayBackedLlmServiceTest.java`

- [ ] **Step 1: 编写 GatewayBackedLlmService 测试**

测试：

1. `callUser(ModelGatewayScenes.SQL_GENERATION, "prompt")` 生成 USER 消息和 sceneCode。
2. 旧 `callUser("prompt")` 使用 `LEGACY_USER_ONLY`。
3. STREAM 模式走 `ModelGateway.stream`。
4. BLOCK 模式走 `ModelGateway.call`。
5. `GatewayChunk`/`GatewayResult` 转回 `ChatResponse` 文本。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl data-agent-ai-core -am -Dtest=GatewayBackedLlmServiceTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，适配器不存在。

- [ ] **Step 3: 实现 GatewayBackedLlmService**

核心规则：

```java
/**
 * 基于模型网关的 LLM 服务适配器。
 */
@RequiredArgsConstructor
public class GatewayBackedLlmService implements LlmService {

    private final ModelGateway modelGateway;
    private final DataAgentProperties properties;

    @Override
    public Flux<ChatResponse> callUser(String sceneCode, String user) {
        return invoke(sceneCode, List.of(new ModelMessage(ModelMessageRole.USER, user)));
    }

    private Flux<ChatResponse> invoke(String sceneCode, List<ModelMessage> messages) {
        ModelCallMode mode = resolveMode();
        ModelGatewayRequest request = new ModelGatewayRequest(
                sceneCode,
                ModelPrompt.direct(messages),
                mode,
                GatewayConstraints.defaults(),
                Map.of()
        );
        if (ModelCallMode.BLOCK == mode) {
            return modelGateway.call(request)
                    .map(GatewayResult::content)
                    .map(ChatResponseUtil::createPureResponse)
                    .flux();
        }
        return modelGateway.stream(request)
                .filter(chunk -> !chunk.finished())
                .map(GatewayChunk::content)
                .map(ChatResponseUtil::createPureResponse);
    }
}
```

旧方法使用兼容场景：

```java
@Override
public Flux<ChatResponse> callUser(String user) {
    return callUser(ModelGatewayScenes.LEGACY_USER_ONLY, user);
}
```

- [ ] **Step 4: 修改 AiCoreAutoConfiguration**

把旧 Bean：

```java
public LlmService llmService(AiModelRegistry registry)
```

替换为：

```java
/**
 * 注册基于模型网关的大模型服务。
 */
@Bean
public LlmService llmService(ModelGateway modelGateway) {
    return new GatewayBackedLlmService(modelGateway, properties);
}
```

同时注册：

```java
/**
 * 注册 OpenAI 兼容模型供应商适配器。
 */
@Bean
public OpenAiCompatibleGatewayProvider openAiCompatibleGatewayProvider(AiModelRegistry registry) {
    return new OpenAiCompatibleGatewayProvider(registry);
}

/**
 * 注册模型网关调用明细记录器。
 */
@Bean
public ModelGatewayInvocationRecorder modelGatewayInvocationRecorder(
        ModelGatewayInvocationMapper invocationMapper,
        ModelGatewayAttemptMapper attemptMapper) {
    return new PersistentModelGatewayInvocationRecorder(
            invocationMapper,
            attemptMapper,
            properties.getModelGateway().isPersistenceEnabled()
    );
}

/**
 * 注册默认模型网关。
 */
@Bean
public ModelGateway modelGateway(OpenAiCompatibleGatewayProvider provider,
                                 ModelGatewayInvocationRecorder recorder,
                                 ObjectProvider<MeterRegistry> meterRegistryProvider) {
    Duration timeout = Duration.ofSeconds(properties.getModelGateway().getDefaultTimeoutSeconds());
    MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
    return new DefaultModelGateway(provider, recorder, timeout, meterRegistry);
}
```

如果 `MeterRegistry` 不存在，使用 `Metrics.globalRegistry` 或 no-op 逻辑，不能导致启动失败。

- [ ] **Step 5: 修改 ResourceGatedLlmService 资源繁忙语义**

资源不足时不要返回普通模型文本，改为：

```java
return Flux.error(new ModelGatewayException(
        ModelGatewayErrorCode.RATE_LIMITED,
        "大模型资源繁忙，请稍后重试"
));
```

并更新测试断言为 `StepVerifier` 检查错误码。

- [ ] **Step 6: 运行测试并提交**

Run:

```powershell
mvn -pl data-agent-ai-core,data-agent-service -am -Dtest=GatewayBackedLlmServiceTest,ResourceGatedLlmServiceTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

Commit:

```powershell
git add data-agent-ai-core/src/main/java/com/liang/data/agent/ai/gateway/GatewayBackedLlmService.java data-agent-ai-core/src/main/java/com/liang/data/agent/ai/config/AiCoreAutoConfiguration.java data-agent-ai-core/src/test/java/com/liang/data/agent/ai/gateway/GatewayBackedLlmServiceTest.java data-agent-service/src/main/java/com/liang/data/agent/service/ratelimit/ResourceGatedLlmService.java data-agent-service/src/test/java/com/liang/data/agent/service/ratelimit/ResourceGatedLlmServiceTest.java
git commit -m "feat(model-gateway): 切换默认模型调用出口"
```

---

### Task 7: 迁移生产调用点到显式 sceneCode

**Files:**
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/IntentRecognitionNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/EvidenceRecallNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/QueryEnhanceNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/FeasibilityAssessmentNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/PlannerNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/service/impl/Nl2SqlServiceImpl.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/SqlExecuteNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/PythonGenerateNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/PythonAnalyzeNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/ReportGeneratorNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/node/ClarificationNormalizeNode.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/util/JsonParseUtil.java`
- Modify: `data-agent-workflow/src/main/java/com/liang/data/agent/workflow/service/HumanFeedbackIntentService.java`
- Modify: `data-agent-service/src/main/java/com/liang/data/agent/service/chat/SessionTitleService.java`
- Modify: `data-agent-service/src/main/java/com/liang/data/agent/service/knowledge/chunk/impl/AiChunkNameGenerator.java`
- Modify: `data-agent-ai-core/src/main/java/com/liang/data/agent/ai/code/strategy/AiSimulatedExecutor.java`
- Modify related tests where Mockito verifies old overloads.
- Create: `data-agent-start/src/test/java/com/liang/data/agent/gateway/ModelGatewayCutoverScanTest.java`

- [ ] **Step 1: 编写切流扫描测试**

创建 `ModelGatewayCutoverScanTest`：

```java
package com.liang.data.agent.gateway;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型网关切流静态扫描测试。
 */
class ModelGatewayCutoverScanTest {

    @Test
    void productionCodeShouldNotCallChatClientDirectlyOutsideGatewayProvider() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        List<Path> javaFiles;
        try (var stream = Files.walk(root)) {
            javaFiles = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("\\src\\test\\"))
                    .filter(path -> !path.toString().contains("/src/test/"))
                    .toList();
        }

        List<String> violations = javaFiles.stream()
                .filter(path -> !path.toString().contains("OpenAiCompatibleGatewayProvider.java"))
                .filter(path -> !path.toString().contains("DynamicModelFactory.java"))
                .filter(path -> !path.toString().contains("AiModelRegistry.java"))
                .filter(path -> containsAny(path, ".prompt()", "ChatClient.builder", "ChatModel "))
                .map(root::relativize)
                .map(Path::toString)
                .toList();

        assertThat(violations).isEmpty();
    }

    private boolean containsAny(Path path, String... needles) {
        try {
            String content = Files.readString(path);
            for (String needle : needles) {
                if (content.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (Exception exception) {
            throw new IllegalStateException("读取文件失败：" + path, exception);
        }
    }
}
```

- [ ] **Step 2: 运行扫描测试并确认当前状态**

Run:

```powershell
mvn -pl data-agent-start -am -Dtest=ModelGatewayCutoverScanTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS 或仅暴露已知允许文件。若暴露业务文件，必须在本任务迁移。

- [ ] **Step 3: 修改调用点使用显式 sceneCode**

示例修改：

```java
Flux<ChatResponse> responseFlux = llmService.callUser(ModelGatewayScenes.INTENT_RECOGNITION, prompt);
```

`Nl2SqlServiceImpl` 映射：

```java
return llmService.callUser(ModelGatewayScenes.SQL_GENERATION, prompt);
```

`fixSql`：

```java
return llmService.callUser(ModelGatewayScenes.SQL_REPAIR, prompt);
```

`fineSelect`：

```java
return llmService.callUser(ModelGatewayScenes.SCHEMA_MIX_SELECT, prompt)
```

`checkSemanticConsistency`：

```java
return llmService.callUser(ModelGatewayScenes.SEMANTIC_CONSISTENCY, prompt);
```

其他映射按 `ModelGatewayScenes` 常量逐一使用。

- [ ] **Step 4: 修复 Mockito 验证**

如果测试中原来验证：

```java
verify(llmService).callUser(promptCaptor.capture());
```

改为：

```java
verify(llmService).callUser(eq(ModelGatewayScenes.REPORT_GENERATION), promptCaptor.capture());
```

仅测试代码可使用 `anyString()`，生产代码必须显式常量。

- [ ] **Step 5: 运行节点与服务测试**

Run:

```powershell
mvn -pl data-agent-workflow,data-agent-service,data-agent-ai-core,data-agent-start -am -Dtest=IntentRecognitionNodeTest,EvidenceRecallNodeTest,QueryEnhanceNodeTest,ClarificationNormalizeNodeTest,ReportGeneratorNodeTest,SqlExecuteNodeTest,JsonParseUtilTest,HumanFeedbackIntentServiceTest,ResourceGatedLlmServiceTest,ModelGatewayCutoverScanTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 6: 搜索旧调用残留**

Run:

```powershell
rg -n "llmService\\.call\\(|llmService\\.callUser\\(|llmService\\.callSystem\\(" data-agent-ai-core/src/main/java data-agent-workflow/src/main/java data-agent-service/src/main/java
```

Expected: 生产调用点应使用带 `ModelGatewayScenes.*` 的重载；旧重载只允许出现在 `GatewayBackedLlmService`、`ResourceGatedLlmService` 兼容入口或明确的 fallback 包装方法中。

- [ ] **Step 7: 提交调用点迁移**

```powershell
git add data-agent-ai-core/src/main/java data-agent-workflow/src/main/java data-agent-service/src/main/java data-agent-start/src/test/java/com/liang/data/agent/gateway/ModelGatewayCutoverScanTest.java data-agent-workflow/src/test/java data-agent-service/src/test/java data-agent-ai-core/src/test/java
git commit -m "feat(model-gateway): 迁移生产模型调用场景"
```

---

### Task 8: 阶段 2 回归验证与文档补充

**Files:**
- Modify: `docs/model_gateway_observability_guide.md`
- Create: `docs/model_gateway_phase2_cutover_guide.md`

- [ ] **Step 1: 补充阶段 2 使用指南**

创建 `docs/model_gateway_phase2_cutover_guide.md`，包含：

1. 阶段 2 调用链。
2. `LlmService` 兼容接口与显式 sceneCode 使用方式。
3. Invocation / Attempt 表字段说明。
4. 如何通过 `invocationId`、`runId`、`traceId` 排查调用。
5. 资源门控繁忙时的结构化错误语义。
6. 不支持动态路由、Langfuse、预算、熔断的阶段边界。
7. 隐私边界：不保存完整 Prompt、完整响应、密钥。
8. 常见故障：未配置 CHAT 模型、Provider 认证失败、超时、调用明细未写入、Prometheus 指标为空。

- [ ] **Step 2: 运行核心模块测试**

Run:

```powershell
mvn -pl data-agent-model-gateway,data-agent-ai-core,data-agent-workflow,data-agent-service -am test
```

Expected: PASS。

- [ ] **Step 3: 运行启动模块定向测试**

Run:

```powershell
mvn -pl data-agent-start -am -Dtest=GraphControllerTest,GraphServiceTest,WorkflowRunServiceImplTest,ModelGatewaySchemaTest,ModelGatewayCutoverScanTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 4: 运行全量构建并记录基线**

Run:

```powershell
mvn clean verify
```

Expected: 理想状态为 PASS。若仍失败于既有 `AgentKnowledgeChunkEntityTest.migrationShouldAddTaskVersionAndProcessingLease` 缺失 `sql/migration/V20260606_02__knowledge_chunk_task_version.sql`，不得在本任务修无关基线，需在最终报告中明确说明。

- [ ] **Step 5: 运行敏感信息与直连出口扫描**

Run:

```powershell
rg -n "apiKey|proxyPassword|完整 Prompt|完整响应" data-agent-model-gateway data-agent-ai-core data-agent-start data-agent-workflow data-agent-service -g "*.java"
rg -n "\\.prompt\\(\\)|ChatClient\\.builder|ChatModel " data-agent-ai-core data-agent-workflow data-agent-service data-agent-start -g "*.java"
git diff --check
```

Expected:

- 敏感词命中不得是新增日志或新增落库字段泄露。
- `ChatClient` / `ChatModel` 生产直连只允许在 `DynamicModelFactory`、`AiModelRegistry` 和 `OpenAiCompatibleGatewayProvider` 等基础设施文件中出现。
- `git diff --check` 无输出。

- [ ] **Step 6: 提交阶段 2 文档**

```powershell
git add -f docs/model_gateway_phase2_cutover_guide.md docs/model_gateway_observability_guide.md
git commit -m "docs(model-gateway): 补充单模型切流指南"
```

## 阶段 2 完成条件

- `LlmService` 默认真实调用出口为 `GatewayBackedLlmService -> ModelGateway`。
- 所有生产模型调用经由 `ModelGateway`。
- 主要生产调用点使用显式 `ModelGatewayScenes`。
- OpenAI 兼容 Provider 支持 block 与 stream。
- Invocation / Attempt 成功、失败、取消均有明细记录。
- 调用明细持久化失败不阻断主模型调用。
- 资源门控繁忙返回结构化网关错误，不再伪装成普通模型文本。
- 不新增完整 Prompt、完整响应、apiKey、proxyPassword 的日志、Span、Metric 或数据库字段。
- 阶段 2 不包含动态路由、Langfuse、预算、熔断和自动降级。
