package com.leis.workflow;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApprovalEngineConfiguration {
    @Bean
    @DependsOnDatabaseInitialization
    ProcessEngineConfigurationConfigurer approvalEngineConfigurer() {
        return configuration -> configuration.setHistoryLevel(HistoryLevel.AUDIT);
    }
}
