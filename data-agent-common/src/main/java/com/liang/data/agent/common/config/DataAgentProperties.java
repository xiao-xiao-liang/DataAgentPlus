package com.liang.data.agent.common.config;

import com.liang.data.agent.common.enums.LlmServiceMode;
import com.liang.data.agent.common.enums.FileStorageType;
import com.liang.data.agent.common.ratelimit.ResourceType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * DataAgent 自定义配置属性
 *
 * <p>绑定 application.yml 中 data-agent.* 前缀</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "data-agent")
public class DataAgentProperties {

    /**
     * LLM 调用模式: STREAM (默认) 或 BLOCK
     */
    private LlmServiceMode llmServiceMode = LlmServiceMode.STREAM;

    /**
     * 向量存储配置
     */
    private VectorStoreProperties vectorStore = new VectorStoreProperties();

    /**
     * 文件存储配置
     */
    private FileStorageProperties fileStorage = new FileStorageProperties();

    /**
     * 连接池配置
     */
    private PoolProperties pool = new PoolProperties();

    /**
     * 资源门控配置
     */
    private ResourceGateProperties resourceGate = new ResourceGateProperties();

    /**
     * Python 代码执行器配置
     */
    private CodeExecutorProperties codeExecutor = new CodeExecutorProperties();

    /**
     * 非模型执行链路超时配置
     */
    private ExecutionTimeoutProperties executionTimeout = new ExecutionTimeoutProperties();

    /**
     * SQL 执行失败最大重试次数
     */
    private int maxSqlRetryCount = 10;

    /**
     * SQL 语义一致性优化最大次数
     */
    private int maxSqlOptimizeCount = 10;

    /**
     * SQL 优化分数阈值 (高于此阈值认为不需要优化)
     */
    private double sqlScoreThreshold = 0.95;

    /**
     * 最多保留的对话轮数
     */
    private int maxTurnHistory = 5;

    /**
     * 单次规划最大长度限制
     */
    private int maxPlanLength = 2000;

    /**
     * 每张表的最大预估列数
     */
    private int maxColumnsPerTable = 50;

    /**
     * Python 沙箱执行内存限制 (MB)
     */
    private String pythonMemoryLimit = "256";

    /**
     * Python 沙箱执行超时时间 (秒)
     */
    private String pythonTimeout = "30";

    /**
     * 是否启用SQL执行结果图表判断，默认启用
     */
    private boolean enableSqlResultChart = true;

    /**
     * 执行SQL结果图表化超时时间，默认3000ms
     */
    private Long enrichSqlResultTimeout = 3000L;

    /**
     * 非模型执行链路超时配置。
     */
    @Getter
    @Setter
    public static class ExecutionTimeoutProperties {

        /** 工作流总运行超时秒数 */
        private int workflowSeconds = 120;

        /** SQL 查询超时秒数 */
        private int sqlSeconds = 30;

        /** Python 执行超时秒数 */
        private int pythonSeconds = 30;
    }

    @Getter
    @Setter
    public static class VectorStoreProperties {
        /**
         * 相似度阈值, 过滤低于此阈值的文档
         */
        private double defaultSimilarityThreshold = 0.4;

        /**
         * 查询时返回的最大文档数量
         */
        private int defaultTopkLimit = 8;

        /**
         * 专门给表召回用的 topK
         */
        private int tableTopkLimit = 10;

        /**
         * 表召回的相似度阈值 (设低保证不遗漏)
         */
        private double tableSimilarityThreshold = 0.2;

        /**
         * 一次删除操作中最多删除的文档数量
         */
        private int batchDelTopkLimit = 5000;

        /**
         * 是否启用混合搜索 (Milvus + ES)
         */
        private boolean enableHybridSearch = false;
    }

    /**
     * Python 代码执行器配置
     */
    @Getter
    @Setter
    public static class CodeExecutorProperties {

        /**
         * 生产环境是否允许降级到本地 Python
         */
        private boolean allowLocalFallbackInProduction = false;

        /**
         * Docker 沙箱镜像
         */
        private String dockerImage = "data-agent-sandbox-py:3.10";

    }

    @Getter
    @Setter
    public static class FileStorageProperties {

        /**
         * 存储类型
         */
        private FileStorageType type = FileStorageType.LOCAL;

        /**
         * 本地文件存储配置
         */
        private LocalProperties local = new LocalProperties();

        /**
         * MinIO 文件存储配置
         */
        private MinioProperties minio = new MinioProperties();
    }

    @Getter
    @Setter
    public static class LocalProperties {

        /**
         * 本地存储根目录
         */
        private String rootPath = "data-agent-storage";
    }

    @Getter
    @Setter
    public static class MinioProperties {

        /**
         * MinIO S3 API 地址
         */
        private String endpoint = "http://101.132.158.169:9001";

        /**
         * 访问密钥
         */
        private String accessKey = "";

        /**
         * 访问密钥 Secret
         */
        private String secretKey = "";

        /**
         * 存储桶名称
         */
        private String bucket = "data-agent";

        /**
         * 对外访问地址
         */
        private String publicEndpoint = "";

        /**
         * 是否自动创建存储桶
         */
        private boolean autoCreateBucket = true;
    }

    @Getter
    @Setter
    public static class PoolProperties {
        /**
         * 初始化连接数
         */
        private int initialSize = 5;

        /**
         * 最小空闲连接数
         */
        private int minIdle = 5;

        /**
         * 最大活跃连接数
         */
        private int maxActive = 20;

        /**
         * 获取连接最大等待时间 (毫秒)
         */
        private long maxWait = 10_000L;
    }

    @Getter
    @Setter
    public static class ResourceGateProperties {

        /**
         * 各类资源的单实例并发上限
         */
        private Map<ResourceType, Integer> limits = defaultLimits();

        private static Map<ResourceType, Integer> defaultLimits() {
            Map<ResourceType, Integer> limits = new EnumMap<>(ResourceType.class);
            limits.put(ResourceType.CHAT_WORKFLOW, 10);
            limits.put(ResourceType.SSE_STREAM, 50);
            limits.put(ResourceType.LLM_CALL, 5);
            limits.put(ResourceType.SQL_EXECUTION, 10);
            limits.put(ResourceType.PYTHON_EXECUTION, 3);
            limits.put(ResourceType.KNOWLEDGE_JOB, 3);
            limits.put(ResourceType.KNOWLEDGE_VECTOR, 5);
            return limits;
        }
    }
}
