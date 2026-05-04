package com.liang.data.agent.ai.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Prompt 贡献者管理器
 *
 * <p>收集所有注册的 PromptContributor, 按优先级顺序组装最终的 PromptContribution。
 * 单个 Contributor 异常不影响整体流程。</p>
 */
@Slf4j
@Component
public class DefaultPromptContributorManager {

    private final List<PromptContributor> contributors;

    public DefaultPromptContributorManager(List<PromptContributor> contributors) {
        this.contributors = new CopyOnWriteArrayList<>();
        if (contributors != null) {
            this.contributors.addAll(contributors);
            this.contributors.sort(Comparator.comparingInt(PromptContributor::getPriority));
        }
        log.info("PromptContributorManager 初始化, 已注册 {} 个贡献者", this.contributors.size());
    }

    /**
     * 组装所有贡献者的内容
     */
    public PromptContribution contribute(PromptContributorContext context) {
        Objects.requireNonNull(context, "context 不能为 null");

        if (contributors.isEmpty()) {
            return PromptContribution.empty();
        }

        String systemPrepend = null, systemAppend = null;
        List<Message> prepend = new ArrayList<>(), append = new ArrayList<>();

        for (PromptContributor contributor : contributors) {
            try {
                if (!contributor.shouldContribute(context)) {
                    log.debug("Contributor [{}] 跳过", contributor.getName());
                    continue;
                }

                PromptContribution c = contributor.contribute(context);
                if (c == null || c.isEmpty()) {
                    log.debug("Contributor [{}] 返回空内容", contributor.getName());
                    continue;
                }

                log.debug("合并 Contributor [{}] 的贡献", contributor.getName());

                // 合并系统文本
                systemPrepend = concat(systemPrepend, c.systemTextToPrepend());
                systemAppend = concat(c.systemTextToAppend(), systemAppend);

                // 合并消息 (过滤掉 SystemMessage, 应该通过 systemText 方式贡献)
                addFiltered(prepend, c.messagesToPrepend());
                addFiltered(append, c.messagesToAppend());
            } catch (Exception e) {
                // 单个 Contributor 失败不影响整体
                log.error("Contributor [{}] 执行失败", contributor.getName(), e);
            }
        }

        return PromptContribution.builder()
                .systemTextToPrepend(systemPrepend)
                .systemTextToAppend(systemAppend)
                .prependAll(prepend)
                .appendAll(append)
                .build();
    }

    /**
     * 获取所有已注册的贡献者 (不可变副本)
     */
    public List<PromptContributor> getContributors() {
        return List.copyOf(contributors);
    }

    /**
     * 动态注册贡献者
     */
    public void register(PromptContributor contributor) {
        Objects.requireNonNull(contributor);
        contributors.add(contributor);
        contributors.sort(Comparator.comparingInt(PromptContributor::getPriority));
        log.info("注册 Contributor: {}", contributor.getName());
    }

    /**
     * 动态移除贡献者
     */
    public void unregister(String name) {
        boolean removed = contributors.removeIf(c -> name.equals(c.getName()));
        if (removed) {
            log.info("移除 Contributor: {}", name);
        }
    }

    private String concat(String existing, String next) {
        if (!StringUtils.hasText(next)) return existing;
        if (!StringUtils.hasText(existing)) return next;
        return existing + "\n\n" + next;
    }

    private void addFiltered(List<Message> out, List<Message> in) {
        if (in == null) return;
        for (Message message : in) {
            // 过滤 SystemMessage, 避免多个 SystemMessage 冲突
            if (!(message instanceof SystemMessage)) {
                out.add(message);
            }
        }
    }
}
