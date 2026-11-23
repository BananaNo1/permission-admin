package com.leis.workflow.template;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/workflow/businesses/{business}/templates")
public class TemplateController {
    private final TemplateService templates;
    public TemplateController(TemplateService templates) { this.templates = templates; }

    @GetMapping
    public List<TemplateService.Draft> drafts(@PathVariable String business) {
        return templates.drafts(business);
    }

    @PutMapping("/{code}")
    public void save(@PathVariable String business, @PathVariable String code,
                     @Valid @RequestBody TemplateService.Save draft) {
        templates.save(business, code, draft);
    }

    @PostMapping("/{code}/publish")
    public void publish(@PathVariable String business, @PathVariable String code,
                        @Valid @RequestBody TemplateService.Publish request) {
        templates.publish(business,code,request.revision());
    }

    @GetMapping("/published")
    public List<TemplateService.Release> published(@PathVariable String business) {
        return templates.published(business);
    }
}
