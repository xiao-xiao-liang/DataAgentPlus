package com.liang.data.agent.service.knowledge.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.data.agent.common.enums.KnowledgeType;
import com.liang.data.agent.common.enums.SplitterType;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.common.util.BeanUtil;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeJobEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeJobMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.AgentKnowledgeService;
import com.liang.data.agent.service.knowledge.job.KnowledgeJobAsyncPublisher;
import com.liang.data.agent.service.knowledge.vo.AgentKnowledgeChunkVO;
import com.liang.data.agent.service.knowledge.vo.AgentKnowledgeVO;
import com.liang.data.agent.service.knowledge.vo.KnowledgeJobQueueVO;
import com.liang.data.agent.service.storage.FileObjectNameGenerator;
import com.liang.data.agent.service.storage.FileStorageService;
import com.liang.data.agent.service.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.liang.data.agent.common.constant.KnowledgeConstant.*;

/**
 * 智能体知识源服务实现。
 *
 * <p>负责知识文档元数据管理，并继承 ServiceImpl 以对齐规范，通过 Spring 事件触发异步解析等任务。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentKnowledgeServiceImpl extends ServiceImpl<AgentKnowledgeMapper, AgentKnowledgeEntity> implements AgentKnowledgeService {

    private final AgentKnowledgeChunkMapper agentKnowledgeChunkMapper;
    private final AgentKnowledgeJobMapper agentKnowledgeJobMapper;
    private final FileStorageService fileStorageService;
    private final FileObjectNameGenerator objectNameGenerator;
    private final KnowledgeJobAsyncPublisher jobAsyncPublisher;
    private static final long MAX_UPLOAD_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of(
            "md", "markdown", "txt", "log", "sql", "csv", "json", "pdf", "doc", "docx", "xls", "xlsx"
    );

    private AgentKnowledgeServiceImpl self;

    @Autowired
    public void setSelf(@Lazy AgentKnowledgeServiceImpl self) {
        this.self = self;
    }

    @Override
    public List<AgentKnowledgeVO> listByAgent(Integer agentId) {
        validateAgentId(agentId);
        return lambdaQuery()
                .eq(AgentKnowledgeEntity::getAgentId, agentId)
                .orderByDesc(AgentKnowledgeEntity::getUpdateTime)
                .list()
                .stream().map(this::toVO)
                .toList();
    }

    /**
     * 上传接口逻辑：将远程网络 RPC 剥离在事务外部，防高并发数据库连接池耗尽风险
     */
    @Override
    public AgentKnowledgeVO upload(Integer agentId, String userId, String title, String sourceFilename, InputStream inputStream,
                                   long contentLength, String splitterType) {
        validateAgentId(agentId);
        validateUpload(sourceFilename, inputStream, contentLength);
        String normalizedUserId = normalizeUserId(userId);

        String fileType = resolveFileType(sourceFilename);
        validateFileType(fileType);

        // 远程文件服务器上传（事务外网络 I/O）
        StoredFile storedFile = null;
        try {
            storedFile = fileStorageService.upload(
                    inputStream,
                    contentLength,
                    objectNameGenerator.generate(STORAGE_PREFIX_KNOWLEDGE, agentId, sourceFilename),
                    resolveContentType(fileType)
            );
            return self.saveKnowledgeMetadataInTransaction(agentId, normalizedUserId, title, sourceFilename, contentLength, fileType, splitterType, storedFile.objectName());
        } catch (RuntimeException e) {
            if (storedFile != null) {
                try {
                    fileStorageService.delete(storedFile.objectName());
                } catch (RuntimeException cleanupException) {
                    e.addSuppressed(cleanupException);
                }
            }
            throw e;
        }
    }

    /**
     * 细粒度持久化事务
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentKnowledgeVO saveKnowledgeMetadataInTransaction(Integer agentId, String userId, String title, String sourceFilename,
                                                               long fileSize, String fileType, String splitterType, String filePath) {
        AgentKnowledgeEntity entity = buildPendingEntity(agentId, title, sourceFilename, fileSize, fileType, splitterType);
        entity.setFilePath(filePath);
        save(entity);

        AgentKnowledgeJobEntity job = buildPendingJob(entity, userId, JOB_TYPE_UPLOAD_VECTORIZE);
        agentKnowledgeJobMapper.insert(job);
        jobAsyncPublisher.publish(job.getId());
        return toVO(entity).setJobQueue(buildJobQueue(job));
    }

    @Override
    public List<AgentKnowledgeChunkVO> listChunks(Integer agentId, Integer id) {
        validateAgentId(agentId);
        getKnowledge(agentId, id);
        return agentKnowledgeChunkMapper.selectList(Wrappers.lambdaQuery(AgentKnowledgeChunkEntity.class)
                        .eq(AgentKnowledgeChunkEntity::getKnowledgeId, id)
                        .orderByAsc(AgentKnowledgeChunkEntity::getChunkOrder))
                .stream()
                .map(this::toChunkVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer agentId, Integer id) {
        validateAgentId(agentId);
        AgentKnowledgeEntity entity = getKnowledge(agentId, id);

        // 状态判定防护：避免并发重复删除提交重复清理任务
        if (KNOWLEDGE_STATUS_DELETING.equals(entity.getEmbeddingStatus())) {
            return;
        }

        entity.setEmbeddingStatus(KNOWLEDGE_STATUS_DELETING);
        entity.setUpdateTime(LocalDateTime.now());
        updateById(entity);

        AgentKnowledgeJobEntity job = buildPendingJob(entity, "default-user", JOB_TYPE_DELETE_CLEANUP);
        agentKnowledgeJobMapper.insert(job);
        jobAsyncPublisher.publish(job.getId());
    }

    /**
     * 构建待处理的知识文档实体。
     */
    private AgentKnowledgeEntity buildPendingEntity(Integer agentId, String title, String sourceFilename,
                                                    long fileSize, String fileType, String splitterType) {
        AgentKnowledgeEntity entity = new AgentKnowledgeEntity();
        entity.setAgentId(agentId);
        entity.setTitle(StringUtils.hasText(title) ? title.trim() : sourceFilename);
        entity.setType(KnowledgeType.DOCUMENT.getCode());
        entity.setIsRecall(1);
        entity.setEmbeddingStatus(KNOWLEDGE_STATUS_PENDING);
        entity.setSourceFilename(sourceFilename);
        entity.setFileSize(fileSize);
        entity.setFileType(fileType);
        entity.setSplitterType(normalizeSplitterType(splitterType));
        entity.setIsResourceCleaned(0);
        entity.setDelFlag(0);
        return entity;
    }

    /**
     * 构建待执行的异步任务。
     */
    private AgentKnowledgeJobEntity buildPendingJob(AgentKnowledgeEntity entity, String userId, String jobType) {
        AgentKnowledgeJobEntity job = AgentKnowledgeJobEntity.builder()
                .knowledgeId(entity.getId())
                .agentId(entity.getAgentId())
                .userId(normalizeUserId(userId))
                .jobType(jobType)
                .status(JOB_STATUS_PENDING)
                .retryCount(0)
                .maxRetryCount(DEFAULT_MAX_RETRY_COUNT)
                .build();
        return job;
    }

    private KnowledgeJobQueueVO buildJobQueue(AgentKnowledgeJobEntity job) {
        if (job == null || job.getId() == null) {
            return null;
        }
        return new KnowledgeJobQueueVO()
                .setJobId(job.getId())
                .setStatus(job.getStatus())
                .setAheadTaskCount(agentKnowledgeJobMapper.countAheadJobs(job.getAgentId(), job.getJobType(), job.getId()))
                .setAheadUserCount(agentKnowledgeJobMapper.countAheadUsers(job.getAgentId(), job.getJobType(), job.getId()));
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim() : "default-user";
    }

    private AgentKnowledgeEntity getKnowledge(Integer agentId, Integer id) {
        if (id == null) {
            throw new ServiceException("知识文件 ID 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        return Optional.ofNullable(baseMapper.selectOne(Wrappers.lambdaQuery(AgentKnowledgeEntity.class)
                        .eq(AgentKnowledgeEntity::getId, id)
                        .eq(AgentKnowledgeEntity::getAgentId, agentId)))
                .orElseThrow(() -> new ServiceException("知识文件不存在", BaseErrorCode.CLIENT_ERROR));
    }

    private void validateUpload(String sourceFilename, InputStream inputStream, long contentLength) {
        if (!StringUtils.hasText(sourceFilename)) {
            throw new ServiceException("文件名不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        if (inputStream == null || contentLength <= 0) {
            throw new ServiceException("文件内容不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        if (contentLength > MAX_UPLOAD_FILE_SIZE) {
            throw new ServiceException("文件大小不能超过 50MB", BaseErrorCode.CLIENT_ERROR);
        }
    }

    private void validateAgentId(Integer agentId) {
        if (agentId == null || agentId <= 0) {
            throw new ServiceException("agentId 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
    }

    private String resolveFileType(String sourceFilename) {
        int index = sourceFilename.lastIndexOf('.');
        return index >= 0 ? sourceFilename.substring(index + 1).toLowerCase(Locale.ROOT) : "txt";
    }

    private void validateFileType(String fileType) {
        if (!SUPPORTED_FILE_TYPES.contains(fileType)) {
            throw new ServiceException("不支持的文件类型：" + fileType, BaseErrorCode.CLIENT_ERROR);
        }
    }

    private String resolveContentType(String fileType) {
        return switch (fileType) {
            case "md", "markdown" -> "text/markdown";
            case "txt", "log", "sql" -> "text/plain";
            case "csv" -> "text/csv";
            case "json" -> "application/json";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    private String normalizeSplitterType(String splitterType) {
        if (!StringUtils.hasText(splitterType)) {
            return SplitterType.TITLE.getCode();
        }
        String normalized = splitterType.trim().toLowerCase(Locale.ROOT);
        if (!SplitterType.isValid(normalized)) {
            throw new ServiceException("不支持的分块策略：" + splitterType, BaseErrorCode.CLIENT_ERROR);
        }
        return normalized;
    }

    private AgentKnowledgeChunkVO toChunkVO(AgentKnowledgeChunkEntity entity) {
        return BeanUtil.copyProperties(entity, AgentKnowledgeChunkVO.class)
                .setId(entity.getChunkId())
                .setSeq(entity.getChunkOrder())
                .setLength(entity.getContentLength());
    }

    private AgentKnowledgeVO toVO(AgentKnowledgeEntity entity) {
        return BeanUtil.copyProperties(entity, AgentKnowledgeVO.class);
    }
}
