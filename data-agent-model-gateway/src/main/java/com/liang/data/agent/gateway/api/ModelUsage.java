package com.liang.data.agent.gateway.api;

/**
 * 模型 Token 使用量，记录输入、输出及总 Token 数。
 *
 * @param inputTokens 输入 Token 数
 * @param outputTokens 输出 Token 数
 * @param totalTokens 总 Token 数
 */
public record ModelUsage(long inputTokens, long outputTokens, long totalTokens) {

    public ModelUsage {
        // 1. 校验 Token 数不能为负，避免用量统计出现无效值。
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("Token数量不能为负");
        }
        // 2. 校验总 Token 数必须等于输入与输出 Token 之和。
        long expectedTotalTokens;
        try {
            expectedTotalTokens = Math.addExact(inputTokens, outputTokens);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("总Token数量超出范围", exception);
        }
        if (totalTokens != expectedTotalTokens) {
            throw new IllegalArgumentException("总Token数量必须等于输入Token与输出Token之和");
        }
    }
}
