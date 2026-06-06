package com.liang.data.agent.service.knowledge.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.common.constant.VectorMetadataKey;
import com.liang.data.agent.common.enums.VectorType;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.common.util.BeanUtil;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeJobEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeJobMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.parser.DocumentParser;
import com.liang.data.agent.service.knowledge.chunk.KnowledgeChunkAsyncPublisher;
import com.liang.data.agent.service.knowledge.splitter.AgentKnowledgeSplitParam;
import com.liang.data.agent.service.knowledge.splitter.AgentKnowledgeTextSplitter;
import com.liang.data.agent.service.knowledge.vo.AgentKnowledgeChunkVO;
import com.liang.data.agent.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.liang.data.agent.common.constant.KnowledgeConstant.*;
import static com.liang.data.agent.common.constant.VectorMetadataKey.*;

/**
 * 智能体知识异步任务执行器。
 *
 * <p>消费知识任务表中的上传向量化和删除清理任务，并通过幂等清理保证重试不会产生重复资源。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentKnowledgeJobExecutor {

    private final AgentKnowledgeMapper agentKnowledgeMapper;
    private final AgentKnowledgeChunkMapper agentKnowledgeChunkMapper;
    private final AgentKnowledgeJobMapper agentKnowledgeJobMapper;
    private final AgentVectorStoreService vectorStoreService;
    private final DocumentParser documentParser;
    private final AgentKnowledgeTextSplitter textSplitter;
    private final FileStorageService fileStorageService;
    private final KnowledgeChunkAsyncPublisher chunkAsyncPublisher;

    private static final String INSTANCE_ID = System.getenv("HOSTNAME") != null 
            ? System.getenv("HOSTNAME") 
            : "local-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    private AgentKnowledgeJobExecutor self;

    @Autowired
    public void setSelf(@Lazy AgentKnowledgeJobExecutor self) {
        this.self = self;
    }

    /**
     * 执行指定知识任务。
     *
     * @param jobId 任务 ID
     */
    public void execute(Long jobId) {
        if (jobId == null) {
            return;
        }
        AgentKnowledgeJobEntity job = agentKnowledgeJobMapper.selectById(jobId);
        if (job == null || JOB_STATUS_SUCCESS.equals(job.getStatus())) {
            return;
        }
        // 悲观抢占：只有修改影响行数 > 0 才能说明加锁成功，防分布式多实例并发重复执行
        if (!markJobRunningWithLock(job)) {
            log.info("知识异步任务已被其他节点抢占或执行完成，jobId={}", jobId);
            return;
        }
        try {
            if (JOB_TYPE_UPLOAD_VECTORIZE.equals(job.getJobType())) {
                executeUploadVectorize(job);
            } else if (JOB_TYPE_DELETE_CLEANUP.equals(job.getJobType())) {
                // 通过 self 代理调用，确保 executeDeleteCleanup 上的 @Transactional 生效
                executeDeleteCleanup(job);
            } else {
                throw new ServiceException("不支持的知识任务类型：" + job.getJobType(), BaseErrorCode.CLIENT_ERROR);
            }
            markJobSuccess(job);
        } catch (Exception e) {
            log.error("知识异步任务执行失败，jobId={}, jobType={}", job.getId(), job.getJobType(), e);
            markJobFailed(job, e);
        }
    }

    /**
     * 业务骨架主方法，不开启数据库全局事务，防止网络 I/O 阻塞数据库连接池。
     */
    protected void executeUploadVectorize(AgentKnowledgeJobEntity job) throws Exception {
        AgentKnowledgeEntity entity = getKnowledge(job.getKnowledgeId());

        // 1. 开启子事务：准备状态并清空旧数据
        self.prepareUpload(entity);

        // 2. 事务外长耗时：文档流式解析，不经过全量 byte 内存数组
        String parsedText;
        try (InputStream inputStream = fileStorageService.openStream(entity.getFilePath())) {
            parsedText = documentParser.parse(inputStream, entity.getSourceFilename(), entity.getFileType());
        }
        entity.setContent(parsedText);

        List<AgentKnowledgeChunkVO> chunks = textSplitter.split(
                entity.getId(),
                parsedText,
                AgentKnowledgeSplitParam.defaults(entity.getSplitterType())
        );
        if (chunks.isEmpty()) {
            throw new ServiceException("文档切分结果为空", BaseErrorCode.CLIENT_ERROR);
        }

        // 3. 开启子事务：批量保存分块数据
        List<AgentKnowledgeChunkEntity> chunkEntities = self.saveChunksInTransaction(entity, chunks);

        // 4. 事务外长耗时：远程网络推送向量数据
        vectorStoreService.addDocuments(entity.getAgentId().toString(), toDocuments(entity, chunkEntities));

        // 5. 开启子事务：更新分块物理标志，最终完成知识库状态提交
        self.completeUpload(entity, chunkEntities);
        publishInitialChunkNames(entity, chunkEntities);
    }

    /**
     * 准备上传：清空数据并更新状态（事务控制）
     */
    @Transactional(rollbackFor = Exception.class)
    public void prepareUpload(AgentKnowledgeEntity entity) {
        entity.setEmbeddingStatus(KNOWLEDGE_STATUS_PROCESSING);
        entity.setErrorMsg(null);
        entity.setUpdateTime(LocalDateTime.now());
        agentKnowledgeMapper.updateById(entity);

        cleanupVectorAndChunks(entity);
    }

    /**
     * 事务内批量保存分块到数据库（事务控制）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<AgentKnowledgeChunkEntity> saveChunksInTransaction(AgentKnowledgeEntity entity, List<AgentKnowledgeChunkVO> chunks) {
        return saveChunks(entity, chunks);
    }

    /**
     * 事务内更新分块为已写入向量库，并最终更新知识库状态为完成（事务控制）
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeUpload(AgentKnowledgeEntity entity, List<AgentKnowledgeChunkEntity> chunkEntities) {
        markChunksVectorStored(chunkEntities);

        entity.setEmbeddingStatus(KNOWLEDGE_STATUS_COMPLETED);
        entity.setErrorMsg(null);
        entity.setUpdateTime(LocalDateTime.now());
        agentKnowledgeMapper.updateById(entity);
    }

    /**
     * 删除清理（事务控制）
     */
    public void executeDeleteCleanup(AgentKnowledgeJobEntity job) {
        AgentKnowledgeEntity entity = agentKnowledgeMapper.selectById(job.getKnowledgeId());
        if (entity == null) {
            return;
        }
        cleanupVectorAndChunks(entity);
        if (StringUtils.hasText(entity.getFilePath())) {
            fileStorageService.delete(entity.getFilePath());
        }
        self.finishDeleteCleanup(entity);
    }

    /**
     * 完成删除清理的数据库状态提交。
     *
     * @param entity 知识文件实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void finishDeleteCleanup(AgentKnowledgeEntity entity) {
        entity.setIsResourceCleaned(1);
        entity.setUpdateTime(LocalDateTime.now());
        agentKnowledgeMapper.updateById(entity);
        agentKnowledgeMapper.deleteById(entity.getId());
    }

    private AgentKnowledgeEntity getKnowledge(Integer knowledgeId) {
        return Optional.ofNullable(agentKnowledgeMapper.selectById(knowledgeId))
                .orElseThrow(() -> new ServiceException("知识文件不存在", BaseErrorCode.CLIENT_ERROR));
    }

    /**
     * 清理旧分块和旧向量，保证任务重试时不会重复入库。
     */
    private void cleanupVectorAndChunks(AgentKnowledgeEntity entity) {
        vectorStoreService.deleteDocumentsByMetadata(Map.of(
                AGENT_ID, entity.getAgentId().toString(),
                VECTOR_TYPE, VectorType.KNOWLEDGE.getCode(),
                "agentKnowledgeId", entity.getId().toString()
        ));
        agentKnowledgeChunkMapper.delete(new QueryWrapper<AgentKnowledgeChunkEntity>()
                .eq("knowledge_id", entity.getId()));
    }

    /**
     * 批量保存文档分块
     */
    private List<AgentKnowledgeChunkEntity> saveChunks(AgentKnowledgeEntity entity, List<AgentKnowledgeChunkVO> chunks) {
        List<AgentKnowledgeChunkEntity> chunkEntities = chunks.stream()
                .sorted(Comparator.comparing(AgentKnowledgeChunkVO::getSeq))
                .map(chunk -> buildChunkEntity(entity, chunk))
                .toList();

        // 利用 MyBatis-Plus 提供的 Db.saveBatch 静态批处理写入数据库，实现真正的批量保存
        Db.saveBatch(chunkEntities);
        return chunkEntities;
    }

    /**
     * 抽取转换逻辑，并使用 BeanUtil 进行同名属性拷贝
     */
    private AgentKnowledgeChunkEntity buildChunkEntity(AgentKnowledgeEntity entity, AgentKnowledgeChunkVO chunk) {
        AgentKnowledgeChunkEntity chunkEntity = BeanUtil.copyProperties(chunk, AgentKnowledgeChunkEntity.class);
        chunkEntity.setKnowledgeId(entity.getId());
        chunkEntity.setChunkId("knowledge-" + entity.getId() + "-chunk-" + chunk.getSeq());
        chunkEntity.setName(buildInitialChunkName(chunk));
        chunkEntity.setNameLocked(0);
        chunkEntity.setChunkOrder(chunk.getSeq());
        chunkEntity.setContentLength(chunk.getLength());
        chunkEntity.setContentVersion(1);
        chunkEntity.setVectorVersion(0);
        chunkEntity.setVectorStatus("PENDING");
        chunkEntity.setRetryCount(0);
        chunkEntity.setMetadata("{}");
        chunkEntity.setStatus(CHUNK_STATUS_SKIP_EMBEDDING);
        chunkEntity.setSkipEmbedding(0);
        chunkEntity.setDelFlag(0);
        chunkEntity.setCreateTime(LocalDateTime.now());
        chunkEntity.setUpdateTime(LocalDateTime.now());
        return chunkEntity;
    }

    /**
     * 为新分块生成可直接展示的大纲名称。
     *
     * <p>初始名称使用正文首行，后续用户可在分块工作台中手动修改或触发 AI 命名。</p>
     */
    private String buildInitialChunkName(AgentKnowledgeChunkVO chunk) {
        String content = Optional.ofNullable(chunk.getContent()).orElse("").trim();
        String firstLine = content.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("分块 #" + chunk.getSeq());
        return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
    }

    /**
     * 构建写入向量库的文档对象。
     */
    private List<Document> toDocuments(AgentKnowledgeEntity entity, List<AgentKnowledgeChunkEntity> chunks) {
        return chunks.stream()
                .sorted(Comparator.comparing(AgentKnowledgeChunkEntity::getChunkOrder))
                .filter(chunk -> chunk.getSkipEmbedding() == null || chunk.getSkipEmbedding() == 0)
                .map(chunk -> new Document(versionedVectorId(chunk), chunk.getContent(), Map.of(
                        AGENT_ID, entity.getAgentId().toString(),
                        VECTOR_TYPE, VectorType.KNOWLEDGE.getCode(),
                        NAME, entity.getTitle(),
                        DESCRIPTION, entity.getSourceFilename(),
                        "agentKnowledgeId", entity.getId().toString(),
                        "chunkId", chunk.getChunkId(),
                        "chunkOrder", chunk.getChunkOrder().toString(),
                        "splitterType", entity.getSplitterType()
                )))
                .toList();
    }

    /**
     * 标记分块已经写入向量库。
     */
    private void markChunksVectorStored(List<AgentKnowledgeChunkEntity> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (AgentKnowledgeChunkEntity chunk : chunks) {
            chunk.setStatus(CHUNK_STATUS_VECTOR_STORED);
            chunk.setEmbeddingId(versionedVectorId(chunk));
            chunk.setVectorVersion(chunk.getContentVersion());
            chunk.setVectorStatus("SYNCED");
            chunk.setRetryCount(0);
            chunk.setErrorMsg(null);
            chunk.setUpdateTime(LocalDateTime.now());
        }
        Db.updateBatchById(chunks);
    }

    private String versionedVectorId(AgentKnowledgeChunkEntity chunk) {
        return chunk.getChunkId() + "-v" + chunk.getContentVersion();
    }

    private void publishInitialChunkNames(AgentKnowledgeEntity knowledge, List<AgentKnowledgeChunkEntity> chunks) {
        for (AgentKnowledgeChunkEntity chunk : chunks) {
            try {
                chunkAsyncPublisher.publishGenerateName(
                        knowledge.getAgentId(), knowledge.getId(), chunk.getChunkId(), chunk.getContentVersion());
            } catch (RuntimeException exception) {
                log.warn("提交初始分块 AI 命名消息失败，保留首行名称：chunkId={}", chunk.getChunkId(), exception);
            }
        }
    }

    private boolean markJobRunningWithLock(AgentKnowledgeJobEntity job) {
        LocalDateTime now = LocalDateTime.now();
        int rows = agentKnowledgeJobMapper.update(null, new UpdateWrapper<AgentKnowledgeJobEntity>()
                .set("status", JOB_STATUS_RUNNING)
                .set("locked_by", INSTANCE_ID)
                .set("locked_until", now.plusMinutes(10))
                .set("update_time", now)
                .eq("id", job.getId())
                .and(wrapper -> wrapper
                        .in("status", List.of(JOB_STATUS_PENDING, JOB_STATUS_RETRYING))
                        .or(recoverWrapper -> recoverWrapper
                                .eq("status", JOB_STATUS_RUNNING)
                                .le("locked_until", now))));
        return rows > 0;
    }

    private void markJobRunning(AgentKnowledgeJobEntity job) {
        job.setStatus(JOB_STATUS_RUNNING);
        job.setLockedBy("local");
        job.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        job.setUpdateTime(LocalDateTime.now());
        agentKnowledgeJobMapper.updateById(job);
    }

    private void markJobSuccess(AgentKnowledgeJobEntity job) {
        job.setStatus(JOB_STATUS_SUCCESS);
        job.setErrorMsg(null);
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setUpdateTime(LocalDateTime.now());
        agentKnowledgeJobMapper.updateById(job);
    }

    private void markJobFailed(AgentKnowledgeJobEntity job, Exception e) {
        int retryCount = job.getRetryCount() == null ? 0 : job.getRetryCount();
        int maxRetryCount = job.getMaxRetryCount() == null ? 3 : job.getMaxRetryCount();
        retryCount++;
        job.setRetryCount(retryCount);
        job.setStatus(retryCount >= maxRetryCount ? JOB_STATUS_FAILED : JOB_STATUS_RETRYING);
        job.setNextRetryTime(LocalDateTime.now().plusMinutes(Math.min(retryCount, 5)));
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setErrorMsg(e.getMessage());
        job.setUpdateTime(LocalDateTime.now());
        agentKnowledgeJobMapper.updateById(job);

        AgentKnowledgeEntity entity = agentKnowledgeMapper.selectById(job.getKnowledgeId());
        if (entity == null) {
            return;
        }
        entity.setEmbeddingStatus(JOB_TYPE_DELETE_CLEANUP.equals(job.getJobType())
                ? KNOWLEDGE_STATUS_DELETE_FAILED
                : KNOWLEDGE_STATUS_FAILED);
        entity.setErrorMsg(e.getMessage());
        entity.setUpdateTime(LocalDateTime.now());
        agentKnowledgeMapper.updateById(entity);
    }
}
