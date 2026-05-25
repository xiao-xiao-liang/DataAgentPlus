package com.liang.data.agent.workflow.service;

import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.workflow.dto.node.SemanticConsistencyDTO;
import com.liang.data.agent.workflow.dto.node.SqlGenerationDTO;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

public interface Nl2SqlService {

    /**
     * 生成 SQL
     */
    Flux<ChatResponse> generateSql(SqlGenerationDTO dto);

    /**
     * 修复 SQL
     */
    Flux<ChatResponse> fixSql(SqlGenerationDTO dto);

    /**
     * LLM 精选 Schema
     */
    Flux<ChatResponse> fineSelect(SchemaDTO schema, String input, String evidence,
                                  String advice, DbConfigBO dbConfig, Consumer<SchemaDTO> consumer);

    /**
     * 语义一致性检查
     */
    Flux<ChatResponse> checkSemanticConsistency(SemanticConsistencyDTO dto);
}