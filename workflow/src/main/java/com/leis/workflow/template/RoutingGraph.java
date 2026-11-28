package com.leis.workflow.template;

import com.leis.common.common.BusinessException;
import com.leis.workflow.template.TemplateCompiler.Position;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 审批画布的有向流程图，连线顺序决定排他条件的匹配顺序。 */
@Schema(description = "带排他网关的审批路由图")
public record RoutingGraph(
        @Schema(description = "与审批步骤一一对应的画布节点标识") List<String> taskIds,
        @Schema(description = "画布中的排他网关") List<Gateway> gateways,
        @Schema(description = "有向连线，按网关条件优先级排列") List<Link> links
) {
    public static final String START = "Start_approval";
    public static final String END = "End_approval";

    /** 排他网关在画布中的位置。 */
    @Schema(description = "排他网关和画布位置")
    public record Gateway(
            @Schema(description = "网关标识") String id,
            @Schema(description = "网关画布位置") Position position
    ) {}

    /** 普通连线、默认连线及金额条件。 */
    public enum Operator { NONE, DEFAULT, GTE, GT, LTE, LT, EQ }

    /** 一条有向连线及其可选的金额比较条件。 */
    @Schema(description = "审批节点之间的连线与分支条件")
    public record Link(
            @Schema(description = "连线标识") String id,
            @Schema(description = "源节点标识") String source,
            @Schema(description = "目标节点标识") String target,
            @Schema(description = "普通、默认或金额比较运算") Operator operator,
            @Schema(description = "金额条件的比较值") BigDecimal amount
    ) {
        /** 使用白名单运算符构造引擎条件。 */
        String expression() {
            String comparison = switch (operator) {
                case GTE -> ">=";
                case GT -> ">";
                case LTE -> "<=";
                case LT -> "<";
                case EQ -> null;
                case NONE, DEFAULT -> null;
            };
            if (operator == Operator.EQ) {
                String literal = amount.toPlainString();
                return "${amount >= '" + literal + "' && amount <= '" + literal + "'}";
            }
            return comparison == null ? null : "${amount " + comparison + " '" + amount.toPlainString() + "'}";
        }

        /** 按模板金额规则在发起前模拟连线匹配。 */
        boolean matches(BigDecimal value) {
            int order = value.compareTo(amount);
            return switch (operator) {
                case GTE -> order >= 0;
                case GT -> order > 0;
                case LTE -> order <= 0;
                case LT -> order < 0;
                case EQ -> order == 0;
                case NONE, DEFAULT -> false;
            };
        }
    }

    /** 校验节点、连线、默认分支和连通性，拒绝循环和孤立节点。 */
    void validate(int stepCount) {
        require(taskIds != null && taskIds.size() == stepCount && gateways != null && !gateways.isEmpty()
                && gateways.size() <= 20 && links != null && links.size() <= 120, "网关或连线数量无效");
        var nodes = new HashSet<>(Set.of(START, END));
        for (String id : taskIds) {
            require(safeId(id) && nodes.add(id), "审批节点标识重复或无效");
        }
        var gatewayIds = new HashSet<String>();
        for (var gate : gateways) {
            require(gate != null && safeId(gate.id()) && nodes.add(gate.id())
                    && DiagramLayout.valid(gate.position()), "排他网关标识或位置无效");
            gatewayIds.add(gate.id());
        }
        var linkIds = new HashSet<String>();
        var incoming = new HashMap<String, Integer>();
        for (var link : links) {
            require(link != null && safeId(link.id()) && !nodes.contains(link.id()) && linkIds.add(link.id())
                    && nodes.contains(link.source()) && nodes.contains(link.target())
                    && !END.equals(link.source()) && !START.equals(link.target())
                    && link.operator() != null, "连线标识或端点无效");
            incoming.merge(link.target(), 1, Integer::sum);
        }
        for (String id : nodes) {
            var outgoing = outgoing(id);
            require(START.equals(id) || incoming.containsKey(id), "存在未连接的流程节点");
            if (END.equals(id)) {
                continue;
            }
            require(new HashSet<>(outgoing.stream().map(Link::target).toList()).size() == outgoing.size(),
                    "同一节点不能重复连接到相同目标");
            if (gatewayIds.contains(id) && outgoing.size() > 1) {
                require(outgoing.stream().filter(link -> link.operator() == Operator.DEFAULT).count() == 1,
                        "每个分支网关必须有一条默认连线");
                for (var link : outgoing) {
                    require(link.operator() == Operator.DEFAULT ? link.amount() == null
                            : link.operator() != Operator.NONE && validAmount(link.amount()), "请配置所有非默认连线的金额条件");
                }
            } else {
                require(outgoing.size() == 1 && outgoing.getFirst().operator() == Operator.NONE
                        && outgoing.getFirst().amount() == null, "非分支节点只允许一条普通出线");
            }
        }
        var queue = new ArrayDeque<String>();
        queue.add(START);
        int visited = 0;
        while (!queue.isEmpty()) {
            String id = queue.remove();
            visited++;
            for (var link : outgoing(id)) {
                if (incoming.merge(link.target(), -1, Integer::sum) == 0) {
                    queue.add(link.target());
                }
            }
        }
        require(visited == nodes.size(), "流程必须从开始节点连通到结束，且不能形成循环");
        var unchecked = new ArrayDeque<String>();
        var inspected = new HashSet<String>();
        unchecked.add(START);
        while (!unchecked.isEmpty()) {
            String id = unchecked.remove();
            require(!END.equals(id), "每条路径至少需要经过一个审批节点");
            if (inspected.add(id) && !taskIds.contains(id)) {
                outgoing(id).forEach(link -> unchecked.add(link.target()));
            }
        }
    }

    /** 计算该金额会执行的步骤下标，审批人预校验只覆盖这条路径。 */
    public List<Integer> path(BigDecimal amount) {
        var result = new ArrayList<Integer>();
        String current = START;
        while (!END.equals(current)) {
            int step = taskIds.indexOf(current);
            if (step >= 0) {
                result.add(step);
            }
            var next = outgoing(current);
            if (next.size() == 1) {
                current = next.getFirst().target();
            } else {
                Link fallback = next.stream().filter(link -> link.operator() == Operator.DEFAULT).findFirst().orElseThrow();
                current = next.stream().filter(link -> link.operator() != Operator.DEFAULT && link.matches(amount))
                        .findFirst().orElse(fallback).target();
            }
        }
        return result;
    }

    /** 按保存顺序获取指定节点的出线。 */
    List<Link> outgoing(String source) {
        return links.stream().filter(link -> source.equals(link.source())).toList();
    }

    /** 限制可持久化的模型标识，避免生成非法 BPMN ID。 */
    private static boolean safeId(String id) {
        return id != null && id.matches("[A-Za-z_][A-Za-z0-9_]{0,63}");
    }

    /** 判断金额是否符合模板的精度范围。 */
    private static boolean validAmount(BigDecimal amount) {
        return amount != null && amount.signum() >= 0 && amount.scale() <= 2
                && (long) amount.precision() - amount.scale() <= 16;
    }

    /** 将非法模型转换为可展示的校验错误。 */
    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new BusinessException(400, message);
        }
    }
}
