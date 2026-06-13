package com.liang.data.agent.workflow.service;

import static com.liang.data.agent.workflow.constants.WorkflowRunConstants.FALLBACK_PERSIST_INTERVAL_MILLIS;

import lombok.extern.slf4j.Slf4j;

/**
 * 流式处理上下文
 *
 * <p>线程安全的上下文对象，用于累积单次流式处理过程中的输出内容。
 * 每个 threadId 对应一个独立的 StreamContext 实例，存储在 {@link GraphService} 的 ConcurrentHashMap 中。</p>
 *
 * <p>提供同步清理机制，确保在流完成、异常或客户端断连时安全释放资源。</p>
 */
@Slf4j
public class StreamContext {

    /**
     * 累积的输出内容
     */
    private final StringBuilder collectedOutput = new StringBuilder();

    /**
     * 最近一次时间兜底保存时间
     */
    private long lastFallbackPersistTimeMillis = System.currentTimeMillis();

    /**
     * 最近一次时间兜底保存的内容长度
     */
    private int lastFallbackPersistedLength = 0;

    /**
     * 是否已清理标识（volatile 保证多线程可见性）
     */
    private volatile boolean cleaned = false;

    /**
     * 清理上下文资源
     *
     * <p>同步方法，防止并发清理导致的数据不一致。
     * 仅首次调用生效，后续重复调用会被忽略。</p>
     */
    public synchronized void cleanup() {
        if (cleaned) {
            log.debug("StreamContext 已清理，跳过重复清理操作");
            return;
        }
        cleaned = true;
        log.debug("StreamContext 清理完成，累积输出长度: {}", collectedOutput.length());
    }

    /**
     * 追加流式输出内容
     *
     * @param output 输出片段
     */
    public synchronized void appendOutput(String output) {
        if (output != null && !cleaned) {
            collectedOutput.append(output);
        }
    }

    /**
     * 判断是否应该执行流式输出时间兜底保存。
     *
     * @param nowMillis 当前时间戳
     * @return true 表示应执行兜底保存
     */
    public synchronized boolean shouldPersistByTimeFallback(long nowMillis) {
        if (cleaned || collectedOutput.isEmpty()) {
            return false;
        }
        if (collectedOutput.length() == lastFallbackPersistedLength) {
            return false;
        }
        if (nowMillis - lastFallbackPersistTimeMillis < FALLBACK_PERSIST_INTERVAL_MILLIS) {
            return false;
        }
        lastFallbackPersistTimeMillis = nowMillis;
        lastFallbackPersistedLength = collectedOutput.length();
        return true;
    }

    /**
     * 获取已累积的完整输出内容
     *
     * @return 累积输出字符串
     */
    public synchronized String getCollectedOutput() {
        return collectedOutput.toString();
    }

    /**
     * 是否已完成清理
     *
     * @return true 表示已清理
     */
    public boolean isCleaned() {
        return cleaned;
    }
}
