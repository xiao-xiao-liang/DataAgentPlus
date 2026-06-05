package com.liang.data.agent.service.storage;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件对象名称生成器。
 *
 * <p>为不同业务场景生成稳定、可分区的对象存储路径。</p>
 */
@Component
public class FileObjectNameGenerator {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 生成对象名称。
     *
     * @param scene    业务场景
     * @param ownerId  归属 ID
     * @param fileName 原始文件名
     * @return 对象名称
     */
    public String generate(String scene, Object ownerId, String fileName) {
        String safeScene = sanitize(scene);
        String safeOwner = sanitize(String.valueOf(ownerId));
        String safeFileName = sanitize(fileName);
        return "%s/%s/%s/%s-%s".formatted(
                safeScene,
                safeOwner,
                LocalDate.now().format(DAY_FORMATTER),
                UUID.randomUUID(),
                safeFileName
        );
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[\\\\:*?\"<>|]", "_")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
    }
}
