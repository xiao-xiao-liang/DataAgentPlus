package com.liang.data.agent.service.knowledge;

import com.liang.data.agent.dal.mapper.AgentKnowledgeJobMapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeJobEntity;
import com.liang.data.agent.service.knowledge.job.AgentKnowledgeJobExecutor;
import com.liang.data.agent.service.knowledge.job.AgentKnowledgeJobScheduler;
import com.liang.data.agent.service.knowledge.job.mq.KnowledgeJobConsumer;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 智能体知识任务执行入口注入测试。
 */
class AgentKnowledgeJobTaskExecutorInjectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldCreateMqConsumerAndUseApplicationTaskExecutorForScheduler() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(KnowledgeJobConsumer.class);
            assertThat(context).hasSingleBean(AgentKnowledgeJobScheduler.class);
        });
    }

    @Test
    void schedulerShouldScanExpiredRunningJobs() {
        AgentKnowledgeJobMapper mapper = mock(AgentKnowledgeJobMapper.class);
        AgentKnowledgeJobExecutor executor = mock(AgentKnowledgeJobExecutor.class);
        AgentKnowledgeJobScheduler scheduler = new AgentKnowledgeJobScheduler(mapper, executor, new SyncTaskExecutor());

        scheduler.scanAndExecute();

        ArgumentCaptor<Wrapper<AgentKnowledgeJobEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("locked_until");
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
        assertThat(wrapper.getParamNameValuePairs())
                .containsValue("RUNNING");
    }

    /**
     * 构造与 Spring Boot 自动配置一致的执行器候选，复现启动时的注入歧义。
     */
    @Configuration
    @Import({KnowledgeJobConsumer.class, AgentKnowledgeJobScheduler.class})
    static class TestConfiguration {

        @Bean
        AgentKnowledgeJobExecutor agentKnowledgeJobExecutor() {
            return mock(AgentKnowledgeJobExecutor.class);
        }

        @Bean
        AgentKnowledgeJobMapper agentKnowledgeJobMapper() {
            return mock(AgentKnowledgeJobMapper.class);
        }

        @Bean
        TaskExecutor applicationTaskExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        ThreadPoolTaskScheduler taskScheduler() {
            return new ThreadPoolTaskScheduler();
        }
    }
}
