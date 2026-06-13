package com.liang.data.agent.workflow.constants;

import lombok.NoArgsConstructor;

import java.util.Set;

import static com.liang.data.agent.common.constant.StateKey.PYTHON_GENERATE_NODE;
import static com.liang.data.agent.common.constant.StateKey.REPORT_GENERATOR_NODE;
import static com.liang.data.agent.common.constant.StateKey.SQL_GENERATE_NODE;

/**
 * 工作流执行计划常量。
 */
@NoArgsConstructor
public final class PlanConstants {

    public static final int MAX_REPAIR_COUNT = 2;

    public static final int MAX_HUMAN_REPAIR_COUNT = 3;

    public static final String STEP_PREFIX = "step_";

    public static final Set<String> SUPPORTED_NODES = Set.of(
            SQL_GENERATE_NODE,
            PYTHON_GENERATE_NODE,
            REPORT_GENERATOR_NODE
    );
}
