package com.leis.workflow.template;

import com.leis.common.common.BusinessException;
import com.leis.workflow.template.TemplateCompiler.Position;

import java.util.List;

public record DiagramLayout(Position start, Position end, List<List<Position>> waypoints) {
    void validate(int stepCount) {
        if (!valid(start) || !valid(end) || waypoints == null || waypoints.size() != stepCount + 1
                || waypoints.stream().anyMatch(line -> line == null || line.size() < 2 || line.size() > 64
                    || line.stream().anyMatch(point -> !valid(point)))) {
            throw new BusinessException(400, "画布坐标或连线布局无效");
        }
    }

    private static boolean valid(Position point) {
        return point != null && point.x() >= -10000 && point.x() <= 10000
                && point.y() >= -6000 && point.y() <= 6000;
    }
}
