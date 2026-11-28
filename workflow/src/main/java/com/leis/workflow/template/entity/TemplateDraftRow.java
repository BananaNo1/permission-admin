package com.leis.workflow.template.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 映射流程模板草稿的持久化字段，包括引擎标识、节点配置与修订状态。
 */
@Schema(description = "流程模板草稿持久化记录")
public record TemplateDraftRow(
        @Schema(description = "业务范围内唯一的模板编码")
        String code,
        @Schema(description = "对应的 Flowable 流程定义键")
        String engineKey,
        @Schema(description = "模板草稿名称")
        String name,
        @Schema(description = "草稿审批节点配置的 JSON 文本")
        String steps,
        @Schema(description = "流程画布布局的 JSON 文本，未保存布局时为空")
        String diagramLayout,
        @Schema(description = "当前草稿修订号")
        int revision,
        @Schema(description = "最新发布版本号，零表示未发布")
        int publishedVersion,
        @Schema(description = "最新发布版本对应的草稿修订号")
        int publishedRevision,
        @Schema(description = "是否允许发起新申请")
        boolean enabled
) {}
