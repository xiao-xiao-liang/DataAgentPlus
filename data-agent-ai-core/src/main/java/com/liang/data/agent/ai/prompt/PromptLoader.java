package com.liang.data.agent.ai.prompt;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板加载器
 *
 * <p>从 classpath 的 prompts/ 目录加载 .txt 模板文件, 带线程安全缓存</p>
 */
@Slf4j
public final class PromptLoader {

    private static final String PROMPTS_DIR = "prompts/";
    private static final Map<String, String> PROMPT_CACHE = new ConcurrentHashMap<>();

    private PromptLoader() {
    }

    /**
     * 加载 Prompt 模板
     *
     * @param promptName 模板名称 (不含路径和后缀), 如 "sql_generator"
     * @return 模板内容
     * @throws ServiceException 文件不存在或读取失败
     */
    public static String load(String promptName) {
        return PROMPT_CACHE.computeIfAbsent(promptName, name -> {
            String fileName = PROMPTS_DIR + name + ".txt";
            try (InputStream is = PromptLoader.class.getClassLoader().getResourceAsStream(fileName)) {
                if (is == null) {
                    throw new IOException("Prompt 文件不存在: " + fileName);
                }
                String content = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                log.debug("加载 Prompt 模板: {} ({} 字符)", name, content.length());
                return content;
            } catch (Exception e) {
                log.error("加载 Prompt 模板失败: {}", name, e);
                throw new ServiceException("加载 Prompt 模板失败: " + name, e, BaseErrorCode.SERVICE_ERROR);
            }
        });
    }

    /**
     * 清除缓存 (热更新场景)
     */
    public static void clearCache() {
        PROMPT_CACHE.clear();
    }

    /**
     * 获取缓存大小
     */
    public static int getCacheSize() {
        return PROMPT_CACHE.size();
    }
}
