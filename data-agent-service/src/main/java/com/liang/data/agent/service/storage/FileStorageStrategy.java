package com.liang.data.agent.service.storage;

import com.liang.data.agent.common.enums.FileStorageType;

import java.io.InputStream;

/**
 * 文件存储策略。
 *
 * <p>屏蔽本地磁盘、MinIO、云 OSS 等不同存储实现的差异。</p>
 */
public interface FileStorageStrategy {

    /**
     * 返回当前策略支持的存储类型。
     *
     * @return 存储类型
     */
    FileStorageType supportType();

    /**
     * 上传文件内容。
     *
     * @param inputStream 文件内容输入流
     * @param contentLength 文件内容长度
     * @param objectName 对象名称
     * @param contentType 内容类型
     * @return 已存储文件信息
     */
    StoredFile upload(InputStream inputStream, long contentLength, String objectName, String contentType);

    /**
     * 打开对象输入流。
     *
     * @param objectName 对象名称
     * @return 对象输入流
     */
    InputStream openStream(String objectName);

    /**
     * 删除对象。
     *
     * @param objectName 对象名称
     */
    void delete(String objectName);
}
