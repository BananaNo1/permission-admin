package com.leis.workflow.execution;

import com.leis.common.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 提供使用已发布流程模板发起申请的入口。
 */
@RestController
@RequestMapping("/api/workflow/businesses/{business}/templates/{code}/applications")
public class TemplateExecutionController {
    private final TemplateExecutionService execution;
    /**
     * 注入按发布版本启动流程的服务。
     */
    public TemplateExecutionController(TemplateExecutionService execution) { this.execution = execution; }

    /**
     * 按业务和模板编码发起已发布的流程。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateExecutionService.Started> start(@PathVariable String business, @PathVariable String code,
                                                 @Valid @RequestBody TemplateExecutionService.Start request) {
        return ApiResponse.created(execution.start(business, code, request));
    }
}
