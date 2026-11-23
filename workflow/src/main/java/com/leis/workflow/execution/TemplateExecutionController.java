package com.leis.workflow.execution;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow/businesses/{business}/templates/{code}/applications")
public class TemplateExecutionController {
    private final TemplateExecutionService execution;
    public TemplateExecutionController(TemplateExecutionService execution) { this.execution = execution; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateExecutionService.Started start(@PathVariable String business, @PathVariable String code,
                                                 @Valid @RequestBody TemplateExecutionService.Start request) {
        return execution.start(business,code,request);
    }
}
