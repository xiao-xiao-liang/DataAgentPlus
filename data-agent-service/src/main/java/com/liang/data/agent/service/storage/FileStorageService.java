package com.liang.data.agent.service.storage;

import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.enums.FileStorageType;
import com.liang.data.agent.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用文件存储服务。
 *
 * <p>根据配置选择具体存储策略，业务模块不直接依赖本地磁盘或对象存储 SDK。</p>
 */
@Service
public class FileStorageService {

    private final DataAgentProperties properties;
    private final Map<FileStorageType, FileStorageStrategy> strategyMap;

    public FileStorageService(DataAgentProperties properties, List<FileStorageStrategy> strategies) {
        this.properties = properties;
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(FileStorageStrategy::supportType, Function.identity()));
    }

    /**
     * 上传文件到当前配置的存储后端。
     */
    public StoredFile upload(InputStream inputStream, long contentLength, String objectName, String contentType) {
        if (inputStream == null || contentLength <= 0) {
            throw new ServiceException("文件内容不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        if (!StringUtils.hasText(objectName)) {
            throw new ServiceException("对象名称不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        try (InputStream stream = inputStream) {
            return currentStrategy().upload(stream, contentLength, objectName, contentType);
        } catch (IOException e) {
            throw new ServiceException("关闭文件流失败：" + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 从当前配置的存储后端读取文件。
     */
    public InputStream openStream(String objectName) {
        return currentStrategy().openStream(objectName);
    }

    /**
     * 从当前配置的存储后端删除文件。
     */
    public void delete(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        currentStrategy().delete(objectName);
    }

    private FileStorageStrategy currentStrategy() {
        FileStorageType type = properties.getFileStorage().getType();
        FileStorageStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new ServiceException("不支持的文件存储类型：" + type, BaseErrorCode.CLIENT_ERROR);
        }
        return strategy;
    }
}
