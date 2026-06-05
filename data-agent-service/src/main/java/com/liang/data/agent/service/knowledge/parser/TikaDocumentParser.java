package com.liang.data.agent.service.knowledge.parser;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 基于 Apache Tika 的智能体知识文档解析器。
 *
 * <p>文本类文件直接按 UTF-8 读取，其他常见办公文档交由 Tika 提取文本。</p>
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    private static final Tika TIKA = new Tika();
    private static final int MAX_PARSED_TEXT_LENGTH = 500_000;
    private static final List<String> TEXT_FILE_TYPES = List.of("txt", "md", "markdown", "csv", "json", "sql", "log");

    static {
        TIKA.setMaxStringLength(MAX_PARSED_TEXT_LENGTH);
    }

    @Override
    public String parse(byte[] content, String fileName, String fileType) {
        if (content == null || content.length == 0) {
            throw new ServiceException("文件内容不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        return parse(new ByteArrayInputStream(content), fileName, fileType);
    }

    @Override
    public String parse(InputStream inputStream, String fileName, String fileType) {
        if (inputStream == null) {
            throw new ServiceException("文件内容流不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        String normalizedType = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        try {
            String text;
            if (TEXT_FILE_TYPES.contains(normalizedType)) {
                // 文本类文件可以直接按 UTF-8 读取
                byte[] content = inputStream.readAllBytes();
                text = normalizeLineBreak(new String(content, StandardCharsets.UTF_8));
            } else {
                // Tika 原生支持流式解析提取
                text = cleanup(TIKA.parseToString(inputStream));
            }
            if (!StringUtils.hasText(text)) {
                throw new ServiceException("文件解析后内容为空", BaseErrorCode.CLIENT_ERROR);
            }
            return text;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("知识文件解析失败, fileName={}, fileType={}", fileName, fileType, e);
            throw new ServiceException("知识文件解析失败：" + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 清洗 Tika 解析后的文本，保留段落结构并压缩无意义空白。
     */
    private String cleanup(String text) {
        if (text == null) {
            return "";
        }
        return normalizeLineBreak(text)
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 统一不同系统的换行符，避免影响后续按位置切分。
     *
     * @param text 原始文本
     * @return 换行符归一化后的文本
     */
    private String normalizeLineBreak(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
