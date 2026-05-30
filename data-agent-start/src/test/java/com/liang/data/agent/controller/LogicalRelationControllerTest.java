package com.liang.data.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.service.logicalrelation.LogicalRelationService;
import com.liang.data.agent.service.logicalrelation.dto.LogicalRelationDTO;
import com.liang.data.agent.service.logicalrelation.vo.LogicalRelationVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogicalRelationControllerTest {

    private final LogicalRelationService service = mock(LogicalRelationService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LogicalRelationController(service)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listReturnsRelationsForDatasource() throws Exception {
        LogicalRelationVO relation = LogicalRelationVO.builder()
                .id(12)
                .datasourceId(7)
                .sourceTableName("orders")
                .sourceColumnName("user_id")
                .targetTableName("users")
                .targetColumnName("id")
                .relationType("N:1")
                .description("orders.user_id points to users.id")
                .build();
        when(service.listByDatasource(7)).thenReturn(List.of(relation));

        mockMvc.perform(get("/api/datasource/7/logical-relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].sourceTableName").value("orders"))
                .andExpect(jsonPath("$.data[0].targetTableName").value("users"));
    }

    @Test
    void createUsesPathDatasourceId() throws Exception {
        LogicalRelationDTO dto = LogicalRelationDTO.builder()
                .sourceTableName("orders")
                .sourceColumnName("user_id")
                .targetTableName("users")
                .targetColumnName("id")
                .relationType("N:1")
                .description("orders.user_id points to users.id")
                .build();
        when(service.create(any(LogicalRelationDTO.class))).thenReturn(99);

        mockMvc.perform(post("/api/datasource/7/logical-relations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(99));

        verify(service).create(any(LogicalRelationDTO.class));
    }

    @Test
    void deleteRemovesRelationById() throws Exception {
        mockMvc.perform(delete("/api/datasource/7/logical-relations/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(service).delete(7, 12);
    }
}
