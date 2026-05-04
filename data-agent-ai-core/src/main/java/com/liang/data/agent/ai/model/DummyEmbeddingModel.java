package com.liang.data.agent.ai.model;

import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;

import java.util.List;

/**
 * 哑巴模型 — 仅用于启动时防止 VectorStore 初始化崩溃
 *
 * <p>当没有配置 EMBEDDING 类型的模型时, AiModelRegistry 会返回此实例。
 * 真正调用 call() 时会抛出 ServiceException 提示用户先配置模型。</p>
 */
public class DummyEmbeddingModel implements EmbeddingModel {

    @Override
    public @NonNull EmbeddingResponse call(@NonNull EmbeddingRequest request) {
        throw new ServiceException("未配置 EMBEDDING 模型, 请在管理页面先配置嵌入模型", BaseErrorCode.SERVICE_ERROR);
    }

    @Override
    public float @NonNull [] embed(@NonNull Document document) {
        return new float[0];
    }

    @Override
    public float @NonNull [] embed(@NonNull String text) {
        return new float[0];
    }

    @Override
    public @NonNull List<float[]> embed(@NonNull List<String> texts) {
        return List.of();
    }

    @Override
    public EmbeddingResponse embedForResponse(@NonNull List<String> texts) {
        return null;
    }

    @Override
    public int dimensions() {
        return 1536; // 返回 1536 (OpenAI 标准维度), 骗过 VectorStore 的维度检查
    }
}
