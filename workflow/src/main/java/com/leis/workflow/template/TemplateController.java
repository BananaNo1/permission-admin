package com.leis.workflow.template;

import com.leis.common.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 提供流程模板查询、草稿保存、发布和设计期审批人选择接口。
 */
@RestController
@RequestMapping("/api/workflow/businesses/{business}/templates")
public class TemplateController {
    private final TemplateService templates;
    private final ReviewerDirectory reviewers;

    /**
     * 注入模板服务及审批人目录。
     */
    public TemplateController(TemplateService templates, ReviewerDirectory reviewers) {
        this.templates = templates;
        this.reviewers = reviewers;
    }

    /**
     * 查询指定业务的流程模板草稿。
     */
    @GetMapping
    public ApiResponse<List<TemplateService.Draft>> drafts(@PathVariable String business) {
        return ApiResponse.ok(templates.drafts(business));
    }

    /**
     * 按关键词搜索或按标识回显流程模板审批人。
     */
    @GetMapping("/reviewers")
    public ApiResponse<List<ReviewerDirectory.Reviewer>> reviewers(@PathVariable String business,
                                                       @RequestParam(defaultValue = "") String keyword,
                                                       @RequestParam(required = false) List<Long> ids) {
        return ApiResponse.ok(reviewers.find(business, keyword, ids));
    }

    /** 返回当前流程业务可配置的启用角色。 */
    @GetMapping("/reviewer-roles")
    public ApiResponse<List<ReviewerDirectory.RoleOption>> reviewerRoles(@PathVariable String business) {
        return ApiResponse.ok(reviewers.roles(business));
    }

    /**
     * 校验保存请求并提交新建或更新草稿操作。
     */
    @PutMapping("/{code}")
    public ApiResponse<Void> save(@PathVariable String business, @PathVariable String code,
                     @Valid @RequestBody TemplateService.Save draft) {
        templates.save(business, code, draft);
        return ApiResponse.ok();
    }

    /**
     * 按请求中的草稿修订号发布指定模板。
     */
    @PostMapping("/{code}/publish")
    public ApiResponse<Void> publish(@PathVariable String business, @PathVariable String code,
                        @Valid @RequestBody TemplateService.Publish request) {
        templates.publish(business, code, request.revision());
        return ApiResponse.ok();
    }

    /** 查询指定流程模板的发布历史。 */
    @GetMapping("/{code}/versions")
    public ApiResponse<List<TemplateService.VersionSummary>> versions(@PathVariable String business, @PathVariable String code) {
        return ApiResponse.ok(templates.versions(business, code));
    }

    /** 只读查看指定发布版本的配置。 */
    @GetMapping("/{code}/versions/{version}")
    public ApiResponse<TemplateService.Release> version(@PathVariable String business, @PathVariable String code,
                                           @PathVariable int version) {
        return ApiResponse.ok(templates.version(business, code, version));
    }

    /** 将历史版本复制为待发布草稿。 */
    @PostMapping("/{code}/versions/{version}/restore")
    public ApiResponse<Void> restore(@PathVariable String business, @PathVariable String code, @PathVariable int version,
                        @Valid @RequestBody TemplateService.Publish request) {
        templates.restore(business, code, version, request.revision());
        return ApiResponse.ok();
    }

    /** 启用或停用模板的新申请入口。 */
    @PutMapping("/{code}/availability")
    public ApiResponse<Void> availability(@PathVariable String business, @PathVariable String code,
                             @Valid @RequestBody TemplateService.Availability request) {
        templates.setAvailability(business, code, request);
        return ApiResponse.ok();
    }

    /**
     * 列出当前用户在指定业务下可发起的模板版本。
     */
    @GetMapping("/published")
    public ApiResponse<List<TemplateService.Release>> published(@PathVariable String business) {
        return ApiResponse.ok(templates.published(business));
    }
}
