package com.leis.workflow.template;

import com.leis.common.common.BusinessException;
import com.leis.workflow.ApprovalService;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;

public final class TemplateCompiler {
    public record Position(int x, int y) {}

    public record Step(String name, ApprovalService.Mode mode, List<Long> approverIds,
                       BigDecimal minAmount, String returnTo, Position position) {
        public Step(String name, ApprovalService.Mode mode, List<Long> approverIds,
                    BigDecimal minAmount, String returnTo) {
            this(name, mode, approverIds, minAmount, returnTo, null);
        }
    }

    public static BpmnModel compile(String key, String name, List<Step> steps) {
        require(key != null && key.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"), "流程标识无效");
        require(validName(name), "流程名称无效");
        require(steps != null && !steps.isEmpty() && steps.size() <= 30, "流程需要一至三十个审批节点");
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
        edge(p, "start", "check0", null);
        edge(p, "revise", "check0", null);
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            validate(s, i);
            String check = "check" + i, review = "review" + i, route = "route" + i;
            String next = i + 1 < steps.size() ? "check" + (i + 1) : "end";
            node(p, check, new ExclusiveGateway());
            if (s.minAmount() != null) {
                edge(p, check, next, amountCondition(s, false));
            }
            edge(p, check, review, s.minAmount() == null ? null : amountCondition(s, true));
            UserTask task = node(p, review, new UserTask());
            task.setName(s.name());
            task.setAssignee("${reviewer}");
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

    private static String amountCondition(Step step, boolean include) {
        return "${amount " + (include ? ">=" : "<") + " '" + step.minAmount().toPlainString() + "'}";
    }

    public static final class ClearDecision implements ExecutionListener {
        public void notify(DelegateExecution execution) {
            execution.setVariable("decision", "");
        }
    }

    private static void validate(Step s, int index) {
        require(s != null && validName(s.name()) && s.mode() != null, "审批节点名称或模式无效");
        Position position = s.position();
        require(position == null || (position.x() >= -8000 && position.x() <= 8000
                && position.y() >= -4000 && position.y() <= 4000), "审批节点画布位置无效");
        boolean single = s.mode() == ApprovalService.Mode.SINGLE;
        List<Long> ids = s.approverIds();
        require(ids != null && ids.size() >= (single ? 1 : 2) && ids.size() <= (single ? 1 : 50)
                && ids.stream().allMatch(id -> id != null && id > 0)
                && new HashSet<>(ids).size() == ids.size(), "审批人配置无效");
        require("APPLICANT".equals(s.returnTo()) || index > 0 && "PREVIOUS".equals(s.returnTo()), "退回目标无效");
        BigDecimal amount = s.minAmount();
        require(amount == null || index > 0 && amount.signum() >= 0 && amount.scale() <= 2
                && (long) amount.precision() - amount.scale() <= 16, "金额条件无效");
    }

    private static boolean validName(String value) {
        return value != null && !value.isBlank() && value.length() <= 120
                && !value.contains("${") && !value.contains("#{")
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(400, message);
    }

    private static <T extends FlowNode> T node(Process p, String id, T node) {
        node.setId(id);
        p.addFlowElement(node);
        return node;
    }

    private static String edge(Process p, String source, String target, String condition) {
        SequenceFlow flow = new SequenceFlow(source, target);
        flow.setId("flow" + p.getFlowElements().size());
        flow.setConditionExpression(condition);
        p.addFlowElement(flow);
        return flow.getId();
    }
}


