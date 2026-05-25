package com.liang.data.agent.workflow.util;

import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 节点 Bean 工具类
 *
 * <p>从 Spring 容器获取节点/路由 Bean，并包装为异步版本供 StateGraph 使用</p>
 *
 * <p>StateGraph 的 addNode() 需要 AsyncNodeAction，
 * 而我们的节点是 @Component 注册的 Spring Bean (NodeAction)，需要一层转换。</p>
 */
@Component
@RequiredArgsConstructor
public class NodeBeanUtil {
    
    private final ApplicationContext context;

    /**
     * 获取节点 Bean
     */
    public <T extends NodeAction> NodeAction getNodeBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    /**
     * 获取节点 Bean 并包装为异步版本
     */
    public <T extends NodeAction> AsyncNodeAction toAsyncNode(Class<T> clazz) {
        return AsyncNodeAction.node_async(getNodeBean(clazz));
    }

    /**
     * 获取路由 Bean
     */
    public <T extends EdgeAction> EdgeAction getEdgeBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    /**
     * 获取路由 Bean 并包装为异步版本
     */
    public <T extends EdgeAction> AsyncEdgeAction toAsyncEdge(Class<T> clazz) {
        return AsyncEdgeAction.edge_async(getEdgeBean(clazz));
    }
}
