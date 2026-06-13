package com.liang.data.agent.ai.code.docker;

/**
 * Docker Python 沙箱执行结果
 *
 * @param success  是否成功
 * @param stdout   标准输出
 * @param stderr   标准错误
 * @param exitCode 容器退出码
 * @param timedOut 是否执行超时
 */
public record DockerSandboxResult(
        boolean success,
        String stdout,
        String stderr,
        long exitCode,
        boolean timedOut
) {
}
