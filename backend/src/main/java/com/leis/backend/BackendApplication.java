package com.leis.backend;

import com.leis.permission.PermissionApplication;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackages = {"com.leis.backend", "com.leis.permission", "com.leis.common"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = PermissionApplication.class))
@MapperScan(basePackages = "com.leis.permission", annotationClass = Mapper.class)
public class BackendApplication {
    /**
     * 启动权限后台，并加载业务模块、通用配置和 MyBatis Mapper。
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}