package com.liang.data.agent.gateway.prompt;

import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import com.liang.data.agent.gateway.error.ModelGatewayException;

import java.util.Map;

/**
 * 提示词模板注册中心。
 *
 * <p>负责按模板标识解析已经治理完成的提示词模板，并返回可直接用于模型调用的消息快照。
 * 接口仅定义模型网关与模板治理能力之间的契约，不约束模板存储、发布或加载方式。</p>
 */
public interface PromptTemplateRegistry {

    /**
     * 解析提示词模板。
     *
     * <p>调用方必须传入非空白模板标识；模板变量允许为 {@code null}，语义等同于空 Map。
     * 实现类不得修改传入的变量 Map，返回值必须是已经完成字段校验的不可变快照。
     * 当模板不存在或变量缺失时，应抛出 {@link ModelGatewayException} 并使用
     * {@link ModelGatewayErrorCode#INVALID_REQUEST}；当模板渲染结果无法转换为合法模型消息时，
     * 应抛出 {@link ModelGatewayException} 并使用 {@link ModelGatewayErrorCode#RESPONSE_INVALID}。</p>
     *
     * @param templateId 模板标识，不能为空白
     * @param variables 模板变量，允许为 null，语义等同空 Map
     * @return 已解析且不可变的提示词快照
     */
    ResolvedPrompt resolve(String templateId, Map<String, Object> variables);
}
