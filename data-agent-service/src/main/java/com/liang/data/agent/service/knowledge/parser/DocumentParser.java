package com.liang.data.agent.service.knowledge.parser;

import java.io.InputStream;

/**
 * 智能体知识文档解析器。
 *
 * <p>负责将上传文件转换为可切分、可向量化的纯文本。</p>
 */
public interface DocumentParser {

    /**
     * 解析文件内容。
     *
     * @param content  文件字节
     * @param fileName 文件名
     * @param fileType 文件后缀类型
     * @return 提取后的文本
     */
    String parse(byte[] content, String fileName, String fileType);

    /**
     * 解析文件流内容，避免大文件一次性加载进 JVM 堆内存。
     *
     * @param inputStream 文件内容输入流
     * @param fileName    文件名
     * @param fileType    文件后缀类型
     * @return 提取后的文本
     */
    String parse(InputStream inputStream, String fileName, String fileType);
}
