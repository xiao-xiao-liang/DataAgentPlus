package com.liang.data.agent.ai.prompt;

/**
 * Prompt 贡献者 SPI 接口
 *
 * <p>每个贡献者负责向 Prompt 中注入一部分内容 (如业务知识、Schema 信息、历史对话等)。
 * 由 PromptContributorManager 按优先级顺序调用。</p>
 *
 * <p>使用方式: 实现此接口并注册为 Spring Bean, 管理器会自动发现。</p>
 */
public interface PromptContributor {

    /**
     * 贡献者名称 (唯一标识, 用于日志和调试)
     */
    String getName();

    /**
     * 条件判断: 是否应该参与本次 Prompt 组装
     *
     * @param context 上下文
     * @return true 表示参与
     */
    boolean shouldContribute(PromptContributorContext context);

    /**
     * 生成贡献内容
     *
     * @param context 上下文
     * @return 贡献内容, 返回 null 或 empty 表示不贡献
     */
    PromptContribution contribute(PromptContributorContext context);

    /**
     * 优先级 (数值越小越先执行)
     * <p>默认 100, 系统级贡献者可用 10/20, 用户自定义用 200+</p>
     */
    default int getPriority() {
        return 100;
    }
}
