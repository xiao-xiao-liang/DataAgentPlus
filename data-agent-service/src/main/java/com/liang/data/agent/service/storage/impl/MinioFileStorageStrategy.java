package com.liang.data.agent.service.storage.impl;

import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.enums.FileStorageType;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.service.storage.FileStorageStrategy;
import com.liang.data.agent.service.storage.StoredFile;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * MinIO 文件存储策略。
 *
 * <p>基于注入的单例 MinIO Client 写入兼容 S3 协议的对象存储，实现高效的连接池复用。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "data-agent.file-storage", name = "type", havingValue = "MINIO")
public class MinioFileStorageStrategy implements FileStorageStrategy {

    private final DataAgentProperties properties;
    private final MinioClient minioClient;
    private volatile boolean bucketVerified = false;

    @Override
    public FileStorageType supportType() {
        return FileStorageType.MINIO;
    }

    @Override
    public StoredFile upload(InputStream inputStream, long contentLength, String objectName, String contentType) {
        try {
            DataAgentProperties.MinioProperties minio = properties.getFileStorage().getMinio();
            ensureBucket(minioClient, minio);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minio.getBucket())
                    .object(objectName)
                    .stream(inputStream, contentLength, -1)
                    .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                    .build());
            return new StoredFile(supportType().name(), objectName, buildUrl(minio, objectName), contentLength);
        } catch (Exception e) {
            throw new ServiceException("MinIO 文件存储失败：" + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    @Override
    public InputStream openStream(String objectName) {
        try {
            DataAgentProperties.MinioProperties minio = properties.getFileStorage().getMinio();
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minio.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new ServiceException("读取 MinIO 文件失败：" + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            DataAgentProperties.MinioProperties minio = properties.getFileStorage().getMinio();
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minio.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new ServiceException("删除 MinIO 文件失败：" + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void ensureBucket(MinioClient client, DataAgentProperties.MinioProperties minio) throws Exception {
        if (bucketVerified) {
            return;
        }
        synchronized (this) {
            if (!bucketVerified) {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(minio.getBucket()).build());
                if (!exists && minio.isAutoCreateBucket()) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(minio.getBucket()).build());
                }
                if (!exists && !minio.isAutoCreateBucket()) {
                    throw new ServiceException("MinIO 存储桶不存在：" + minio.getBucket(), BaseErrorCode.CLIENT_ERROR);
                }
                bucketVerified = true;
            }
        }
    }

    private String buildUrl(DataAgentProperties.MinioProperties minio, String objectName) {
        String endpoint = StringUtils.hasText(minio.getPublicEndpoint()) ? minio.getPublicEndpoint() : minio.getEndpoint();
        return endpoint.replaceAll("/+$", "") + "/" + minio.getBucket() + "/" + objectName;
    }
}
