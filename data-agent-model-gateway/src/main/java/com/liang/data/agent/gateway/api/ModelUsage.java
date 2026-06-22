package com.liang.data.agent.gateway.api;

/**
 * 模型 Token 使用量，记录输入、输出及总 Token 数。
 *
 * @param inputTokens 输入 Token 数
 * @param outputTokens 输出 Token 数
 * @param totalTokens 总 Token 数
 */
public record ModelUsage(long inputTokens, long outputTokens, long totalTokens) {
}
