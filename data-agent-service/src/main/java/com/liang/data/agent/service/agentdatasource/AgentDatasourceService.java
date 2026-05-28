package com.liang.data.agent.service.agentdatasource;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.data.agent.dal.entity.AgentDatasourceEntity;
import com.liang.data.agent.service.agentdatasource.vo.AgentDatasourceVO;

/**
 * 智能体-数据源关联服务接口
 * <p>
 * 管理智能体与数据源的绑定关系，以及 Schema 的同步与向量化。
 * 一个 Agent 同一时刻最多绑定一个数据源。
 * </p>
 */
public interface AgentDatasourceService extends IService<AgentDatasourceEntity> {

    /**
     * 绑定数据源到智能体
     * <p>
     * 一个 Agent 只允许绑定一个数据源。若已绑定其他数据源，会先解绑旧的再绑定新的。
     * 绑定成功后会异步触发 Schema 同步和向量化。
     * </p>
     *
     * @param agentId      智能体ID
     * @param datasourceId 数据源ID
     * @param tables       选中的表名列表，若为空则默认同步所有表
     */
    void bindDatasource(Integer agentId, Integer datasourceId, java.util.List<String> tables);

    /**
     * 解绑智能体当前绑定的数据源
     * <p>
     * 解绑时会同时清除该智能体的向量化文档（TABLE + COLUMN）。
     * 若当前无绑定则静默返回。
     * </p>
     *
     * @param agentId 智能体ID
     */
    void unbindDatasource(Integer agentId);

    /**
     * 获取智能体当前绑定的数据源关联信息
     *
     * @param agentId 智能体ID
     * @return 关联视图对象，未绑定时返回 null
     */
    AgentDatasourceVO getCurrentBinding(Integer agentId);

    /**
     * 触发 Schema 同步 + 向量化
     * <p>
     * 重新从数据源拉取所有表结构和字段信息，清除旧的向量文档后重新向量化。
     * 该操作会同步执行（调用方可通过 @Async 异步调用）。
     * </p>
     *
     * @param agentId 智能体ID
     * @param tables  指定的表名列表，若为空则从数据库表记录读取或降级全表同步
     */
    void syncSchema(Integer agentId, java.util.List<String> tables);
}
