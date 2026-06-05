package com.liang.data.agent.service.storage;

/**
 * 已存储文件信息。
 *
 * <p>用于向业务层返回对象存储 Key、访问地址和存储类型。</p>
 */
public record StoredFile(String storageType, String objectName, String url, long size) {
}
