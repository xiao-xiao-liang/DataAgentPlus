package com.liang.data.agent.common.config;

import com.liang.data.agent.common.enums.LlmServiceMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
     * 连接池配置
     */
    private PoolProperties pool = new PoolProperties();

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
}
