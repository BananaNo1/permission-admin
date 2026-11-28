package com.leis.workflow.template;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.common.common.BusinessException;
import com.leis.workflow.template.TemplateCompiler.Position;

import java.util.List;

/**
 * 保存流程图起止节点坐标与连线拐点，用于恢复画布布局。
 */
@Schema(description = "保存流程图起止节点坐标与连线拐点，用于恢复画布布局")
public record DiagramLayout(
    @Schema(description = "开始节点的画布坐标")
    Position start,
    @Schema(description = "结束节点的画布坐标")
    Position end,
    @Schema(description = "按审批路径顺序保存的各段连线拐点")
    List<List<Position>> waypoints,
    @Schema(description = "条件分支的可选路由图，历史线性模板为空")
    RoutingGraph routing
) {
    /** 兼容只保存线性路径画布的调用方。 */
    public DiagramLayout(Position start, Position end, List<List<Position>> waypoints) {
        this(start, end, waypoints, null);
    }

    /**
     * 校验起止坐标、连线数量及拐点边界，确保布局与审批步骤匹配。
     */
    void validate(int stepCount) {
        if (routing != null) {
            routing.validate(stepCount);
        }
        int linkCount = routing == null ? stepCount + 1 : routing.links().size();
        if (!valid(start) || !valid(end) || waypoints == null || waypoints.size() != linkCount
                || waypoints.stream().anyMatch(line -> line == null || line.size() < 2 || line.size() > 64
                    || line.stream().anyMatch(point -> !valid(point)))) {
            throw new BusinessException(400, "画布坐标或连线布局无效");
        }
    }

    /**
     * 判断坐标是否存在且在画布可接受的范围内。
     */
    static boolean valid(Position point) {
        return point != null && point.x() >= -10000 && point.x() <= 10000
                && point.y() >= -6000 && point.y() <= 6000;
    }
}
