package com.leis.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.common.common.BusinessException;
import com.leis.common.common.PageResult;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.auth.ActorSnapshot;
import com.leis.workflow.execution.mapper.InstanceOperationMapper;
import com.leis.workflow.template.ReviewerDirectory;
import com.leis.workflow.template.mapper.ReviewerMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.TaskInfoQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 处理模板流程的审批待办、实例查询和运维操作。
 */
@Service
public class ApprovalService {
    public static final String FORM_OPINION = "opinion";
    public static final String FORM_REQUIRED_OPINION = "required-opinion";
    private static final String TEMPLATE_KEY_PATTERN = "wfTpl%";
    private static final String REVISION_TASK = "revise";

    private final RuntimeService runtime;
    private final TaskService taskService;
    private final HistoryService history;
    private final AccountQueryService accounts;
    private final InstanceOperationMapper operations;
    private final ReviewerMapper reviewerMapper;

    /**
     * 注入 Flowable 的运行时、任务、历史服务和审批人账号查询。
     */
    public ApprovalService(RuntimeService runtime, TaskService taskService, HistoryService history,
                           AccountQueryService accounts, InstanceOperationMapper operations, ReviewerMapper reviewerMapper) {
        this.runtime = runtime;
        this.taskService = taskService;
        this.history = history;
        this.accounts = accounts;
        this.operations = operations;
        this.reviewerMapper = reviewerMapper;
    }

    /**
     * 分页查询指定申请人发起的模板流程，按发起时间和实例标识倒序排列。
     */
    public PageResult<Application> applications(Long applicantId, int page, int pageSize) {
        return applications(applicantId, page, pageSize, new InboxFilter(null, null, null));
    }

    /**
     * 在数据库分页前应用申请标题、业务范围与状态筛选。
     */
    public PageResult<Application> applications(Long applicantId, int page, int pageSize, InboxFilter filter) {
        int offset = offset(page, pageSize);
        var query = history.createHistoricProcessInstanceQuery()
                .processDefinitionKeyLike(TEMPLATE_KEY_PATTERN).startedBy(applicantId.toString());
        filterApplications(query, filter);
        long total = query.count();
        var records = query.includeProcessVariables().orderByProcessInstanceStartTime().desc()
                .orderByProcessInstanceId().desc().listPage(offset, pageSize);
        return new PageResult<>(toApplications(records), total, page, pageSize);
    }

    /**
     * 按申请人和实例标识查询流程详情，对范围外的记录返回 404。
     */
    public Application application(Long applicantId, String instanceId) {
        var instance = history.createHistoricProcessInstanceQuery().processDefinitionKeyLike(TEMPLATE_KEY_PATTERN)
                .startedBy(applicantId.toString()).processInstanceId(instanceId)
                .includeProcessVariables().singleResult();
        if (instance == null) {
            throw new BusinessException(404, "审批申请不存在");
        }
        return toApplications(List.of(instance)).getFirst();
    }

    /**
     * 分页查询审批人的有效待办，按创建时间和任务标识倒序排列。
     */
    public PageResult<PendingTask> tasks(Long approverId, int page, int pageSize) {
        return tasks(approverId, page, pageSize, new InboxFilter(null, null, null));
    }

    /**
     * 查询符合业务和关键词条件的当前办理人待办。
     */
    public PageResult<PendingTask> tasks(Long approverId, int page, int pageSize, InboxFilter filter) {
        int offset = offset(page, pageSize);
        var query = assignedTasks(approverId);
        filterTasks(query, filter);
        long total = query.count();
        var records = query.includeProcessVariables().orderByTaskCreateTime().desc()
                .orderByTaskId().desc().listPage(offset, pageSize)
                .stream().map(ApprovalService::toPendingTask).toList();
        return new PageResult<>(records, total, page, pageSize);
    }

    /**
     * 校验任务归属和审批意见，写入决策变量并完成任务。
     */
    @Transactional
    public void decide(Long approverId, String taskId, DecisionRequest request) {
        Task task = taskForMutation(approverId, taskId);
        if (REVISION_TASK.equals(task.getTaskDefinitionKey())) {
            throw new BusinessException(409, "请使用重新提交接口处理修改待办");
        }
        Action action = request.action();
        String comment = optionalText(request.comment());
        if (FORM_REQUIRED_OPINION.equals(task.getFormKey()) && comment.isBlank()) {
            throw new BusinessException(400, "请填写审批意见");
        }
        complete(taskId, approverId, action.name(), comment, Map.of("decision", action.name(), "comment", comment));
    }

    /**
     * 完成退回修改任务，用新申请内容覆盖原内容并清空上次决策。
     */
    @Transactional
    public void resubmit(Long applicantId, String taskId, ResubmitRequest request) {
        Task task = taskForMutation(applicantId, taskId);
        if (!REVISION_TASK.equals(task.getTaskDefinitionKey())) {
            throw new BusinessException(409, "只有退回的申请可以重新提交");
        }
        complete(taskId, applicantId, "RESUBMIT", "", Map.of(
                "title", request.title().strip(), "description", optionalText(request.description()),
                "decision", "", "comment", ""));
    }

    /**
     * 校验申请归属后分页查询已完成且留有办理记录的任务。
     */
    public PageResult<RecordedAction> actions(Long applicantId, String instanceId, int page, int pageSize) {
        application(applicantId, instanceId);
        return actionPage(instanceId, page, pageSize);
    }

    /**
     * 读取已授权实例的办理历史，并按完成时间分页。
     */
    private PageResult<RecordedAction> actionPage(String instanceId, int page, int pageSize) {
        int offset = offset(page, pageSize);
        var query = history.createHistoricTaskInstanceQuery().processInstanceId(instanceId)
                .finished().taskVariableExists("recordedAction");
        long total = query.count();
        var records = query.includeTaskLocalVariables().orderByHistoricTaskInstanceEndTime().desc()
                .orderByTaskId().desc().listPage(offset, pageSize).stream().map(task -> {
                    var variables = task.getTaskLocalVariables();
                    return new RecordedAction(task.getId(), task.getName(),
                            task.getCompletedBy() == null ? task.getAssignee() : task.getCompletedBy(),
                            (String) variables.get("recordedAction"), (String) variables.get("recordedComment"),
                            task.getEndTime(), (String) variables.get("wfCompletedAt"));
                }).toList();
        return new PageResult<>(records, total, page, pageSize);
    }

    /**
     * 分页查询当前人员实际办理过的任务，排除被流程自动取消的任务。
     */
    public PageResult<CompletedTask> completed(Long actorId, int page, int pageSize, InboxFilter filter) {
        int offset = offset(page, pageSize);
        var query = completedBy(actorId);
        filterTasks(query, filter);
        long total = query.count();
        var records = query.includeTaskLocalVariables().includeProcessVariables()
                .orderByHistoricTaskInstanceEndTime().desc().orderByTaskId().desc()
                .listPage(offset, pageSize).stream().map(task -> {
                    var variables = task.getProcessVariables();
                    return new CompletedTask(task.getId(), task.getProcessInstanceId(),
                            (String) variables.get("title"), task.getName(),
                            (String) task.getTaskLocalVariables().get("recordedAction"), task.getEndTime());
                }).toList();
        return new PageResult<>(records, total, page, pageSize);
    }

    /** 管理员按在途或引擎作业异常筛选审批实例。 */
    public PageResult<Application> monitor(ActorSnapshot actor, int page, int pageSize, InboxFilter filter) {
        if (!actor.admin()) {
            throw new BusinessException(403, "需要超级管理员权限");
        }
        int offset = offset(page, pageSize);
        var query = history.createHistoricProcessInstanceQuery()
                .processDefinitionKeyLike(TEMPLATE_KEY_PATTERN).unfinished();
        filterApplications(query, new InboxFilter(filter.keyword(), filter.business(), null));
        if ("EXCEPTION".equals(filter.status())) {
            query.withJobException();
        } else if (filter.status() != null && !"ACTIVE".equals(filter.status())) {
            throw new BusinessException(400, "运维筛选状态无效");
        }
        long total = query.count();
        var rows = query.includeProcessVariables().orderByProcessInstanceStartTime().desc()
                .orderByProcessInstanceId().desc().listPage(offset, pageSize);
        return new PageResult<>(toApplications(rows), total, page, pageSize);
    }

    /** 由当前办理人转办审批任务。系统接受任何其他启用账号，包括申请人。 */
    @Transactional
    public void transfer(Long actorId, String taskId, TransferRequest request) {
        Task task = taskForMutation(actorId, taskId);
        if (REVISION_TASK.equals(task.getTaskDefinitionKey())) {
            throw new BusinessException(409, "退回修改任务不能转办");
        }
        if (actorId.equals(request.userId())) {
            throw new BusinessException(400, "转办对象不能是当前办理人");
        }
        var recipient = accounts.findById(request.userId());
        if (recipient == null || !"ENABLED".equals(recipient.getStatus())) {
            throw new BusinessException(400, "请选择启用的转办账号");
        }
        String reason = operationReason(request.reason());
        try {
            taskService.setAssignee(taskId, request.userId().toString());
            operations.record(task.getProcessInstanceId(), taskId, actorId, request.userId(), "TRANSFER", reason);
        } catch (FlowableObjectNotFoundException | FlowableOptimisticLockingException e) {
            throw conflict();
        }
    }

    /** 搜索任意启用用户，入口仅对可转办任务的当前办理人开放。 */
    public List<ReviewerDirectory.Reviewer> transferCandidates(Long actorId, String taskId, String keyword) {
        Task task = assignedTask(actorId, taskId);
        if (REVISION_TASK.equals(task.getTaskDefinitionKey())) {
            throw new BusinessException(409, "退回修改任务不能转办");
        }
        String term = keyword.strip();
        if (term.length() > 64) {
            throw new BusinessException(400, "转办搜索关键词过长");
        }
        return reviewerMapper.searchEnabled(term).stream().filter(item -> item.id() != actorId).toList();
    }

    /** 申请人可在首次审批完成前，或退回本人等待重提时撤回。 */
    @Transactional
    public void withdraw(Long actorId, String instanceId, OperationRequest request) {
        var instance = findInstance(instanceId);
        if (!actorId.toString().equals(instance.getStartUserId())) {
            throw new BusinessException(404, "审批申请不存在");
        }
        lockInstance(instanceId);
        instance = findInstance(instanceId);
        if (instance.getEndTime() != null || !canWithdraw(actorId, instance)) {
            throw new BusinessException(409, "当前申请已经办理且不在待重提节点，无法撤回");
        }
        endInstance(instanceId, actorId, "WITHDRAW", operationReason(request.reason()));
    }

    /** 超级管理员可终止仍在运行的审批实例，原因写入实例审计。 */
    @Transactional
    public void terminate(ActorSnapshot actor, String instanceId, OperationRequest request) {
        if (!actor.admin()) {
            throw new BusinessException(403, "需要超级管理员权限");
        }
        findInstance(instanceId);
        lockInstance(instanceId);
        if (findInstance(instanceId).getEndTime() != null) {
            throw new BusinessException(409, "已结束的审批实例不能终止");
        }
        endInstance(instanceId, actor.id(), "TERMINATE", operationReason(request.reason()));
    }

    /** 撤回属于申请人，办理记录存在时仍可从申请人修改节点撤回。 */
    private boolean canWithdraw(Long actorId, HistoricProcessInstance instance) {
        if (instance.getEndTime() != null || !actorId.toString().equals(instance.getStartUserId())) {
            return false;
        }
        String id = instance.getId();
        if (taskService.createTaskQuery().processInstanceId(id).active().taskDefinitionKey(REVISION_TASK)
                .taskAssignee(actorId.toString()).count() > 0) {
            return true;
        }
        return history.createHistoricTaskInstanceQuery().processInstanceId(id).finished()
                .taskVariableExists("recordedAction").count() == 0;
    }

    /** 保证操作原因经过后端校验，适用于非 HTTP 的调用方。 */
    private static String operationReason(String reason) {
        if (reason == null || reason.isBlank() || reason.strip().length() > 500) {
            throw new BusinessException(400, "请填写操作原因，最多 500 字");
        }
        return reason.strip();
    }

    /** 先写审计再结束实例，同一事务内任一失败都会回滚。 */
    private void endInstance(String instanceId, long actorId, String action, String reason) {
        try {
            operations.record(instanceId, null, actorId, null, action, reason);
            runtime.deleteProcessInstance(instanceId, action);
        } catch (FlowableObjectNotFoundException | FlowableOptimisticLockingException e) {
            throw conflict();
        }
    }

    /** 在获得实例锁后复查当前任务归属，避免使用加锁前的待办快照。 */
    private Task taskForMutation(Long actorId, String taskId) {
        var original = assignedTask(actorId, taskId);
        lockInstance(original.getProcessInstanceId());
        return assignedTask(actorId, taskId);
    }

    /** 将流程实例上的审批、重提与运维写操作串行化。 */
    private void lockInstance(String instanceId) {
        operations.ensureLock(instanceId);
        operations.lock(instanceId);
    }

    /** 所有流程写操作统一返回可重试的并发错误。 */
    private static BusinessException conflict() {
        return new BusinessException(409, "待办或审批实例已变更，请刷新后重试");
    }

    /** 校验实例属于审批模块，并读取用于详情或操作的历史数据。 */
    private HistoricProcessInstance findInstance(String id) {
        var instance = history.createHistoricProcessInstanceQuery()
                .processDefinitionKeyLike(TEMPLATE_KEY_PATTERN)
                .processInstanceId(id).includeProcessVariables().singleResult();
        if (instance == null) {
            throw new BusinessException(404, "审批申请不存在");
        }
        return instance;
    }

    /** 转办所需的接收人及原因。 */
    @Schema(description = "审批任务转办请求")
    public record TransferRequest(
            @Schema(description = "接收转办的启用用户标识") @NotNull @Positive Long userId,
            @Schema(description = "转办原因") @NotBlank @Size(max = 500) String reason
    ) {}

    /** 撤回或管理员终止所需的原因。 */
    @Schema(description = "结束审批实例的操作原因")
    public record OperationRequest(
            @Schema(description = "撤回或终止的原因") @NotBlank @Size(max = 500) String reason
    ) {}

    /** 用于展示转办、撤回和管理员终止的审计记录。 */
    @Schema(description = "审批实例操作记录")
    public record InstanceOperation(
            @Schema(description = "操作记录标识") long id,
            @Schema(description = "转办任务标识") String taskId,
            @Schema(description = "操作者账号标识") String actorId,
            @Schema(description = "转办接收人账号标识") String targetId,
            @Schema(description = "操作类型") String operation,
            @Schema(description = "操作原因") String reason,
            @Schema(description = "操作时间") OffsetDateTime createdAt
    ) {}

    /**
     * 向申请人、当前办理人和实际已办人员提供同一份审批实例详情。
     */
    public InstanceDetail detail(Long actorId, String instanceId, int page, int pageSize) {
        return detail(actorId, false, instanceId, page, pageSize);
    }

    /** 允许超级管理员定位实例，用于终止和运维。 */
    public InstanceDetail detail(Long actorId, boolean admin, String instanceId, int page, int pageSize) {
        offset(page, pageSize);
        var instance = findInstance(instanceId);
        var active = taskService.createTaskQuery().processInstanceId(instanceId).active()
                .includeProcessVariables().orderByTaskCreateTime().asc().orderByTaskId().asc().list();
        String actor = actorId.toString();
        boolean participant = admin || actor.equals(instance.getStartUserId())
                || active.stream().anyMatch(task -> actor.equals(task.getAssignee()))
                || completedBy(actorId).processInstanceId(instanceId).count() > 0
                || operations.wasTransferrer(instanceId, actorId);
        if (!participant) {
            throw new BusinessException(404, "审批申请不存在");
        }

        var actions = actionPage(instanceId, page, pageSize);
        var recent = operations.recent(instanceId);
        var userIds = new LinkedHashSet<Long>();
        addUserId(userIds, instance.getStartUserId());
        for (var task : active) {
            addUserId(userIds, task.getAssignee());
        }
        for (var action : actions.records()) {
            addUserId(userIds, action.actorId());
        }
        for (var operation : recent) {
            addUserId(userIds, operation.actorId());
            addUserId(userIds, operation.targetId());
        }
        var names = accounts.displayNames(userIds);
        var running = active.stream().map(task -> new ActiveWork(task.getId(), task.getName(),
                task.getAssignee(), displayName(names, task.getAssignee()), task.getCreateTime(),
                actor.equals(task.getAssignee()))).toList();
        var ownTasks = active.stream().filter(task -> actor.equals(task.getAssignee()))
                .map(ApprovalService::toPendingTask).toList();
        return new InstanceDetail(toApplication(instance, active.stream().map(Task::getName).distinct().toList()),
                running, ownTasks, actions, names, recent,
                canWithdraw(actorId, instance), admin && instance.getEndTime() == null);
    }

    /**
     * 查询由指定账号完成且留有办理动作的当前模块任务。
     */
    private HistoricTaskInstanceQuery completedBy(Long actorId) {
        return history.createHistoricTaskInstanceQuery()
                .processDefinitionKeyLike(TEMPLATE_KEY_PATTERN)
                .taskCompletedBy(actorId.toString()).finished().taskVariableExists("recordedAction");
    }

    /**
     * 以相同条件筛选待办与已办，在数据库分页前完成过滤。
     */
    private static void filterTasks(TaskInfoQuery<?, ?> query, InboxFilter filter) {
        if (filter.business() != null) {
            query.processVariableValueEquals("businessCode", filter.business());
        }
        if (filter.keyword() != null) {
            query.or().processVariableValueLikeIgnoreCase("title", like(filter.keyword()))
                    .processInstanceBusinessKeyLikeIgnoreCase(like(filter.keyword())).endOr();
        }
    }

    /**
     * 按流程决策和运行状态筛选申请。
     */
    private static void filterApplications(HistoricProcessInstanceQuery query, InboxFilter filter) {
        if (filter.business() != null) {
            query.variableValueEquals("businessCode", filter.business());
        }
        if (filter.keyword() != null) {
            query.or().variableValueLikeIgnoreCase("title", like(filter.keyword()))
                    .processInstanceBusinessKeyLikeIgnoreCase(like(filter.keyword())).endOr();
        }
        if (filter.status() == null) {
            return;
        }
        switch (filter.status()) {
            case "PENDING" -> query.unfinished().variableValueNotEquals("decision", "RETURN");
            case "RETURNED" -> query.unfinished().variableValueEquals("decision", "RETURN");
            case "APPROVED" -> query.finished().notDeleted().variableValueEquals("decision", "APPROVE");
            case "REJECTED" -> query.finished().notDeleted().variableValueEquals("decision", "REJECT");
            case "CANCELLED" -> query.deleted();
            default -> throw new BusinessException(400, "申请状态无效");
        }
    }

    /**
     * 构造标题和单号的包含查询。关键词中的百分号和下划线按原字符匹配。
     */
    private static String like(String keyword) {
        return "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /**
     * 将数字型账号标识纳入批量显示名查询，历史非数字标识仍可回显原值。
     */
    private static void addUserId(java.util.Set<Long> ids, String id) {
        if (id == null) {
            return;
        }
        try {
            ids.add(Long.valueOf(id));
        } catch (NumberFormatException ignored) {
            // 历史引擎账号标识可能不对应系统用户。
        }
    }

    /**
     * 缺少账号显示名时回显历史任务中的办理人标识。
     */
    private static String displayName(Map<String, String> names, String id) {
        return id == null ? null : names.getOrDefault(id, id);
    }

    /**
     * 审批中心筛选条件，空白按未填写处理。
     */
    @Schema(description = "审批中心关键词、业务与申请状态筛选")
    public record InboxFilter(
            @Schema(description = "按申请标题或业务单号搜索") String keyword,
            @Schema(description = "流程业务编码") String business,
            @Schema(description = "我的申请状态") String status
    ) {
        /** 归一化筛选条件并限制输入长度。 */
        public InboxFilter {
            keyword = trimFilter(keyword, 120);
            business = trimFilter(business, 64);
            status = trimFilter(status, 16);
        }

        /** 对非空筛选值去除两端空格并检查长度。 */
        private static String trimFilter(String value, int limit) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String trimmed = value.strip();
            if (trimmed.length() > limit) {
                throw new BusinessException(400, "筛选条件过长");
            }
            return trimmed;
        }
    }

    /** 已办列表中的个人办理记录。 */
    @Schema(description = "当前用户的已办任务")
    public record CompletedTask(
            @Schema(description = "已办任务标识") String taskId,
            @Schema(description = "流程实例标识") String instanceId,
            @Schema(description = "申请标题") String title,
            @Schema(description = "已办节点名称") String nodeName,
            @Schema(description = "实际提交的办理动作") String action,
            @Schema(description = "任务完成时间") Date completedAt
    ) {}

    /** 当前节点上尚未完成的任务及处理人。 */
    @Schema(description = "审批实例中当前尚未完成的任务")
    public record ActiveWork(
            @Schema(description = "当前任务标识") String taskId,
            @Schema(description = "当前审批节点名称") String nodeName,
            @Schema(description = "当前处理人账号标识") String actorId,
            @Schema(description = "当前处理人显示名称") String actorName,
            @Schema(description = "待办任务创建时间") Date createTime,
            @Schema(description = "该任务是否分配给当前用户") boolean mine
    ) {}

    /** 统一审批详情的申请信息、当前办理情况及历史时间线。 */
    @Schema(description = "统一审批实例详情")
    public record InstanceDetail(
            @Schema(description = "申请与模板版本信息") Application application,
            @Schema(description = "所有当前处理中的任务") List<ActiveWork> active,
            @Schema(description = "当前用户可以办理的任务") List<PendingTask> ownTasks,
            @Schema(description = "实例办理时间线") PageResult<RecordedAction> actions,
            @Schema(description = "本页办理记录涉及的账号显示名，键为账号标识") Map<String, String> actorNames,
            @Schema(description = "实例最近 20 条流程操作") List<InstanceOperation> operations,
            @Schema(description = "当前用户是否可以撤回申请") boolean canWithdraw,
            @Schema(description = "当前用户是否可以管理员终止") boolean canTerminate
    ) {}

    /**
     * 记录任务局部审计变量并完成任务，将任务消失或并发冲突转换为 409。
     */
    private void complete(String taskId, Long actorId, String action, String comment, Map<String, Object> variables) {
        try {
            taskService.setVariablesLocal(taskId, Map.of("recordedAction", action, "recordedComment", comment));
            taskService.complete(taskId, actorId.toString(), variables);
        } catch (FlowableObjectNotFoundException | FlowableOptimisticLockingException e) {
            throw conflict();
        }
    }

    /**
     * 获取当前操作者的指定有效待办，找不到时返回 404。
     */
    private Task assignedTask(Long actorId, String taskId) {
        Task task = assignedTasks(actorId).taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException(404, "待办任务不存在");
        }
        return task;
    }

    /**
     * 构造按流程类型、办理人及有效状态过滤的待办查询。
     */
    private TaskQuery assignedTasks(Long approverId) {
        return taskService.createTaskQuery().processDefinitionKeyLike(TEMPLATE_KEY_PATTERN)
                .taskAssignee(approverId.toString()).active();
    }

    /** 批量读取当前页在途实例的人工待办节点。会签任务共用一个节点名称。 */
    private List<Application> toApplications(List<HistoricProcessInstance> instances) {
        var runningIds = instances.stream().filter(instance -> instance.getEndTime() == null)
                .map(HistoricProcessInstance::getId).toList();
        var nodeNames = new HashMap<String, LinkedHashSet<String>>();
        if (!runningIds.isEmpty()) {
            var active = taskService.createTaskQuery().processInstanceIdIn(runningIds).active()
                    .orderByTaskCreateTime().asc().orderByTaskId().asc().list();
            for (var task : active) {
                nodeNames.computeIfAbsent(task.getProcessInstanceId(), id -> new LinkedHashSet<>()).add(task.getName());
            }
        }
        return instances.stream().map(instance -> {
            var names = nodeNames.get(instance.getId());
            return toApplication(instance, names == null ? List.of() : List.copyOf(names));
        }).toList();
    }

    /**
     * 把历史实例与流程变量转换为申请详情。
     */
    private static Application toApplication(HistoricProcessInstance instance, List<String> currentNodeNames) {
        var variables = instance.getProcessVariables();
        List<String> approverIds = variables.get("approverIds") instanceof List<?> ids
                ? ids.stream().map(String.class::cast).toList() : List.of();
        String amount = variables.get("amount") instanceof BigDecimal value ? value.toPlainString() : null;
        return new Application(instance.getId(), (String) variables.get("title"),
                (String) variables.get("description"), status(instance, variables), (String) variables.get("comment"),
                instance.getStartTime(), instance.getEndTime(), approverIds, (String) variables.get("businessCode"),
                (String) variables.get("templateCode"), (Integer) variables.get("templateVersion"),
                instance.getBusinessKey(), instance.getStartUserId(), amount, currentNodeNames);
    }

    /**
     * 按实例结束状态、取消原因和决策变量推导申请状态。
     */
    private static String status(HistoricProcessInstance instance, Map<String, Object> variables) {
        var decision = (String) variables.get("decision");
        if (instance.getEndTime() == null) {
            return "RETURN".equals(decision) ? "RETURNED" : "PENDING";
        }
        if (instance.getDeleteReason() != null) {
            return "CANCELLED";
        }
        if ("APPROVE".equals(decision)) {
            return "APPROVED";
        }
        if ("REJECT".equals(decision)) {
            return "REJECTED";
        }
        return "CANCELLED";
    }

    /**
     * 从任务和流程变量提取待办信息、表单配置与退回能力。
     */
    private static PendingTask toPendingTask(Task task) {
        var variables = task.getProcessVariables();
        String type = REVISION_TASK.equals(task.getTaskDefinitionKey()) ? "RESUBMIT" : "REVIEW";
        return new PendingTask(task.getId(), task.getProcessInstanceId(), (String) variables.get("title"),
                (String) variables.get("description"), (String) variables.get("applicantId"), task.getCreateTime(), type, task.getName(),
                "REVIEW".equals(type), task.getFormKey(),
                (String) variables.get("wfEntered_" + task.getTaskDefinitionKey()));
    }

    /**
     * 校验分页范围并计算 Flowable 查询偏移量，防止整数溢出。
     */
    private static int offset(int page, int pageSize) {
        long offset = (page - 1L) * pageSize;
        if (page < 1 || pageSize < 1 || pageSize > 100 || offset > Integer.MAX_VALUE - pageSize) {
            throw new BusinessException(400, "分页参数无效");
        }
        return (int) offset;
    }

    /**
     * 将缺省文本转换为空串，并去除已填写文本两端的空白。
     */
    private static String optionalText(String text) {
        return text == null ? "" : text.strip();
    }

    /**
     * 定义审批节点使用的单人、或签、会签和顺序审批模式。
     */
    public enum Mode {
        SINGLE, ANY, ALL, SEQUENTIAL
    }

    /**
     * 定义通过、驳回和退回三种审批动作。
     */
    public enum Action {
        APPROVE, REJECT, RETURN
    }

    /**
     * 办理审批任务时提交的动作与意见。
     */
    @Schema(description = "办理审批任务时提交的动作与意见")
    public record DecisionRequest(
        @Schema(description = "审批动作，支持 APPROVE、REJECT 和 RETURN")
        @NotNull Action action,
        @Schema(description = "办理时填写的审批意见")
        @Size(max = 1000) String comment
    ) {
    }

    /**
     * 退回后重新提交申请时可更新的标题和说明。
     */
    @Schema(description = "退回后重新提交申请时可更新的标题和说明")
    public record ResubmitRequest(
        @Schema(description = "审批申请标题")
        @NotBlank @Size(max = 120) String title,
        @Schema(description = "申请内容的补充说明")
        @Size(max = 1000) String description
    ) {
    }

    /**
     * 返回审批申请的当前状态、起止时间及关联模板信息。
     */
    @Schema(description = "返回审批申请的当前状态、起止时间及关联模板信息")
    public record Application(
        @Schema(description = "Flowable 流程实例标识")
        String id,
        @Schema(description = "审批申请标题")
        String title,
        @Schema(description = "申请内容的补充说明")
        String description,
        @Schema(description = "申请状态，取值为 PENDING、RETURNED、APPROVED、REJECTED 或 CANCELLED")
        String status,
        @Schema(description = "办理时填写的审批意见")
        String comment,
        @Schema(description = "流程开始时间")
        Date startTime,
        @Schema(description = "流程结束时间，运行时未设置")
        Date endTime,
        @Schema(description = "配置的审批人标识列表")
        List<String> approverIds,
        @Schema(description = "流程模板所属的业务编码")
        String businessCode,
        @Schema(description = "流程模板编码")
        String templateCode,
        @Schema(description = "本次申请实际使用的模板发布版本")
        Integer templateVersion,
        @Schema(description = "用于关联业务单据的业务标识")
        String businessKey,
        @Schema(description = "申请人账号标识")
        String applicantId,
        @Schema(description = "申请金额的十进制字符串")
        String amount,
        @Schema(description = "当前活跃人工审批或修改节点的名称，流程结束时为空列表")
        List<String> currentNodeNames
    ) {
    }

    /**
     * 返回待办的办理类型、节点表单和退回能力等信息。
     */
    @Schema(description = "返回待办的办理类型、节点表单和退回能力等信息")
    public record PendingTask(
        @Schema(description = "Flowable 待办任务标识")
        String id,
        @Schema(description = "Flowable 流程实例标识")
        String instanceId,
        @Schema(description = "审批申请标题")
        String title,
        @Schema(description = "申请内容的补充说明")
        String description,
        @Schema(description = "发起审批申请的用户标识")
        String applicantId,
        @Schema(description = "待办任务创建时间")
        Date createTime,
        @Schema(description = "待办类型，REVIEW 表示审批，RESUBMIT 表示修改后重新提交")
        String type,
        @Schema(description = "当前审批节点的显示名称")
        String nodeName,
        @Schema(description = "该任务是否允许执行退回操作")
        boolean returnAllowed,
        @Schema(description = "审批表单标识，支持 opinion 和 required-opinion，省略时沿用默认行为")
        String formKey,
        @Schema(description = "监听器记录的节点进入时间，未记录时为空")
        String nodeEnteredAt
    ) {
    }

    /**
     * 返回已办任务的办理人、审批意见及完成时间。
     */
    @Schema(description = "返回已办任务的办理人、审批意见及完成时间")
    public record RecordedAction(
        @Schema(description = "Flowable 任务标识")
        String taskId,
        @Schema(description = "办理时的流程节点名称")
        String nodeName,
        @Schema(description = "办理任务的用户标识")
        String actorId,
        @Schema(description = "已记录的动作，包含通过、驳回、退回和重新提交")
        String action,
        @Schema(description = "办理时填写的审批意见")
        String comment,
        @Schema(description = "历史任务的完成时间")
        Date time,
        @Schema(description = "任务监听器记录的完成时间，未记录时为空")
        String listenerCompletedAt
    ) {
    }
}

