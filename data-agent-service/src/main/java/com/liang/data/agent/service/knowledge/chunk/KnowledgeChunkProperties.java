package com.liang.data.agent.service.knowledge.chunk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 知识分块任务配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.chunk")
public class KnowledgeChunkProperties {
    private Duration vectorTaskTimeout = Duration.ofMinutes(10);
    private Duration vectorRecoveryInterval = Duration.ofMinutes(1);
    private Duration aiNameTimeout = Duration.ofSeconds(30);
    private int recoveryBatchSize = 100;
}
