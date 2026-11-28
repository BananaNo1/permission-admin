package com.leis.workflow.execution;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.common.common.BusinessException;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.auth.CurrentActor;
import com.leis.permission.dept.service.DeptQueryService;
import com.leis.workflow.template.TemplateCompiler;
import com.leis.workflow.template.TemplateCompiler.Step;
import com.leis.workflow.template.mapper.ReviewerMapper;
import com.leis.workflow.template.TemplateService;
import jakarta.validation.constraints.*;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.RuntimeService;
import com.leis.workflow.execution.mapper.TemplateInstanceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/**
 * 按已发布版本启动流程实例，并记录业务单据与流程实例的关联。
 */
@Service
public class TemplateExecutionService {
    /**
     * 发起模板流程时提交的业务单据标识、申请内容和金额。
     */
    @Schema(description = "发起模板流程时提交的业务单据标识、申请内容和金额")
    public record Start(
        @Schema(description = "用于关联业务单据的业务标识")
        @NotBlank @Size(max = 120) String businessKey,
        @Schema(description = "审批申请标题")
        @NotBlank @Size(max = 120) String title,
        @Schema(description = "申请内容的补充说明")
        @Size(max = 1000) String description,
        @Schema(description = "用于判断金额门槛的申请金额")
        @NotNull @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal amount
    ) {}
    /**
     * 返回新流程实例及其关联的模板版本和业务单据标识。
     */
    @Schema(description = "返回新流程实例及其关联的模板版本和业务单据标识")
    public record Started(
        @Schema(description = "Flowable 流程实例标识")
        String instanceId,
        @Schema(description = "流程模板编码")
        String templateCode,
        @Schema(description = "本次发起使用的模板发布版本")
        int version,
        @Schema(description = "用于关联业务单据的业务标识")
        String businessKey
    ) {}

    private final TemplateService templates;
    private final TemplateInstanceMapper instances;
    private final CurrentActor actor;
    private final AccountQueryService accounts;
    private final RuntimeService engine;
    private final DeptQueryService departments;
    private final ReviewerMapper reviewerMapper;

    /**
     * 注入模板版本查询、身份解析、账号查询、流程引擎和实例登记依赖。
     */
    public TemplateExecutionService(TemplateService templates, TemplateInstanceMapper instances, CurrentActor actor,
                                    AccountQueryService accounts, RuntimeService engine, DeptQueryService departments,
                                    ReviewerMapper reviewerMapper) {
        this.templates = templates;
        this.instances = instances;
        this.actor = actor;
        this.accounts = accounts;
        this.engine = engine;
        this.departments = departments;
        this.reviewerMapper = reviewerMapper;
    }

    /**
     * 校验本次会执行的审批人，启动当前发布版本，并在同一事务内登记业务实例关联。
     */
    @Transactional
    public Started start(String business, String code, Start request) {
        var release = templates.release(business, code);
        var applicantActor = actor.get();
        long applicant = applicantActor.id();
        var variables = new HashMap<String, Object>();
        var reviewers = new LinkedHashSet<String>();
        var layout = release.diagramLayout();
        var graph = layout == null ? null : layout.routing();
        var path = graph == null ? null : graph.path(request.amount());
        if (path != null) {
            int previous = -1;
            for (int step : path) {
                variables.put("wfPrevious_" + step, previous);
                previous = step;
            }
        }
        for (int i = 0; i < release.steps().size(); i++) {
            if (path != null && !path.contains(i)) {
                continue;
            }
            var step = release.steps().get(i);
            if (step.minAmount() != null && request.amount().compareTo(step.minAmount()) < 0) {
                continue;
            }
            var ids = resolveReviewers(step, applicant, applicantActor.deptId()).stream().map(String::valueOf).toList();
            variables.put("stepUsers" + i, new ArrayList<>(ids));
            reviewers.addAll(ids);
        }
        variables.put("approverIds", new ArrayList<>(reviewers));
        variables.put("applicantId", Long.toString(applicant));
        variables.put("title", request.title().strip());
        variables.put("description", request.description() == null ? "" : request.description().strip());
        variables.put("decision", "");
        variables.put("amount", request.amount());
        variables.put("templateCode", code);
        variables.put("businessCode", business);
        variables.put("templateVersion", release.version());
        var context = Authentication.getAuthenticationContext();
        var previous = context.getPrincipal();
        String businessKey = request.businessKey().strip();
        try {
            Authentication.setAuthenticatedUserId(Long.toString(applicant));
            var instance = engine.startProcessInstanceById(release.definitionId(), businessKey, variables);
            instances.insert(business, code, businessKey, release.version(), instance.getId());
            return new Started(instance.getId(), code, release.version(), businessKey);
        } finally {
            context.setPrincipal(previous);
        }
    }

    /** 按模板配置解析实际办理人，以发起时的账号关系为准。 */
    private List<Long> resolveReviewers(Step step, long applicant, Long deptId) {
        List<Long> ids = switch (step.reviewerSource()) {
            case FIXED -> step.approverIds();
            case ROLE -> reviewerMapper.activeRoleMembers(step.roleId(), applicant);
            case DEPARTMENT_MANAGER -> List.of(requireManager(step, deptId, applicant));
        };
        if (!TemplateCompiler.validReviewerCount(step.mode(), ids.size())) {
            throw new BusinessException(400, "节点「" + step.name() + "」的可用审批人数与审批模式不匹配");
        }
        if (step.reviewerSource() == TemplateCompiler.ReviewerSource.FIXED) {
            for (Long id : ids) {
                var user = accounts.findById(id);
                if (id == applicant || user == null || !"ENABLED".equals(user.getStatus())) {
                    throw new BusinessException(400, "节点「" + step.name() + "」包含申请人或未启用账号");
                }
            }
        }
        return ids;
    }

    /** 负责人必须仍是本部门的启用用户，且不能是申请人本人。 */
    private Long requireManager(Step step, Long deptId, long applicant) {
        Long id = departments.managerId(deptId);
        var user = id == null ? null : accounts.findById(id);
        if (id == null || id == applicant || user == null || !"ENABLED".equals(user.getStatus())
                || !Objects.equals(deptId, user.getDeptId())) {
            throw new BusinessException(400, "节点「" + step.name() + "」没有可用的部门负责人");
        }
        return id;
    }
}
