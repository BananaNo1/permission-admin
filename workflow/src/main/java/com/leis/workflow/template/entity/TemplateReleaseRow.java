package com.leis.workflow.template.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 映射模板发布版本的持久化字段及对应的 Flowable 定义标识。
 */
@Schema(description = "流程模板发布快照持久化记录")
public record TemplateReleaseRow(
        @Schema(description = "业务范围内唯一的模板编码")
        String code,
        @Schema(description = "发布时的模板名称")
        String name,
        @Schema(description = "模板发布版本号")
        int version,
        @Schema(description = "该版本对应的 Flowable 流程定义标识")
        String definitionId,
        @Schema(description = "发布时的审批节点配置 JSON 文本")
        String steps,
        @Schema(description = "发布时的画布布局 JSON 文本，未保存布局时为空")
        String diagramLayout
) {}
