package com.leis.workflow.execution.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 登记业务单据与流程实例使用的模板版本。
 */
@Mapper
public interface TemplateInstanceMapper {
    /** 写入业务单据、模板版本及 Flowable 实例的关联。 */
    void insert(@Param("business") String business,
                @Param("code") String code,
                @Param("businessKey") String businessKey,
                @Param("version") int version,
                @Param("instanceId") String instanceId);
}
