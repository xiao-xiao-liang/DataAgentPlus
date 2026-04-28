package com.liang.data.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * DataAgent 启动类
 *
 * <p>基于 Spring AI Alibaba Graph 的企业级智能数据分析 Agent</p>
 *
 * @author liang
 * @since 1.0.0
 */
@EnableAsync
@SpringBootApplication
public class DataAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataAgentApplication.class, args);
    }

}
