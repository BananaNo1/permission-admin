package com.leis.workflow;

import com.leis.common.common.BusinessException;
import com.leis.common.common.PageResult;
import com.leis.permission.auth.AccountQueryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class ApprovalService {
    public static final String FORM_OPINION = "opinion";
    public static final String FORM_REQUIRED_OPINION = "required-opinion";
    private static final String PROCESS_KEY = "singleApproval";
    private static final String LEGACY_REVIEW = "review";
    private static final String REVISION_TASK = "revise";
    private static final int MAX_APPROVERS = 50;

    private final RuntimeService runtime;
    private final TaskService taskService;
    private final HistoryService history;
    private final AccountQueryService accounts;

    public ApprovalService(RuntimeService runtime, TaskService taskService, HistoryService history,
                           AccountQueryService accounts) {
        this.runtime = runtime;
        this.taskService = taskService;
        this.history = history;
        this.accounts = accounts;
    }

    public Application start(Long applicantId, StartRequest request) {
        Mode mode = request.mode() == null ? Mode.SINGLE : request.mode();
        var approverIds = approverIds(applicantId, request, mode);
        var context = Authentication.getAuthenticationContext();
        var previous = context.getPrincipal();
        String title = request.title().strip();
        String description = optionalText(request.description());
        Authentication.setAuthenticatedUserId(applicantId.toString());
        try {
            var instance = runtime.startProcessInstanceByKey(PROCESS_KEY, Map.of(
                    "title", title,
                    "description", description,
                    "applicantId", applicantId.toString(),
                    "approverIds", new ArrayList<>(approverIds),
                    "mode", mode.name(),
                    "decision", ""));
            return new Application(instance.getId(), title, description, singleApprover(mode, approverIds),
                    "PENDING", null, instance.getStartTime(), null, mode, approverIds, null, null, null, null);
        } finally {
            context.setPrincipal(previous);
        }
    }

    public PageResult<Application> applications(Long applicantId, int page, int pageSize) {
        int offset = offset(page, pageSize);
        var query = history.createHistoricProcessInstanceQuery()
                .or().processDefinitionKey(PROCESS_KEY).processDefinitionKeyLike("wfTpl%").endOr().startedBy(applicantId.toString());
        long total = query.count();
        var records = query.includeProcessVariables().orderByProcessInstanceStartTime().desc()
                .orderByProcessInstanceId().desc().listPage(offset, pageSize)
                .stream().map(ApprovalService::toApplication).toList();
        return new PageResult<>(records, total, page, pageSize);
    }

    public Application application(Long applicantId, String instanceId) {
        var instance = history.createHistoricProcessInstanceQuery().or().processDefinitionKey(PROCESS_KEY).processDefinitionKeyLike("wfTpl%").endOr()
                .startedBy(applicantId.toString()).processInstanceId(instanceId)
                .includeProcessVariables().singleResult();
        if (instance == null) {
            throw new BusinessException(404, "审批申请不存在");
        }
        return toApplication(instance);
    }

    public PageResult<PendingTask> tasks(Long approverId, int page, int pageSize) {
        int offset = offset(page, pageSize);
        var query = assignedTasks(approverId);
        long total = query.count();
        var records = query.includeProcessVariables().orderByTaskCreateTime().desc()
                .orderByTaskId().desc().listPage(offset, pageSize)
                .stream().map(ApprovalService::toPendingTask).toList();
        return new PageResult<>(records, total, page, pageSize);
    }

    @Transactional
    public void decide(Long approverId, String taskId, DecisionRequest request) {
        Task task = assignedTask(approverId, taskId);
        if (REVISION_TASK.equals(task.getTaskDefinitionKey())) {
            throw new BusinessException(409, "请使用重新提交接口处理修改待办");
        }
        Action action = request.resolveAction();
        String comment = optionalText(request.comment());
        if (FORM_REQUIRED_OPINION.equals(task.getFormKey()) && comment.isBlank()) {
            throw new BusinessException(400, "请填写审批意见");
        }
        boolean legacy = LEGACY_REVIEW.equals(task.getTaskDefinitionKey());
        if (legacy && action == Action.RETURN) {
            throw new BusinessException(409, "该申请使用旧版流程，仅支持通过或驳回");
        }
        var variables = legacy ? Map.<String, Object>of("approved", action == Action.APPROVE, "comment", comment)
                : Map.<String, Object>of("decision", action.name(), "comment", comment);
        complete(taskId, approverId, action.name(), comment, variables);
    }

    @Transactional
    public void resubmit(Long applicantId, String taskId, ResubmitRequest request) {
        Task task = assignedTask(applicantId, taskId);
        if (!REVISION_TASK.equals(task.getTaskDefinitionKey())) {
            throw new BusinessException(409, "只有退回的申请可以重新提交");
        }
        complete(taskId, applicantId, "RESUBMIT", "", Map.of(
                "title", request.title().strip(), "description", optionalText(request.description()),
                "decision", "", "comment", ""));
    }

    public PageResult<RecordedAction> actions(Long applicantId, String instanceId, int page, int pageSize) {
        int offset = offset(page, pageSize);
        application(applicantId, instanceId);
        var query = history.createHistoricTaskInstanceQuery().processInstanceId(instanceId)
                .finished().taskVariableExists("recordedAction");
        long total = query.count();
        var records = query.includeTaskLocalVariables().orderByHistoricTaskInstanceEndTime().desc()
                .orderByTaskId().desc().listPage(offset, pageSize).stream().map(task -> {
                    var variables = task.getTaskLocalVariables();
                    return new RecordedAction(task.getId(), task.getAssignee(),
                            (String) variables.get("recordedAction"), (String) variables.get("recordedComment"),
                            task.getEndTime(), (String) variables.get("wfCompletedAt"));
                }).toList();
        return new PageResult<>(records, total, page, pageSize);
    }

    private void complete(String taskId, Long actorId, String action, String comment, Map<String, Object> variables) {
        try {
            taskService.setVariablesLocal(taskId, Map.of("recordedAction", action, "recordedComment", comment));
            taskService.complete(taskId, actorId.toString(), variables);
        } catch (FlowableObjectNotFoundException | FlowableOptimisticLockingException e) {
            throw new BusinessException(409, "待办任务已变更，请刷新后重试");
        }
    }

    private Task assignedTask(Long actorId, String taskId) {
        Task task = assignedTasks(actorId).taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException(404, "待办任务不存在");
        }
        return task;
    }

    private TaskQuery assignedTasks(Long approverId) {
        return taskService.createTaskQuery().or().processDefinitionKey(PROCESS_KEY).processDefinitionKeyLike("wfTpl%").endOr()
                .taskAssignee(approverId.toString()).active();
    }

    private static Application toApplication(HistoricProcessInstance instance) {
        var variables = instance.getProcessVariables();
        var mode = variables.containsKey("templateCode") ? null : Mode.valueOf((String) variables.getOrDefault("mode", Mode.SINGLE.name()));
        String legacyApprover = (String) variables.get("approverId");
        List<String> approverIds = legacyApprover == null ? List.of() : List.of(legacyApprover);
        if (variables.get("approverIds") instanceof List<?> ids) {
            approverIds = ids.stream().map(String.class::cast).toList();
        }
        return new Application(instance.getId(), (String) variables.get("title"),
                (String) variables.get("description"), singleApprover(mode, approverIds),
                status(instance, variables), (String) variables.get("comment"),
                instance.getStartTime(), instance.getEndTime(), mode, approverIds, (String) variables.get("businessCode"), (String) variables.get("templateCode"), (Integer) variables.get("templateVersion"), instance.getBusinessKey());
    }

    private static String status(HistoricProcessInstance instance, Map<String, Object> variables) {
        var decision = (String) variables.get("decision");
        if (instance.getEndTime() == null) {
            return "RETURN".equals(decision) ? "RETURNED" : "PENDING";
        }
        if (instance.getDeleteReason() != null) {
            return "CANCELLED";
        }
        if ("APPROVE".equals(decision) || Boolean.TRUE.equals(variables.get("approved"))) {
            return "APPROVED";
        }
        if ("REJECT".equals(decision) || Boolean.FALSE.equals(variables.get("approved"))) {
            return "REJECTED";
        }
        return "CANCELLED";
    }

    private List<String> approverIds(Long applicantId, StartRequest request, Mode mode) {
        List<Long> ids = request.approverIds();
        if (request.approverId() != null) {
            if (ids != null) {
                throw new BusinessException(400, "请只填写一种审批人字段");
            }
            ids = List.of(request.approverId());
        }
        int minApprovers = mode == Mode.SINGLE ? 1 : 2;
        int maxApprovers = mode == Mode.SINGLE ? 1 : MAX_APPROVERS;
        if (ids == null || ids.size() < minApprovers || ids.size() > maxApprovers) {
            throw new BusinessException(400, "审批人数与审批模式不匹配");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(400, "审批人不能重复");
        }
        for (Long id : ids) {
            if (applicantId.equals(id)) {
                throw new BusinessException(400, "不能审批自己的申请");
            }
            if (id == null || id <= 0) {
                throw new BusinessException(400, "审批人编号无效");
            }
            var approver = accounts.findById(id);
            if (approver == null || !"ENABLED".equals(approver.getStatus())) {
                throw new BusinessException(400, "请选择已启用的审批人");
            }
        }
        return ids.stream().map(String::valueOf).toList();
    }

    private static String singleApprover(Mode mode, List<String> approverIds) {
        return mode == Mode.SINGLE && !approverIds.isEmpty() ? approverIds.getFirst() : null;
    }

    private static PendingTask toPendingTask(Task task) {
        var variables = task.getProcessVariables();
        String type = REVISION_TASK.equals(task.getTaskDefinitionKey()) ? "RESUBMIT" : "REVIEW";
        return new PendingTask(task.getId(), task.getProcessInstanceId(), (String) variables.get("title"),
                (String) variables.get("description"), (String) variables.get("applicantId"), task.getCreateTime(), type, task.getName(),
                "REVIEW".equals(type) && !LEGACY_REVIEW.equals(task.getTaskDefinitionKey()), task.getFormKey(),
                (String) variables.get("wfEntered_" + task.getTaskDefinitionKey()));
    }

    private static int offset(int page, int pageSize) {
        long offset = (page - 1L) * pageSize;
        if (page < 1 || pageSize < 1 || pageSize > 100 || offset > Integer.MAX_VALUE - pageSize) {
            throw new BusinessException(400, "分页参数无效");
        }
        return (int) offset;
    }

    private static String optionalText(String text) {
        return text == null ? "" : text.strip();
    }

    public record StartRequest(@NotBlank @Size(max = 120) String title,
                               @Positive Long approverId,
                               @Size(max = 1000) String description,
                               Mode mode, @Size(max = MAX_APPROVERS) List<@NotNull @Positive Long> approverIds) {
        public StartRequest(String title, Long approverId, String description) {
            this(title, approverId, description, null, null);
        }
    }

    public enum Mode {
        SINGLE, ANY, ALL, SEQUENTIAL
    }

    public enum Action {
        APPROVE, REJECT, RETURN
    }

    public record DecisionRequest(Boolean approved, @Size(max = 1000) String comment, Action action) {
        public DecisionRequest(Boolean approved, String comment) {
            this(approved, comment, null);
        }

        public DecisionRequest(Action action, String comment) {
            this(null, comment, action);
        }

        Action resolveAction() {
            if ((approved == null) == (action == null)) {
                throw new BusinessException(400, "请填写一种审批动作");
            }
            if (action != null) {
                return action;
            }
            return approved ? Action.APPROVE : Action.REJECT;
        }
    }

    public record ResubmitRequest(@NotBlank @Size(max = 120) String title, @Size(max = 1000) String description) {
    }

    public record Application(String id, String title, String description, String approverId,
                              String status, String comment, Date startTime, Date endTime,
                              Mode mode, List<String> approverIds, String businessCode, String templateCode, Integer templateVersion, String businessKey) {
    }

    public record PendingTask(String id, String instanceId, String title, String description,
                              String applicantId, Date createTime, String type, String nodeName, boolean returnAllowed,
                              String formKey, String nodeEnteredAt) {
    }

    public record RecordedAction(String taskId, String actorId, String action, String comment, Date time,
                                 String listenerCompletedAt) {
    }
}

