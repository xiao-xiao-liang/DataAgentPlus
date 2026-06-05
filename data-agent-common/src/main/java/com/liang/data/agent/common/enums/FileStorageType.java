package com.liang.data.agent.common.enums;

/**
 * 文件存储类型枚举。
 *
 * <p>对应 application.yml 中 data-agent.file-storage.type 配置，用于统一描述本地存储、对象存储和兼容 S3 协议的存储平台。</p>
 */
public enum FileStorageType {

    /**
     * 本地文件系统存储。
     */
    LOCAL,

    /**
     * MinIO 对象存储。
     */
    MINIO,

    /**
     * 通用 OSS 对象存储。
     */
    OSS,

    /**
     * RustFS 对象存储。
     */
    RUSTFS,

    /**
     * 阿里云 OSS 对象存储。
     */
    ALIYUN_OSS,

    /**
     * 腾讯云 COS 对象存储。
     */
    TENCENT_COS,

    /**
     * 华为云 OBS 对象存储。
     */
    HUAWEI_OBS,

    /**
     * AWS S3 对象存储。
     */
    AWS_S3,

    /**
     * Azure Blob Storage 对象存储。
     */
    AZURE_BLOB,

    /**
     * Google Cloud Storage 对象存储。
     */
    GOOGLE_CLOUD_STORAGE,

    /**
     * 七牛云 Kodo 对象存储。
     */
    QINIU_KODO,

    /**
     * 百度云 BOS 对象存储。
     */
    BAIDU_BOS,

    /**
     * 京东云 OSS 对象存储。
     */
    JD_OSS,

    /**
     * Ceph RGW 对象存储。
     */
    CEPH_RGW,

    /**
     * SeaweedFS 文件存储。
     */
    SEAWEEDFS,

    /**
     * FastDFS 文件存储。
     */
    FASTDFS,

    /**
     * FTP 文件存储。
     */
    FTP,

    /**
     * SFTP 文件存储。
     */
    SFTP,

    /**
     * WebDAV 文件存储。
     */
    WEBDAV
}
