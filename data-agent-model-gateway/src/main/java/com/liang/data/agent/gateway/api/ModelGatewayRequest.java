package com.liang.data.agent.gateway.api;

import java.util.Map;
import java.util.Objects;

/**
 * 模型网关请求，聚合场景、提示词、调用方式、约束与业务标签。
 *
 * @param sceneCode 场景编码
 * @param prompt 模型提示词
 * @param mode 调用方式
 * @param constraints 调用约束
 * @param tags 业务标签
 */
public record ModelGatewayRequest(String sceneCode, ModelPrompt prompt, ModelCallMode mode,
                                  GatewayConstraints constraints, Map<String, String> tags) {

    public ModelGatewayRequest {
        // 1. 校验场景编码，确保网关能够识别调用场景。
        if (sceneCode == null || sceneCode.isBlank()) {
            throw new IllegalArgumentException("场景编码不能为空");
        }
        // 2. 校验提示词，模型调用必须具有明确输入。
        Objects.requireNonNull(prompt, "Prompt不能为空");
        // 3. 补充调用方式与调用约束的默认值。
        mode = mode == null ? ModelCallMode.BLOCK : mode;
        constraints = constraints == null ? GatewayConstraints.defaults() : constraints;
        // 4. 复制业务标签，避免请求构造后被外部修改。
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}
