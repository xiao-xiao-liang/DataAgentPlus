package com.liang.data.agent.ai.prompt;

import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Prompt 贡献内容 (不可变对象)
 *
 * <p>包含系统文本和消息两种贡献方式:
 * <ul>
 *   <li>systemTextToPrepend/Append → 追加到系统消息前/后</li>
 *   <li>messagesToPrepend/Append → 追加到消息列表前/后</li>
 * </ul>
 * 优先使用 systemText, 避免产生多个 SystemMessage</p>
 */
public final class PromptContribution {

    private final String systemTextToPrepend;
    private final String systemTextToAppend;
    private final List<Message> messagesToPrepend;
    private final List<Message> messagesToAppend;

    private PromptContribution(Builder builder) {
        this.systemTextToPrepend = builder.systemTextToPrepend;
        this.systemTextToAppend = builder.systemTextToAppend;
        this.messagesToPrepend = builder.messagesToPrepend;
        this.messagesToAppend = builder.messagesToAppend;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PromptContribution empty() {
        return builder().build();
    }

    public String systemTextToPrepend() {
        return systemTextToPrepend;
    }

    public String systemTextToAppend() {
        return systemTextToAppend;
    }

    public List<Message> messagesToPrepend() {
        return Collections.unmodifiableList(messagesToPrepend);
    }

    public List<Message> messagesToAppend() {
        return Collections.unmodifiableList(messagesToAppend);
    }

    public boolean isEmpty() {
        return (systemTextToPrepend == null || systemTextToPrepend.isBlank())
                && (systemTextToAppend == null || systemTextToAppend.isBlank())
                && messagesToPrepend.isEmpty()
                && messagesToAppend.isEmpty();
    }

    public static final class Builder {
        private String systemTextToPrepend;
        private String systemTextToAppend;
        private final List<Message> messagesToPrepend = new ArrayList<>();
        private final List<Message> messagesToAppend = new ArrayList<>();

        public Builder systemTextToPrepend(String systemTextToPrepend) {
            this.systemTextToPrepend = systemTextToPrepend;
            return this;
        }

        public Builder systemTextToAppend(String systemTextToAppend) {
            this.systemTextToAppend = systemTextToAppend;
            return this;
        }

        public Builder prepend(Message message) {
            this.messagesToPrepend.add(Objects.requireNonNull(message));
            return this;
        }

        public Builder prependAll(List<? extends Message> messages) {
            if (messages != null) {
                messages.forEach(this::prepend);
            }
            return this;
        }

        public Builder append(Message message) {
            this.messagesToAppend.add(Objects.requireNonNull(message));
            return this;
        }

        public Builder appendAll(List<? extends Message> messages) {
            if (messages != null) {
                messages.forEach(this::append);
            }
            return this;
        }

        public PromptContribution build() {
            return new PromptContribution(this);
        }
    }
}
