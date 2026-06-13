package com.liang.data.agent.workflow.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 分析队列 Lua 脚本资源单元测试。
 */
class RedisWorkflowQueueSchedulerLuaScriptTest {

    @Test
    void luaScriptsShouldBeLoadedFromClasspathResources() throws Exception {
        assertLuaScriptExists("lua/workflow_queue_claim_runnable.lua", "selectedUsers");
        assertLuaScriptExists("lua/workflow_queue_release_running.lua", "HDEL");
        assertLuaScriptExists("lua/workflow_queue_rollback_claim.lua", "ZADD");
    }

    private void assertLuaScriptExists(String path, String expectedContent) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        assertThat(resource.exists()).isTrue();
        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        assertThat(content).contains(expectedContent);
    }
}
