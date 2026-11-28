package com.leis.workflow.template;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.common.common.BusinessException;
import com.leis.workflow.access.BusinessAccess;
import com.leis.workflow.template.mapper.ReviewerMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 为模板设计提供审批人搜索和按标识回显的查询能力。
 */
@Service
public class ReviewerDirectory {
    /**
     * 返回审批人选择器所需的显示名称、账号和启用状态。
     */
    @Schema(description = "返回审批人选择器所需的显示名称、账号和启用状态")
    public record Reviewer(
        @Schema(description = "记录标识")
        long id,
        @Schema(description = "显示名称")
        String name,
        @Schema(description = "登录账号")
        String username,
        @Schema(description = "审批人账号是否处于启用状态")
        boolean enabled
    ) {}

    /** 模板设计用的审批角色选项。 */
    @Schema(description = "审批角色选项")
    public record RoleOption(
            @Schema(description = "角色标识") String id,
            @Schema(description = "角色名称") String name,
            @Schema(description = "角色编码") String code
    ) {}

    private final BusinessAccess access;
    private final ReviewerMapper mapper;

    /**
     * 注入业务访问校验和审批人查询依赖。
     */
    public ReviewerDirectory(BusinessAccess access, ReviewerMapper mapper) {
        this.access = access;
        this.mapper = mapper;
    }

    /**
     * 校验业务可见性后按账号标识回显已有审批人，或按关键词搜索启用账号。
     */
    public List<Reviewer> find(String business, String keyword, List<Long> ids) {
        access.require(business, BusinessAccess.Capability.VIEW_DRAFT);
        if (keyword.length() > 64 || (ids != null && ids.size() > 50)) {
            throw new BusinessException(400, "审批人查询参数超出范围");
        }
        if (ids != null && !ids.isEmpty()) {
            return mapper.findByIds(ids);
        }
        return mapper.searchEnabled(keyword.strip());
    }

    /** 校验草稿查看权限后返回可选择的审批角色。 */
    public List<RoleOption> roles(String business) {
        access.require(business, BusinessAccess.Capability.VIEW_DRAFT);
        return mapper.roles();
    }
}
