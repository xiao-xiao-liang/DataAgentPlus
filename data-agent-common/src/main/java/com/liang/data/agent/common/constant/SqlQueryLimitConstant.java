package com.liang.data.agent.common.constant;

import lombok.RequiredArgsConstructor;

/**
 * SQL 查询行数限制常量。
 */
@RequiredArgsConstructor
public final class SqlQueryLimitConstant {

    public static final int DEFAULT_MAX_RESULT_ROWS = 100;
    public static final int MAX_RESULT_ROWS = 1000;
}
