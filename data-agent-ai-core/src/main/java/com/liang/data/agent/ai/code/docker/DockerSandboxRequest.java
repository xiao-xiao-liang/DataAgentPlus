package com.liang.data.agent.ai.code.docker;

/**
 * Docker Python 沙箱执行请求
 *
 * @param image                  沙箱镜像
 * @param code                   Python 脚本
 * @param inputJson              标准输入 JSON
 * @param timeoutSeconds         超时时间
 * @param memoryMb               内存限制
 * @param nanoCpus               CPU 限制
 * @param pidsLimit              最大进程数
 * @param user                   容器运行用户
 * @param networkEnabled         是否允许网络
 * @param readonlyRootFilesystem 是否启用只读根文件系统
 * @param dropAllCapabilities    是否移除全部能力
 * @param noNewPrivileges        是否禁止获取新权限
 */
public record DockerSandboxRequest(
        String image,
        String code,
        String inputJson,
        int timeoutSeconds,
        long memoryMb,
        long nanoCpus,
        long pidsLimit,
        String user,
        boolean networkEnabled,
        boolean readonlyRootFilesystem,
        boolean dropAllCapabilities,
        boolean noNewPrivileges
) {
}
