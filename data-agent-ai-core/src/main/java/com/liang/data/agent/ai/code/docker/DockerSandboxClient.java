package com.liang.data.agent.ai.code.docker;

/**
 * Docker Python 沙箱客户端
 *
 * <p>封装容器运行时细节，为 Python 执行策略提供稳定接口。</p>
 */
public interface DockerSandboxClient {

    /**
     * 判断 Docker 服务和指定镜像是否可用
     *
     * @param image 沙箱镜像
     * @return 可用时返回 true
     */
    boolean isAvailable(String image);

    /**
     * 在隔离容器中执行 Python 脚本
     *
     * @param request 沙箱执行请求
     * @return 沙箱执行结果
     */
    DockerSandboxResult execute(DockerSandboxRequest request);
}
