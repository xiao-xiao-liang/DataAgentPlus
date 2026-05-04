package com.liang.data.agent.ai.model;

import com.liang.data.agent.common.enums.ModelType;
import com.liang.data.agent.dal.entity.ModelConfigEntity;
import com.liang.data.agent.dal.mapper.ModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 模型配置查询服务 — 仅负责从 DB 查询 active 配置
 *
 * <p>写操作(增/改/删/激活)由 Phase 5 的 Service 层负责</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigQueryService {

    private final ModelConfigMapper modelConfigMapper;

    /**
     * 查询指定类型的激活配置
     *
     * @param modelType 模型类型
     * @return 激活的配置, 无则返回 empty
     */
    public Optional<ModelConfigEntity> getActiveConfig(ModelType modelType) {
        ModelConfigEntity modelConfig = modelConfigMapper.selectActiveByType(modelType.getCode());
        if (modelConfig == null) {
            log.warn("未找到 [{}] 类型的激活模型配置", modelType);
        }
        return Optional.ofNullable(modelConfig);
    }
}
