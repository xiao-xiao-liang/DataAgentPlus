package com.liang.data.agent.service.agent;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.data.agent.dal.entity.AgentEntity;
import com.liang.data.agent.service.agent.dto.AgentDTO;
import com.liang.data.agent.service.agent.vo.AgentVO;

import java.util.List;

/**
 * 智能体管理服务
 */
public interface AgentService extends IService<AgentEntity> {

    /**
     * 创建智能体
     *
     * @param dto 创建参数
     * @return 智能体ID
     */
    Integer create(AgentDTO dto);

    /**
     * 更新智能体
     *
     * @param dto 更新参数，必须包含 id
     */
    void update(AgentDTO dto);

    /**
     * 删除智能体（逻辑删除）
     *
     * @param id 智能体ID
     */
    void delete(Integer id);

    /**
     * 获取智能体详情
     *
     * @param id 智能体ID
     * @return 智能体视图对象
     */
    AgentVO getById(Integer id);

    /**
     * 获取智能体列表（支持关键词和状态过滤）
     *
     * @param keyword 关键词，匹配名称或描述，可为 null
     * @param status  状态过滤，可为 null
     * @return 智能体列表
     */
    List<AgentVO> list(String keyword, String status);

    /**
     * 发布智能体
     *
     * @param id 智能体ID
     */
    void publish(Integer id);

    /**
     * 下线智能体
     *
     * @param id 智能体ID
     */
    void offline(Integer id);

    /**
     * 获取智能体的 API Key
     *
     * @param id 智能体ID
     * @return API Key，可能为 null
     */
    String getApiKey(Integer id);

    /**
     * 生成 API Key（智能体尚未拥有 API Key 时调用）
     *
     * @param id 智能体ID
     * @return 生成的 API Key
     */
    String generateApiKey(Integer id);

    /**
     * 重置 API Key
     *
     * @param id 智能体ID
     * @return 新的 API Key
     */
    String resetApiKey(Integer id);

    /**
     * 启用 API Key 调用。
     *
     * @param id 智能体ID
     */
    void enableApiKey(Integer id);

    /**
     * 禁用 API Key 调用。
     *
     * @param id 智能体ID
     */
    void disableApiKey(Integer id);

    /**
     * 删除 API Key。
     *
     * @param id 智能体ID
     */
    void deleteApiKey(Integer id);
}
