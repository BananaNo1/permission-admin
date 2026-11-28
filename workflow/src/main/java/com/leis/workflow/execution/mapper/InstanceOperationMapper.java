package com.leis.workflow.execution.mapper;

import com.leis.workflow.ApprovalService.InstanceOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 审批操作的串行控制与审计记录。 */
@Mapper
public interface InstanceOperationMapper {
    /** 为首次办理的实例补建行锁记录，兼容已运行的老实例。 */
    void ensureLock(@Param("instanceId") String instanceId);

    /** 在审批写事务内锁定实例。 */
    String lock(@Param("instanceId") String instanceId);

    /** 记录操作类型、原因和可选的转办接收人。 */
    void record(@Param("instanceId") String instanceId,
                @Param("taskId") String taskId,
                @Param("actorId") long actorId,
                @Param("targetId") Long targetId,
                @Param("operation") String operation,
                @Param("reason") String reason);

    /** 查询实例最近的流程操作。 */
    List<InstanceOperation> recent(@Param("instanceId") String instanceId);

    /** 即使转办后失去任务归属，也允许已执行过转办的原办理人查看详情。 */
    boolean wasTransferrer(@Param("instanceId") String instanceId, @Param("actorId") long actorId);
}
