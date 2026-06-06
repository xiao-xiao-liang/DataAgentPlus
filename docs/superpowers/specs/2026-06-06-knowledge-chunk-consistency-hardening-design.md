# 知识分块一致性加固设计

## 1. 背景与目标

当前知识分块工作台已支持单分块编辑、异步向量化、AI 命名和手动重试，但在消息乱序、重复消费、任务超时、死信延迟到达以及向量删除失败时，仍可能出现旧任务覆盖新任务、数据库状态与有效向量不一致、过期向量参与检索等问题。

本次改造目标：

- 使用 RocketMQ 事务消息保证正文更新与向量化消息提交的一致性。
- 通过正文版本和任务版本共同隔离乱序消息、旧消费者和旧死信。
- 建立严格、可验证的分块向量化状态机。
- 保证检索结果只使用数据库确认的当前有效向量。
- 自动恢复长期停留在等待或处理状态的任务。
- 消除本功能范围内的魔法字符串和具有业务含义的魔法数值。
- 修复前端未保存内容丢失和异步请求竞态。

## 2. 设计原则

### 2.1 MyBatis-Plus 使用约束

分块表相关的数据访问优先使用 MyBatis-Plus 提供的能力：

- 分块服务接口优先继承 `IService<AgentKnowledgeChunkEntity>`。
- 分块服务实现优先继承 `ServiceImpl<AgentKnowledgeChunkMapper, AgentKnowledgeChunkEntity>`。
- 普通单表查询和更新优先使用 `lambdaQuery()`、`lambdaUpdate()`。
- 仅在复杂 CAS、批量活动版本查询或 MP 服务层难以清晰表达的操作中使用自定义 Mapper 方法。
- 自定义 Mapper 方法必须对应明确的数据一致性需求，避免将普通 CRUD 下沉到 Mapper。
- 其他表和跨模块依赖根据职责选择注入 Service 或 Mapper，不强制统一。

### 2.2 一致性边界

- 数据库是分块当前版本、当前任务和当前有效向量的事实来源。
- RocketMQ 事务消息保证：只要正文更新事务成功，对应向量化消息就能够提交。
- 向量库不参与数据库事务，因此通过版本隔离、有效版本校验和尽力清理保证业务正确性。
- 旧向量允许短期物理残留，但不得参与最终检索结果。

## 3. 数据模型

在 `agent_knowledge_chunk` 增加：

```sql
vector_task_version INT NOT NULL DEFAULT 1 COMMENT '当前向量化任务版本号',
vector_processing_started_at TIMESTAMP NULL COMMENT '当前向量化任务开始处理时间'
```

现有字段语义调整：

- `content_version`：正文版本，仅在正文变化时递增。
- `vector_task_version`：向量化任务版本，每次正文修改、手动重试或超时恢复时递增。
- `vector_version`：当前有效向量对应的正文版本。
- `embedding_id`：数据库确认的当前有效向量 ID。
- `vector_status`：当前任务状态。

向量 ID 格式：

```text
{chunkId}-c{contentVersion}-t{vectorTaskVersion}
```

向量 metadata 必须包含：

```text
agentKnowledgeId
chunkId
chunkOrder
contentVersion
vectorTaskVersion
splitterType
```

## 4. 状态机

状态统一定义为 `ChunkVectorStatus` 枚举：

```text
PENDING
PROCESSING
SYNCED
FAILED
```

合法状态迁移：

| 操作 | 来源状态 | 目标状态 | 版本变化 |
| --- | --- | --- | --- |
| 修改正文 | SYNCED / FAILED | PENDING | `contentVersion + 1`，`vectorTaskVersion + 1` |
| 消费者领取 | PENDING | PROCESSING | 不变，记录处理开始时间 |
| 消费成功 | PROCESSING | SYNCED | 回写 `vectorVersion` 和 `embeddingId` |
| 可重试消费失败 | PROCESSING | PENDING | 不变 |
| 重试耗尽 | PROCESSING | FAILED | 不变 |
| 手动重试 | FAILED | PENDING | `vectorTaskVersion + 1` |
| 超时恢复 | 超时 PENDING / PROCESSING | PENDING | `vectorTaskVersion + 1` |

所有任务状态 CAS 必须同时匹配：

```text
chunkId + contentVersion + vectorTaskVersion + 预期来源状态
```

仅修改名称时：

- 只更新名称并锁定名称。
- 不改变正文版本和任务版本。
- 不触发向量化。

服务端通过请求名称与数据库当前名称比较判断是否属于手动改名，不信任客户端提供的判断结果。

## 5. RocketMQ 事务消息

### 5.1 消息结构

事务消息包含：

```text
agentId
knowledgeId
chunkId
contentVersion
vectorTaskVersion
operationId
```

`operationId` 用于标识一次事务消息操作和辅助日志排查。

### 5.2 正文更新

1. Producer 发送 Half Message。
2. RocketMQ 执行本地事务。
3. 本地事务使用 CAS 更新正文、名称、正文版本、任务版本和状态。
4. 本地事务成功后提交消息。
5. 本地事务失败或 CAS 冲突时回滚消息。
6. Broker 回查时查询数据库：
   - 当前分块的正文版本、任务版本和状态与消息完全匹配时提交。
   - 不匹配、记录不存在或本地事务未成功时回滚。

严格事务语义：

- Half Message 发送失败时正文不保存，接口返回保存失败。
- 正文保存成功意味着向量化消息能够被提交。
- 消费最终失败时正文保留，任务进入 `FAILED`，允许用户手动重试。

### 5.3 手动重试与超时恢复

手动重试和超时恢复使用同一套事务消息入口：

- 本地事务执行严格状态 CAS。
- 成功后递增 `vectorTaskVersion` 并提交新消息。
- 旧消息、旧消费者和旧死信因任务版本不匹配无法修改最新任务。

AI 命名不影响正文与向量一致性，继续使用普通消息。

## 6. 消费者、死信与向量切换

### 6.1 消费者处理流程

1. 校验知识文件归属、正文版本和任务版本。
2. CAS 执行 `PENDING -> PROCESSING`，记录处理开始时间。
3. 使用版本化 ID 写入新向量。
4. CAS 执行 `PROCESSING -> SYNCED`：
   - 回写新 `embedding_id`。
   - 回写 `vector_version`。
   - 清空处理开始时间、错误信息和重试次数。
5. 成功切换后尽力删除旧 `embedding_id`。

若完成 CAS 失败，消费者只能删除自己生成的临时向量，不得删除数据库记录的当前有效向量。

### 6.2 消费失败

- 可恢复异常：使用 CAS 将当前任务恢复为 `PENDING`，抛出异常触发 RocketMQ 重试。
- 不可恢复异常或重试耗尽：仅允许完全匹配当前正文版本和任务版本的 `PROCESSING` 任务进入 `FAILED`。
- 旧死信不得覆盖新任务、`PENDING` 新重试任务或不同任务版本。

### 6.3 旧向量清理

- 新向量成功切换后立即尝试删除旧向量。
- 删除失败只记录带业务标识的错误日志，不回滚已生效的新向量。
- 本次不新增独立向量清理任务表。
- 过期向量由检索结果版本校验隔离，后续可增加离线清理机制。

## 7. 检索结果有效版本校验

新增 `ActiveChunkVersionResolver` 接口，第一版提供数据库实现。

处理流程：

1. 向量库返回 TopK 结果。
2. 提取结果中的 `chunkId`、`contentVersion`、`vectorTaskVersion` 和向量 ID。
3. 使用一次批量查询按 `chunk_id IN (...)` 获取当前分块版本信息。
4. 仅保留与数据库当前 `embedding_id`、正文版本和任务版本完全一致的结果。

查询使用已有 `chunk_id` 唯一索引，不执行 N+1 查询。后续如出现性能瓶颈，可增加 Redis Resolver 实现，不修改检索业务调用方。

## 8. 超时恢复

新增分块任务恢复器，默认配置：

```yaml
knowledge:
  chunk:
    vector-task-timeout: 10m
    vector-recovery-interval: 1m
```

恢复器扫描：

- 超过任务超时时间仍未领取的 `PENDING`。
- 处理开始时间超过任务超时时间的 `PROCESSING`。

恢复时通过事务消息执行严格 CAS，创建新的任务版本。旧任务即使恢复执行，也无法更新数据库或影响当前有效向量。

前端同时为已超时任务提供人工“恢复任务”入口，作为自动恢复机制的兜底。

## 9. 常量、配置与魔法值治理

新增或调整：

- `ChunkVectorStatus`：状态枚举及合法迁移语义。
- `KnowledgeChunkMqConstant`：Topic、Tag、Consumer Group、DLQ Topic。
- `VectorMetadataKey`：补充分块版本 metadata key。
- `KnowledgeChunkConstraint`：名称、正文和错误信息长度限制。
- `KnowledgeChunkProperties`：任务超时、恢复周期和 AI 命名超时。

数据库布尔字段 `name_locked` 暂时保留 `TINYINT`，业务层通过统一转换方法处理，禁止散落裸 `0/1`。

前端集中定义向量状态、轮询间隔和状态展示元数据，移除具有业务含义的散落时间数值。

## 10. 前端交互

- 仅名称变化时，按钮文案为“保存名称”，不展示重新向量化提示。
- 正文变化时，按钮文案为“保存并重新向量化”。
- `FAILED` 状态显示“重新提交”。
- 超时 `PENDING/PROCESSING` 显示“恢复任务”。
- 正常 `PENDING/PROCESSING/SYNCED` 禁止手动重试。
- 切换分块、搜索、筛选和返回页面时，如存在未保存修改，需要用户确认。
- 详情请求使用 `AbortController` 或请求序号，旧请求不能覆盖当前分块。
- 使用统一轮询策略刷新任务和 AI 命名状态，移除固定的临时二次刷新。

## 11. 测试与验收

### 11.1 数据访问与状态机

- 非法来源状态无法迁移。
- 旧正文版本和旧任务版本 CAS 失败。
- 手动重试只能处理 `FAILED`。
- 死信无法覆盖新任务或不同任务版本。
- 超时恢复只能处理达到超时阈值的任务。

### 11.2 事务消息

- Half Message 发送失败时正文不保存。
- 本地事务成功时消息提交。
- 本地事务失败或 CAS 冲突时消息回滚。
- Broker 回查根据数据库当前版本提交或回滚。
- 手动重试和超时恢复产生新任务版本。

### 11.3 消费与检索

- 乱序消息和重复消息保持幂等。
- 成功后正确回写 `embedding_id`。
- 旧消费者无法覆盖新任务。
- 旧死信无法覆盖新任务。
- TopK 结果通过一次批量查询只保留当前有效版本。
- 旧向量删除失败时，当前有效向量仍正常参与检索。

### 11.4 前端

- 仅名称修改不触发向量化。
- 正文修改触发事务向量化任务。
- 未保存修改离开时进行确认。
- 快速切换分块时旧请求不会覆盖新详情。
- 根据状态和超时时间正确展示重试或恢复入口。

## 12. 实施优先级

1. 数据库字段、状态枚举、约束常量和严格 CAS。
2. RocketMQ 事务消息、本地事务和事务回查。
3. 消费者向量切换、死信处理和超时恢复。
4. 检索结果批量有效版本校验。
5. 前端保存语义、未保存确认和请求并发修复。
6. 全量自动化测试、构建和浏览器验证。
