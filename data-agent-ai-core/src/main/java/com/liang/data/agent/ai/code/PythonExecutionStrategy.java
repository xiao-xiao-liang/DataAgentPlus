package com.liang.data.agent.ai.code;

import com.liang.data.agent.ai.code.model.TaskResponse;

/**
 * Python 脚本执行策略接口
 */
public interface PythonExecutionStrategy {

    /**
     * 获取当前策略执行的优先级（数值越小越优先执行）
     *
     * @return 优先级数值
     */
    int getOrder();

    /**
     * 判断当前环境该执行策略是否可用
     *
     * @return 如果可用返回 true，否则返回 false
     */
    boolean isAvailable();

    /**
     * 执行 Python 代码
     *
     * @param code           生成的 Python 代码
     * @param inputJson      标准输入的 SQL 数据集 JSON 字符串
     * @param timeoutSeconds 最大超时限制（秒）
     * @return 执行响应包装模型
     */
    TaskResponse execute(String code, String inputJson, int timeoutSeconds);
}
