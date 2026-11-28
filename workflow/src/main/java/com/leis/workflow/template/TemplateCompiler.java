package com.leis.workflow.template;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.common.common.BusinessException;
import com.leis.workflow.ApprovalService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;

/**
 * 校验审批步骤并构建可部署的 Flowable BPMN 模型。
 */
public final class TemplateCompiler {
    /**
     * 表示流程图节点或连线拐点的二维坐标。
     */
    @Schema(description = "表示流程图节点或连线拐点的二维坐标")
    public record Position(
        @Schema(description = "画布横坐标")
        int x,
        @Schema(description = "画布纵坐标")
        int y
    ) {}

    /** 节点运行时用于确定审批人的规则。 */
    public enum ReviewerSource { FIXED, ROLE, DEPARTMENT_MANAGER }

    /**
     * 描述一个审批步骤的名称、办理方式、审批人及表单和监听配置。
     */
    @Schema(description = "描述一个审批步骤的名称、办理方式、审批人及表单和监听配置")
    public record Step(
        @Schema(description = "审批节点名称")
        String name,
        @Schema(description = "审批模式，支持 SINGLE、ANY、ALL 和 SEQUENTIAL")
        ApprovalService.Mode mode,
        @Schema(description = "配置的审批人标识列表")
        List<Long> approverIds,
        @Schema(description = "执行本步骤的最低金额，未配置时不限制")
        BigDecimal minAmount,
        @Schema(description = "退回位置，APPLICANT 表示申请人，PREVIOUS 表示上一实际执行的审批节点")
        String returnTo,
        @Schema(description = "审批节点的画布坐标")
        Position position,
        @Schema(description = "审批表单标识，支持 opinion 和 required-opinion，省略时沿用默认行为")
        String formKey,
        @Schema(description = "是否在进入本节点时记录时间，缺省为关闭")
        Boolean recordEntered,
        @Schema(description = "是否在本节点任务完成时记录时间，缺省为关闭")
        Boolean recordCompleted,
        @Schema(description = "审批人来源，缺省为指定成员")
        ReviewerSource reviewerSource,
        @Schema(description = "按角色选人时使用的角色标识")
        Long roleId
    ) {
        /**
         * 将旧草稿或请求中缺省的监听开关归一为关闭状态。
         */
        public Step {
            recordEntered = Boolean.TRUE.equals(recordEntered);
            recordCompleted = Boolean.TRUE.equals(recordCompleted);
            reviewerSource = reviewerSource == null ? ReviewerSource.FIXED : reviewerSource;
            approverIds = approverIds == null && reviewerSource != ReviewerSource.FIXED ? List.of() : approverIds;
        }


        /** 兼容旧调用方创建指定审批人步骤。 */
        public Step(String name, ApprovalService.Mode mode, List<Long> approverIds,
                    BigDecimal minAmount, String returnTo, Position position, String formKey,
                    Boolean recordEntered, Boolean recordCompleted) {
            this(name, mode, approverIds, minAmount, returnTo, position, formKey, recordEntered, recordCompleted, null, null);
        }

        /**
         * 构建带画布坐标、使用缺省表单和监听配置的审批步骤。
         */
        public Step(String name, ApprovalService.Mode mode, List<Long> approverIds,
                    BigDecimal minAmount, String returnTo, Position position) {
            this(name, mode, approverIds, minAmount, returnTo, position, null, false, false);
        }

        /**
         * 构建未设置坐标、表单和监听器的审批步骤。
         */
        public Step(String name, ApprovalService.Mode mode, List<Long> approverIds,
                    BigDecimal minAmount, String returnTo) {
            this(name, mode, approverIds, minAmount, returnTo, null);
        }
    }

    /**
     * 将步骤转换成含金额分支、多实例审批和退回路径的 Flowable 模型。
     */
    public static BpmnModel compile(String key, String name, List<Step> steps) {
        return compile(key, name, steps, null);
    }

    /** 编译已保存的线性审批或排他分支，二者共享审批任务语义。 */
    public static BpmnModel compile(String key, String name, List<Step> steps, DiagramLayout layout) {
        require(key != null && key.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"), "流程标识无效");
        require(validName(name), "流程名称无效");
        require(steps != null && !steps.isEmpty() && steps.size() <= 30, "流程需要一至三十个审批节点");
        if (layout != null) {
            layout.validate(steps.size());
        }
        RoutingGraph graph = layout == null ? null : layout.routing();
        BpmnModel model = new BpmnModel();
        Process p = new Process();
        p.setId(key);
        p.setName(name);
        p.setExecutable(true);
        model.addProcess(p);
        node(p, "start", new StartEvent());
        node(p, "end", new EndEvent());
        UserTask revise = node(p, "revise", new UserTask());
        revise.setName("修改申请");
        revise.setAssignee("${applicantId}");
        var entries = graph == null ? Map.<String, String>of() : graphEntries(p, graph);
        String initial = graph == null ? "check0" : entries.get(graph.outgoing(RoutingGraph.START).getFirst().target());
        edge(p, "start", initial, null);
        edge(p, "revise", initial, null);
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            validate(s, graph == null ? i : 1);
            String review = "review" + i, route = "route" + i;
            String next;
            if (graph == null) {
                String check = "check" + i;
                next = i + 1 < steps.size() ? "check" + (i + 1) : "end";
                node(p, check, new ExclusiveGateway());
                if (s.minAmount() != null) {
                    edge(p, check, next, amountCondition(s, false));
                }
                edge(p, check, review, s.minAmount() == null ? null : amountCondition(s, true));
            } else {
                require(s.minAmount() == null, "条件分支模板的金额须配置在网关连线上");
                next = entries.get(graph.outgoing(graph.taskIds().get(i)).getFirst().target());
            }
            UserTask task = node(p, review, new UserTask());
            task.setName(s.name());
            task.setAssignee("${reviewer}");
            task.setFormKey(s.formKey());
            if (s.recordEntered()) {
                task.getExecutionListeners().add(listener("start", RecordNodeEntry.class));
            }
            if (s.recordCompleted()) {
                task.getTaskListeners().add(listener("complete", RecordTaskCompletion.class));
            }
            MultiInstanceLoopCharacteristics loop = new MultiInstanceLoopCharacteristics();
            loop.setInputDataItem("${stepUsers" + i + "}");
            loop.setElementVariable("reviewer");
            loop.setSequential(s.mode() == ApprovalService.Mode.SEQUENTIAL);
            loop.setCompletionCondition(s.mode() == ApprovalService.Mode.ANY ? "${decision == 'APPROVE' || decision == 'RETURN' || nrOfCompletedInstances == nrOfInstances}"
                    : "${decision == 'RETURN' || decision == 'REJECT' || nrOfCompletedInstances == nrOfInstances}");
            task.setLoopCharacteristics(loop);
            node(p, route, new ExclusiveGateway());
            edge(p, review, route, null);
            edge(p, route, next, "${decision == 'APPROVE'}");
            edge(p, route, "end", "${decision == 'REJECT'}");
            if ("APPLICANT".equals(s.returnTo())) {
                edge(p, route, "revise", "${decision == 'RETURN'}");
            } else if (graph != null) {
                graphReturn(p, route, i, steps.size());
            } else {
                String back = "back" + i;
                edge(p, route, back, "${decision == 'RETURN'}");
                ExclusiveGateway gateway = node(p, back, new ExclusiveGateway());
                FlowableListener reset = new FlowableListener();
                reset.setEvent("start");
                reset.setImplementationType("class");
                reset.setImplementation(ClearDecision.class.getName());
                gateway.getExecutionListeners().add(reset);
                for (int j = i - 1; j >= 0; j--) {
                    Step prior = steps.get(j);
                    String target = edge(p, back, "review" + j,
                            prior.minAmount() == null ? null : amountCondition(prior, true));
                    if (prior.minAmount() == null) {
                        gateway.setDefaultFlow(target);
                        break;
                    }
                }
            }
        }
        return model;
    }

    /** 将画布网关转换为引擎节点，并按保存顺序挂载条件和默认连线。 */
    private static Map<String, String> graphEntries(Process process, RoutingGraph graph) {
        var entries = new HashMap<String, String>();
        entries.put(RoutingGraph.END, "end");
        for (int index = 0; index < graph.taskIds().size(); index++) {
            entries.put(graph.taskIds().get(index), "review" + index);
        }
        for (int index = 0; index < graph.gateways().size(); index++) {
            String id = "gate" + index;
            entries.put(graph.gateways().get(index).id(), id);
            node(process, id, new ExclusiveGateway());
        }
        for (var gateway : graph.gateways()) {
            String id = entries.get(gateway.id());
            var model = (ExclusiveGateway) process.getFlowElement(id);
            for (var link : graph.outgoing(gateway.id())) {
                String flow = edge(process, id, entries.get(link.target()), link.expression());
                if (link.operator() == RoutingGraph.Operator.DEFAULT) {
                    model.setDefaultFlow(flow);
                }
            }
        }
        return entries;
    }

    /** 退回到本次金额路径的实际前序节点，首个审批节点回到申请人。 */
    private static void graphReturn(Process process, String route, int step, int count) {
        String id = "back" + step;
        var gateway = node(process, id, new ExclusiveGateway());
        gateway.getExecutionListeners().add(listener("start", ClearDecision.class));
        edge(process, route, id, "${decision == 'RETURN'}");
        for (int index = 0; index < count; index++) {
            if (index != step) {
                edge(process, id, "review" + index, "${wfPrevious_" + step + " == " + index + "}");
            }
        }
        gateway.setDefaultFlow(edge(process, id, "revise", null));
    }

    /**
     * 生成通过或跳过金额门槛的 Flowable 条件表达式。
     */
    private static String amountCondition(Step step, boolean include) {
        return "${amount " + (include ? ">=" : "<") + " '" + step.minAmount().toPlainString() + "'}";
    }

    /**
     * 构建引用指定 Java 实现的 Flowable 类监听器配置。
     */
    private static FlowableListener listener(String event, Class<?> implementation) {
        FlowableListener listener = new FlowableListener();
        listener.setEvent(event);
        listener.setImplementationType("class");
        listener.setImplementation(implementation.getName());
        return listener;
    }

    /**
     * 在审批节点进入时将时间写入流程变量。
     */
    public static final class RecordNodeEntry implements ExecutionListener {
        /**
         * 按当前节点标识记录进入时间，供待办详情读取。
         */
        @Override
        public void notify(DelegateExecution execution) {
            execution.setVariable("wfEntered_" + execution.getCurrentActivityId(), Instant.now().toString());
        }
    }

    /**
     * 在审批任务完成时将时间写入任务局部变量。
     */
    public static final class RecordTaskCompletion implements TaskListener {
        /**
         * 记录任务完成时间，供该任务的历史办理记录读取。
         */
        @Override
        public void notify(DelegateTask task) {
            task.setVariableLocal("wfCompletedAt", Instant.now().toString());
        }
    }

    /**
     * 在流程退回前序节点时清理上一轮决定。
     */
    public static final class ClearDecision implements ExecutionListener {
        /**
         * 重置流程决策变量，避免上一轮决策影响重新进入的审批节点。
         */
        public void notify(DelegateExecution execution) {
            execution.setVariable("decision", "");
        }
    }

    /**
     * 校验节点名称、审批人数、表单、金额、坐标和退回位置等编译约束。
     */
    private static void validate(Step s, int index) {
        require(s != null && validName(s.name()) && s.mode() != null, "审批节点名称或模式无效");
        require(s.formKey() == null || ApprovalService.FORM_OPINION.equals(s.formKey())
                || ApprovalService.FORM_REQUIRED_OPINION.equals(s.formKey()), "审批表单无效");
        Position position = s.position();
        require(position == null || (position.x() >= -8000 && position.x() <= 8000
                && position.y() >= -4000 && position.y() <= 4000), "审批节点画布位置无效");
        List<Long> ids = s.approverIds();
        switch (s.reviewerSource()) {
            case FIXED -> require(s.roleId() == null && ids != null && validReviewerCount(s.mode(), ids.size())
                    && ids.stream().allMatch(id -> id != null && id > 0)
                    && new HashSet<>(ids).size() == ids.size(), "审批人配置无效");
            case ROLE -> require(s.roleId() != null && s.roleId() > 0 && ids.isEmpty(), "请选择审批角色");
            case DEPARTMENT_MANAGER -> require(s.mode() == ApprovalService.Mode.SINGLE && s.roleId() == null
                    && ids.isEmpty(), "部门负责人节点只能使用单人审批");
        }
        require("APPLICANT".equals(s.returnTo()) || index > 0 && "PREVIOUS".equals(s.returnTo()), "退回目标无效");
        BigDecimal amount = s.minAmount();
        require(amount == null || index > 0 && amount.signum() >= 0 && amount.scale() <= 2
                && (long) amount.precision() - amount.scale() <= 16, "金额条件无效");
    }

    /** 检查解析后的审批人数是否符合所选模式。 */
    public static boolean validReviewerCount(ApprovalService.Mode mode, int count) {
        return mode == ApprovalService.Mode.SINGLE ? count == 1 : count >= 2 && count <= 50;
    }

    /**
     * 拒绝空白、过长或包含表达式标记与控制字符的名称。
     */
    private static boolean validName(String value) {
        return value != null && !value.isBlank() && value.length() <= 120
                && !value.contains("${") && !value.contains("#{")
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    /**
     * 将模板校验失败转换为可返回给调用方的业务错误。
     */
    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new BusinessException(400, message);
        }
    }

    /**
     * 为流程节点设置标识并加入流程模型。
     */
    private static <T extends FlowNode> T node(Process p, String id, T node) {
        node.setId(id);
        p.addFlowElement(node);
        return node;
    }

    /**
     * 在源节点和目标节点之间添加有向连线，可附带条件表达式。
     */
    private static String edge(Process p, String source, String target, String condition) {
        SequenceFlow flow = new SequenceFlow(source, target);
        flow.setId("flow" + p.getFlowElements().size());
        flow.setConditionExpression(condition);
        p.addFlowElement(flow);
        return flow.getId();
    }
}


