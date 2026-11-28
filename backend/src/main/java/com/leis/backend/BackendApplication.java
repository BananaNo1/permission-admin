package com.leis.backend;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 权限后台的组合启动入口，装配权限、流程和公共组件。
 */
@SpringBootApplication
@ComponentScan(basePackages = { "com.leis.backend", "com.leis.permission", "com.leis.common", "com.leis.workflow" })
@MapperScan(basePackages = { "com.leis.permission", "com.leis.workflow" }, annotationClass = Mapper.class)
public class BackendApplication {
    /**
     * 启动权限后台，并加载业务模块、通用配置和 MyBatis Mapper。
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
