package com.leis.workflow.template.mapper;

import com.leis.workflow.template.ReviewerDirectory.Reviewer;
import com.leis.workflow.template.ReviewerDirectory.RoleOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提供流程设计所需的审批人查询。
 */
@Mapper
public interface ReviewerMapper {
    /** 按已配置的账号标识回显审批人，包括停用账号。 */
    List<Reviewer> findByIds(@Param("ids") List<Long> ids);

    /** 按显示名或账号搜索启用用户，最多返回 30 条。 */
    List<Reviewer> searchEnabled(String keyword);

    /** 列出启用角色，供模板设计选择。 */
    List<RoleOption> roles();

    /** 读取未包含申请人的启用角色成员，按用户标识排序。 */
    List<Long> activeRoleMembers(long roleId, long applicantId);

    /** 判断角色是否存在且启用。 */
    boolean roleEnabled(long roleId);
}
