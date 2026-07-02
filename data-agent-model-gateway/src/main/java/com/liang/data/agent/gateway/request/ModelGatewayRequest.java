package com.liang.data.agent.gateway.request;

import com.liang.data.agent.gateway.prompt.ModelPrompt;

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

    /**
     * 要求请求调用模式与入口期望一致。
     *
     * @param expected 入口期望的调用模式
     */
    public void requireMode(ModelCallMode expected) {
        // 1. 校验入口期望模式，避免调用方传入空期望导致误判。
        Objects.requireNonNull(expected, "期望调用模式不能为空");
        // 2. 对比请求模式与入口模式，显式拒绝 call/stream 混用。
        if (mode != expected) {
            throw new IllegalArgumentException("调用模式不匹配，期望：" + expected + "，实际：" + mode);
        }
    }
}
