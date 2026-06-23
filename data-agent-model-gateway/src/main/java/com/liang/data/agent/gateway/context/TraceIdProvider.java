package com.liang.data.agent.gateway.context;

/**
 * 链路追踪编号提供器，用于从当前执行环境读取可用的 traceId。
 */
@FunctionalInterface
public interface TraceIdProvider {

    /**
     * 读取当前链路追踪编号。
     *
     * @return 当前链路追踪编号；返回 null 表示当前没有可用链路追踪编号
     */
    String currentTraceId();
}
