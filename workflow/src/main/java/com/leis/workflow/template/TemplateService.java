package com.leis.workflow.template;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.common.common.BusinessException;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.auth.CurrentActor;
import com.leis.workflow.access.BusinessAccess;
import com.leis.workflow.template.entity.TemplateDraftRow;
import com.leis.workflow.template.entity.TemplateReleaseRow;
import com.leis.workflow.template.mapper.TemplateMapper;
import com.leis.workflow.template.mapper.ReviewerMapper;
import jakarta.validation.constraints.*;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 管理流程模板草稿及发布版本，协调权限检查、流程部署和版本登记。
 */
@Service
public class TemplateService {
    /**
     * 返回流程模板的草稿内容、当前修订及是否有待发布修改。
     */
    @Schema(description = "返回流程模板的草稿内容、当前修订及是否有待发布修改")
    public record Draft(
        @Schema(description = "同一业务范围内唯一的模板编码")
        String code,
        @Schema(description = "显示名称")
        String name,
        @Schema(description = "模板草稿修订号")
        int revision,
        @Schema(description = "当前发布版本号，0 表示尚未发布")
        int publishedVersion,
        @Schema(description = "是否有尚未发布的草稿修改")
        boolean dirty,
        @Schema(description = "当前模板是否允许发起新申请")
        boolean enabled,
        @Schema(description = "按执行顺序排列的审批步骤")
        List<TemplateCompiler.Step> steps,
        @Schema(description = "用于恢复流程设计器显示的画布布局，可省略")
        DiagramLayout diagramLayout
    ) {}
    /**
     * 保存模板草稿的请求参数，修订号为零表示创建。
     */
    @Schema(description = "保存模板草稿的请求参数，修订号为零表示创建")
    public record Save(
        @Schema(description = "显示名称")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "期望的草稿修订号，0 表示创建新模板")
        @PositiveOrZero int revision,
        @Schema(description = "按执行顺序排列的审批步骤")
        @NotEmpty @Size(max = 30) List<TemplateCompiler.Step> steps,
        @Schema(description = "用于恢复流程设计器显示的画布布局，可省略")
        DiagramLayout diagramLayout
    ) {
        /**
         * 构建未指定画布布局的草稿保存请求。
         */
        public Save(String name, int revision, List<TemplateCompiler.Step> steps) {
            this(name, revision, steps, null);
        }
    }
    /**
     * 提交待发布草稿的修订号，避免部署过期内容。
     */
    @Schema(description = "提交待发布草稿的修订号，避免部署过期内容")
    public record Publish(
        @Schema(description = "本次要发布的草稿修订号，必须与当前修订匹配")
        @Positive int revision
    ) {}
    /**
     * 返回可执行的模板版本快照及其 Flowable 定义标识。
     */
    @Schema(description = "返回可执行的模板版本快照及其 Flowable 定义标识")
    public record Release(
        @Schema(description = "同一业务范围内唯一的模板编码")
        String code,
        @Schema(description = "显示名称")
        String name,
        @Schema(description = "模板发布版本号")
        int version,
        @Schema(description = "该发布版本对应的 Flowable 流程定义标识")
        String definitionId,
        @Schema(description = "按执行顺序排列的审批步骤")
        List<TemplateCompiler.Step> steps,
        @Schema(description = "用于恢复流程设计器显示的画布布局，可省略")
        DiagramLayout diagramLayout
    ) {}
    private final TemplateMapper mapper;
    private final ObjectMapper json;
    private final RepositoryService repository;
    private final BusinessAccess access;
    private final CurrentActor actor;
    private final AccountQueryService accounts;
    private final ReviewerMapper reviewers;

    /**
     * 注入模板持久化、JSON 映射、流程部署、权限和账号查询依赖。
     */
    public TemplateService(TemplateMapper mapper, ObjectMapper json, RepositoryService repository, BusinessAccess access,
                           CurrentActor actor, AccountQueryService accounts, ReviewerMapper reviewers) {
        this.mapper = mapper;
        this.json = json;
        this.repository = repository;
        this.access = access;
        this.actor = actor;
        this.accounts = accounts;
        this.reviewers = reviewers;
    }

    /**
     * 将持久化的节点配置 JSON 转换为审批步骤列表。
     */
    private List<TemplateCompiler.Step> steps(String raw) {
        return Arrays.asList(json.readValue(raw, TemplateCompiler.Step[].class));
    }

    /**
     * 读取保存的画布布局，兼容没有布局的历史模板。
     */
    private DiagramLayout layout(String raw) {
        return raw == null ? null : json.readValue(raw, DiagramLayout.class);
    }

    /**
     * 校验草稿查看权限并返回指定业务下的模板及发布状态。
     */
    public List<Draft> drafts(String business) {
        access.require(business, BusinessAccess.Capability.VIEW_DRAFT);
        return mapper.findDrafts(business).stream()
                .map(row -> new Draft(row.code(), row.name(), row.revision(), row.publishedVersion(),
                        row.revision() != row.publishedRevision(), row.enabled(), steps(row.steps()), layout(row.diagramLayout())))
                .toList();
    }

    /**
     * 校验模板编码、流程节点和画布布局，按修订号新建或更新草稿。
     */
    @Transactional
    public void save(String business, String code, Save request) {
        access.require(business, BusinessAccess.Capability.EDIT);
        if (!code.matches("[a-zA-Z][a-zA-Z0-9_-]{0,47}")) {
            throw new BusinessException(400, "模板编码格式无效");
        }
        String key = "wfTpl" + UUID.randomUUID().toString().replace("-", "");
        TemplateCompiler.compile(key, request.name(), request.steps(), request.diagramLayout());
        String data = json.writeValueAsString(request.steps());
        String diagram = null;
        if (request.diagramLayout() != null) {
            diagram = json.writeValueAsString(request.diagramLayout());
        }
        long editor = actor.get().id();
        if (request.revision() == 0) {
            mapper.insertDraft(business, code, key, request.name(), data, diagram, editor);
        } else if (mapper.updateDraft(business, code, request.revision(), request.name(), data, diagram, editor) != 1) {
            throw new BusinessException(409, "模板已修改，请刷新");
        }
    }

    /**
     * 锁定待发布草稿并校验修订号和审批人，部署模型后登记发布快照。
     */
    @Transactional
    public void publish(String business, String code, int revision) {
        access.require(business, BusinessAccess.Capability.PUBLISH);
        var draft = mapper.lockDraft(business, code);
        if (draft == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (draft.revision() != revision) {
            throw new BusinessException(409, "模板已修改，请刷新");
        }
        if (draft.publishedRevision() == revision) {
            throw new BusinessException(409, "当前草稿已经发布");
        }
        String key = draft.engineKey();
        String name = draft.name();
        String data = draft.steps();
        var nodes = steps(data);
        for (var step : nodes) {
            if (step.reviewerSource() == TemplateCompiler.ReviewerSource.ROLE && !reviewers.roleEnabled(step.roleId())) {
                throw new BusinessException(400, "模板包含未启用的审批角色");
            }
        }
        for (Long id : nodes.stream().flatMap(s -> s.approverIds().stream()).distinct().toList()) {
            var user = accounts.findById(id);
            if (user == null || !"ENABLED".equals(user.getStatus())) {
                throw new BusinessException(400, "模板包含无效审批人");
            }
        }
        var deployment = repository.createDeployment().name(name)
                .addBpmnModel(key + ".bpmn20.xml", TemplateCompiler.compile(key, name, nodes, layout(draft.diagramLayout()))).deploy();
        var definition = repository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
        int version = draft.publishedVersion() + 1;
        mapper.insertRelease(business, code, version, name, data, draft.diagramLayout(), definition.getId(), actor.get().id());
        mapper.markPublished(business, code, version);
    }

    /**
     * 返回指定模板的发布历史，由模板编辑或发布人员查看。
     */
    public List<VersionSummary> versions(String business, String code) {
        access.require(business, BusinessAccess.Capability.VIEW_DRAFT);
        return mapper.findVersions(business, code);
    }

    /**
     * 查看指定历史版本的不可变配置快照。
     */
    public Release version(String business, String code, int version) {
        access.require(business, BusinessAccess.Capability.VIEW_DRAFT);
        return toRelease(requireVersion(business, code, version));
    }

    /**
     * 把历史版本复制到干净的草稿，在另行发布前继续使用当前版本。
     */
    @Transactional
    public void restore(String business, String code, int version, int revision) {
        access.require(business, BusinessAccess.Capability.EDIT);
        var draft = requireDraftRevision(business, code, revision);
        if (draft.revision() != draft.publishedRevision()) {
            throw new BusinessException(409, "当前模板有未发布草稿，请先处理后再复制版本");
        }
        var release = requireVersion(business, code, version);
        if (mapper.updateDraft(business, code, revision, release.name(), release.steps(), release.diagramLayout(),
                actor.get().id()) != 1) {
            throw new BusinessException(409, "模板已修改，请刷新");
        }
    }

    /**
     * 控制模板可否启动新实例，保留旧版定义和所有在途实例。
     */
    @Transactional
    public void setAvailability(String business, String code, Availability request) {
        access.require(business, BusinessAccess.Capability.PUBLISH);
        var draft = requireDraftRevision(business, code, request.revision());
        if (draft.publishedVersion() == 0) {
            throw new BusinessException(409, "模板发布后才能设置启停状态");
        }
        if (draft.enabled() != request.enabled()
                && mapper.updateAvailability(business, code, request.revision(), request.enabled(), actor.get().id()) != 1) {
            throw new BusinessException(409, "模板已修改，请刷新");
        }
    }

    /** 锁定草稿并验证调用方持有的修订号。 */
    private TemplateDraftRow requireDraftRevision(String business, String code, int revision) {
        var draft = mapper.lockDraft(business, code);
        if (draft == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (draft.revision() != revision) {
            throw new BusinessException(409, "模板已修改，请刷新");
        }
        return draft;
    }

    /** 查找一个已发布快照，指定版本不存在时返回 404。 */
    private TemplateReleaseRow requireVersion(String business, String code, int version) {
        var release = mapper.findVersion(business, code, version);
        if (release == null) {
            throw new BusinessException(404, "模板发布版本不存在");
        }
        return release;
    }

    /** 发布历史列表项。 */
    @Schema(description = "模板发布历史记录")
    public record VersionSummary(
            @Schema(description = "模板发布版本号") int version,
            @Schema(description = "发布时的模板名称") String name,
            @Schema(description = "发布人标识") String publishedBy,
            @Schema(description = "发布时间") OffsetDateTime publishedAt
    ) {}

    /** 控制模板是否接受新申请的请求。 */
    @Schema(description = "已发布模板的启停设置")
    public record Availability(
            @Schema(description = "当前草稿修订号") @Positive int revision,
            @Schema(description = "是否允许发起新申请") @NotNull Boolean enabled
    ) {}

    /**
     * 校验发起权限，读取指定模板当前生效的发布版本。
     */
    public Release release(String business, String code) {
        access.require(business, BusinessAccess.Capability.START);
        var row = mapper.findCurrentRelease(business, code);
        if (row == null) {
            throw new BusinessException(404, "已发布的模板不存在");
        }
        return toRelease(row);
    }

    /**
     * 校验发起权限，列出指定业务下当前生效的模板版本。
     */
    public List<Release> published(String business) {
        access.require(business, BusinessAccess.Capability.START);
        return mapper.findCurrentReleases(business).stream().map(this::toRelease).toList();
    }

    /**
     * 解码版本快照中的节点配置和画布布局，构建调用方可消费的版本数据。
     */
    private Release toRelease(TemplateReleaseRow row) {
        return new Release(row.code(), row.name(), row.version(), row.definitionId(),
                steps(row.steps()), layout(row.diagramLayout()));
    }
}

