package com.leis.workflow.access;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.common.common.BusinessException;
import com.leis.permission.auth.CurrentActor;
import com.leis.permission.log.dto.AuditCommand;
import com.leis.permission.log.service.AuditService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.leis.workflow.access.mapper.BusinessMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 管理流程业务范围与用户授权，并校验编辑、发布和发起能力。
 */
@Service
public class BusinessAccess {
    /**
     * 表示一个可访问的流程业务及当前操作者在该业务下的能力。
     */
    @Schema(description = "表示一个可访问的流程业务及当前操作者在该业务下的能力")
    public record Business(
        @Schema(description = "流程业务编码")
        String code,
        @Schema(description = "显示名称")
        String name,
        @Schema(description = "是否允许编辑流程模板草稿")
        boolean edit,
        @Schema(description = "是否允许发布流程模板")
        boolean publish,
        @Schema(description = "是否允许使用已发布模板发起申请")
        boolean start
    ) {}
    /**
     * 创建流程业务范围时提交的业务编码和名称。
     */
    @Schema(description = "创建流程业务范围时提交的业务编码和名称")
    public record Create(
        @Schema(description = "流程业务编码")
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String code,
        @Schema(description = "显示名称")
        @NotBlank @Size(max = 128) String name
    ) {}
    /**
     * 返回某个用户在指定流程业务下的授权结果。
     */
    @Schema(description = "返回某个用户在指定流程业务下的授权结果")
    public record Grant(
        @Schema(description = "授予流程业务权限的用户标识")
        long userId,
        @Schema(description = "是否允许编辑流程模板草稿")
        boolean edit,
        @Schema(description = "是否允许发布流程模板")
        boolean publish,
        @Schema(description = "是否允许使用已发布模板发起申请")
        boolean start
    ) {}
    /**
     * 保存用户在流程业务下的编辑、发布和发起权限设置。
     */
    @Schema(description = "保存用户在流程业务下的编辑、发布和发起权限设置")
    public record Rights(
        @Schema(description = "是否允许编辑流程模板草稿")
        boolean edit,
        @Schema(description = "是否允许发布流程模板")
        boolean publish,
        @Schema(description = "是否允许使用已发布模板发起申请")
        boolean start
    ) {}
    private final BusinessMapper mapper;
    private final CurrentActor actor;

    private final AuditService audit;

    /**
     * 注入业务授权持久化、当前操作者及审计依赖。
     */
    public BusinessAccess(BusinessMapper mapper, CurrentActor actor, AuditService audit) {
        this.mapper = mapper;
        this.actor = actor;
        this.audit = audit;
    }

    /**
     * 列出流程业务接口可校验的操作能力。
     */
    public enum Capability { VIEW_DRAFT, EDIT, PUBLISH, START }

    /**
     * 校验业务操作权限。保留业务行锁，使写操作与授权变更按顺序执行。
     */
    @Transactional
    public void require(String business, Capability capability) {
        var user = actor.get();
        if (mapper.lockCode(business) == null) {
            throw new BusinessException(403, "无此业务权限");
        }
        if (user.admin()) {
            return;
        }

        var rights = mapper.findRights(business, user.id());
        boolean allowed = rights != null && switch (capability) {
            case VIEW_DRAFT -> rights.edit() || rights.publish();
            case EDIT -> rights.edit();
            case PUBLISH -> rights.publish();
            case START -> rights.start();
        };
        if (!allowed) {
            throw new BusinessException(403, "无此业务权限");
        }
    }

    /**
     * 列出当前用户可见的流程业务及各项能力，超级管理员可见全部业务。
     */
    public List<Business> list() {
        var user = actor.get();
        return mapper.findVisible(user.id(), user.admin());
    }

    /**
     * 要求当前操作者具备超级管理员权限。
     */
    private void admin() {
        if (!actor.get().admin()) {
            throw new BusinessException(403, "需要超级管理员权限");
        }
    }

    /**
     * 校验指定业务编码存在，不存在时返回 404。
     */
    private void checkBusiness(String code) {
        if (!mapper.exists(code)) {
            throw new BusinessException(404, "业务不存在");
        }
    }

    /**
     * 创建流程业务范围，处理编码冲突并记录创建审计。
     */
    @Transactional
    public void create(Create request) {
        admin();
        try {
            mapper.insertBusiness(request.code(), request.name().strip());
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new BusinessException(409, "业务编码已存在");
        }
        record("WORKFLOW_BUSINESS_CREATE", request.code(), "创建业务");
    }

    /**
     * 记录流程业务维护操作，并关联当前请求的追踪标识。
     */
    private void record(String action, String code, String summary) {
        var user = actor.get();
        var request = RequestContextHolder.getRequestAttributes();
        var traceId = request == null ? null : (String) request.getAttribute("traceId", RequestAttributes.SCOPE_REQUEST);
        audit.success(new AuditCommand(user.id(), user.username(), action, "WORKFLOW_BUSINESS",
                code, true, summary, traceId));
    }

    /**
     * 由超级管理员查询指定业务下已有的用户授权。
     */
    public List<Grant> grants(String code) {
        admin();
        checkBusiness(code);
        return mapper.findGrants(code);
    }

    /**
     * 锁定业务记录，串行化同一业务下的授权变更。
     */
    private void lockBusiness(String code) {
        if (mapper.lockCode(code) == null) {
            throw new BusinessException(404, "业务不存在");
        }
    }

    /**
     * 更新或新增用户业务权限，三项权限均关闭时撤销授权，并记录审计。
     */
    @Transactional
    public void grant(String code, long userId, Rights rights) {
        admin();
        lockBusiness(code);
        if (!rights.edit() && !rights.publish() && !rights.start()) {
            mapper.deleteGrant(code, userId);
            record("WORKFLOW_BUSINESS_REVOKE", code, rightsSummary(userId, rights));
            return;
        }
        if (mapper.updateGrant(code, userId, rights) == 0 && mapper.insertGrant(code, userId, rights) == 0) {
            throw new BusinessException(404, "用户不存在");
        }
        record("WORKFLOW_BUSINESS_GRANT", code, rightsSummary(userId, rights));
    }

    /**
     * 生成包含授权对象和三项权限状态的审计摘要。
     */
    private String rightsSummary(long userId, Rights rights) {
        return "userId=%d edit=%b publish=%b start=%b".formatted(userId, rights.edit(), rights.publish(), rights.start());
    }
}
