package com.liang.data.agent.controller;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;
import com.liang.data.agent.service.datasource.DatasourceService;
import com.liang.data.agent.service.datasource.dto.DatasourceDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据源接口冒烟测试。
 *
 * <p>覆盖 PostgreSQL 数据源连接预检与元数据查询入口，确保控制层正确接收并转发数据库类型。</p>
 */
class DatasourceControllerTest {

    private final DatasourceService service = mock(DatasourceService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DatasourceController(service)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testBeforeCreateAcceptsPostgresqlConfig() throws Exception {
        DatasourceDTO dto = new DatasourceDTO()
                .setName("pg_orders")
                .setType("postgresql")
                .setHost("pg.example.com")
                .setPort(5432)
                .setDatabaseName("public")
                .setUsername("agent")
                .setPassword("secret");
        when(service.testConnectionByDto(org.mockito.ArgumentMatchers.any(DatasourceDTO.class))).thenReturn(null);

        mockMvc.perform(post("/api/datasource/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<DatasourceDTO> captor = ArgumentCaptor.forClass(DatasourceDTO.class);
        verify(service).testConnectionByDto(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("postgresql");
        assertThat(captor.getValue().getPort()).isEqualTo(5432);
        assertThat(captor.getValue().getDatabaseName()).isEqualTo("public");
    }

    @Test
    void tablesEndpointReturnsPostgresqlMetadata() throws Exception {
        when(service.getTables(11)).thenReturn(List.of(new TableInfoBO("orders", "订单表")));

        mockMvc.perform(get("/api/datasource/11/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].tableName").value("orders"))
                .andExpect(jsonPath("$.data[0].comment").value("订单表"));
    }
}
