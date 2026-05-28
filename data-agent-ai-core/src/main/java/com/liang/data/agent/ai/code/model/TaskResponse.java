package com.liang.data.agent.ai.code.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 代码执行响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否执行成功 */
    private boolean success;

    /** 标准输出内容（成功时为结果 JSON） */
    private String stdout;

    /** 标准错误/异常信息 */
    private String stderr;

    /** 执行耗时（毫秒） */
    private long executionTimeMs;

    public static TaskResponse success(String stdout, long timeMs) {
        return TaskResponse.builder()
                .success(true)
                .stdout(stdout)
                .executionTimeMs(timeMs)
                .build();
    }

    public static TaskResponse failure(String stdout, String stderr, long timeMs) {
        return TaskResponse.builder()
                .success(false)
                .stdout(stdout)
                .stderr(stderr)
                .executionTimeMs(timeMs)
                .build();
    }

    public static TaskResponse error(String message) {
        return TaskResponse.builder()
                .success(false)
                .stderr(message)
                .build();
    }
}
