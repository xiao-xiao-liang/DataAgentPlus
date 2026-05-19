package com.liang.data.agent.workflow.dto;

/**
 * SQL 节点重试状态
 *
 * @param reason         失败原因描述
 * @param semanticFail   是否语义一致性校验失败
 * @param sqlExecuteFail 是否 SQL 执行失败
 */
public record SqlRetryDTO(String reason, boolean semanticFail, boolean sqlExecuteFail) {

    /**
     * 语义校验失败
     */
    public static SqlRetryDTO semantic(String reason) {
        return new SqlRetryDTO(reason, true, false);
    }

    /**
     * SQL 执行失败
     */
    public static SqlRetryDTO sqlExecute(String reason) {
        return new SqlRetryDTO(reason, false, true);
    }

    /**
     * 空状态 (初始/重置用)
     */
    public static SqlRetryDTO empty() {
        return new SqlRetryDTO("", false, false);
    }
}
