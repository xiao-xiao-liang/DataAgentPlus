package com.liang.data.agent.gateway.prompt;

import java.util.Map;

/**
 * 提示词模板注册中心。
 *
 * <p>仅负责解析已经治理完成的提示词模板，并返回可直接用于模型调用的消息快照；
 * 本阶段不绑定 Nacos 或其他具体配置中心实现。</p>
 */
public interface PromptTemplateRegistry {

    /**
     * 解析提示词模板。
     *
     * @param templateId 模板标识
     * @param variables 模板变量
     * @return 已解析提示词
     */
    ResolvedPrompt resolve(String templateId, Map<String, Object> variables);
}
