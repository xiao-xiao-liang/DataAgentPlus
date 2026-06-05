package com.liang.data.agent.service.storage;

import com.liang.data.agent.common.config.DataAgentProperties;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端单例配置类。
 *
 * @author 资深Java架构师
 */
@Configuration
@ConditionalOnProperty(prefix = "data-agent.file-storage", name = "type", havingValue = "MINIO")
public class MinioConfiguration {

    @Bean
    public MinioClient minioClient(DataAgentProperties properties) {
        DataAgentProperties.MinioProperties minio = properties.getFileStorage().getMinio();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }
}
