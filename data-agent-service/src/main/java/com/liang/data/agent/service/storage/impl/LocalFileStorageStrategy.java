package com.liang.data.agent.service.storage.impl;

import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.enums.FileStorageType;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.service.storage.FileStorageStrategy;
import com.liang.data.agent.service.storage.StoredFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地文件存储策略。
 *
 * <p>用于开发环境或未配置对象存储时的文件落盘。</p>
 */
@Component
public class LocalFileStorageStrategy implements FileStorageStrategy {

    private final DataAgentProperties properties;

    public LocalFileStorageStrategy(DataAgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public FileStorageType supportType() {
        return FileStorageType.LOCAL;
    }

    @Override
    public StoredFile upload(InputStream inputStream, long contentLength, String objectName, String contentType) {
        try {
            Path root = Path.of(properties.getFileStorage().getLocal().getRootPath()).toAbsolutePath().normalize();
            Path target = root.resolve(objectName).normalize();
            if (!target.startsWith(root)) {
                throw new ServiceException("对象名称非法", BaseErrorCode.CLIENT_ERROR);
            }
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(supportType().name(), objectName, target.toString(), contentLength);
        } catch (IOException e) {
            throw new ServiceException("本地文件存储失败：" + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    @Override
    public InputStream openStream(String objectName) {
        try {
            Path root = Path.of(properties.getFileStorage().getLocal().getRootPath()).toAbsolutePath().normalize();
            Path target = root.resolve(objectName).normalize();
            if (!target.startsWith(root)) {
                throw new ServiceException("对象名称非法", BaseErrorCode.CLIENT_ERROR);
            }
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new ServiceException("读取本地文件失败：" + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            Path root = Path.of(properties.getFileStorage().getLocal().getRootPath()).toAbsolutePath().normalize();
            Path target = root.resolve(objectName).normalize();
            if (!target.startsWith(root)) {
                throw new ServiceException("对象名称非法", BaseErrorCode.CLIENT_ERROR);
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new ServiceException("删除本地文件失败：" + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
