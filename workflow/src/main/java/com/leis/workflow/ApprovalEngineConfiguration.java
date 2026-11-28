package com.leis.workflow;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置流程引擎初始化顺序及审批历史记录级别。
 */
@Configuration(proxyBeanMethods = false)
class ApprovalEngineConfiguration {
    /**
     * 等待数据库初始化完成后配置审计历史级别，供已办记录查询使用。
     */
    @Bean
    @DependsOnDatabaseInitialization
    ProcessEngineConfigurationConfigurer approvalEngineConfigurer() {
        return configuration -> configuration.setHistoryLevel(HistoryLevel.AUDIT);
    }
}
