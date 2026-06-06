package com.liang.data.agent.service.knowledge.chunk.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static com.liang.data.agent.service.knowledge.chunk.KnowledgeChunkConstraint.MAX_CONTENT_LENGTH;
import static com.liang.data.agent.service.knowledge.chunk.KnowledgeChunkConstraint.MAX_NAME_LENGTH;

/**
 * 知识分块保存请求。
 */
@Data
public class KnowledgeChunkUpdateRequest {
    @NotBlank(message = "分块名称不能为空")
    @Size(max = MAX_NAME_LENGTH, message = "分块名称不能超过 255 个字符")
    private String name;

    @NotBlank(message = "分块正文不能为空")
    @Size(max = MAX_CONTENT_LENGTH, message = "分块正文不能超过 200000 个字符")
    private String content;

    @NotNull(message = "分块内容版本不能为空")
    @Positive(message = "分块内容版本必须大于 0")
    private Integer contentVersion;

    private Boolean manualNameChanged;
}
