package com.leis.workflow.template;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/workflow/businesses/{business}/templates")
public class TemplateController {
    private final TemplateService templates;
    private final ReviewerDirectory reviewers;

    public TemplateController(TemplateService templates, ReviewerDirectory reviewers) {
        this.templates = templates;
        this.reviewers = reviewers;
    }

    @GetMapping
    public List<TemplateService.Draft> drafts(@PathVariable String business) {
        return templates.drafts(business);
    }

    @GetMapping("/reviewers")
    public List<ReviewerDirectory.Reviewer> reviewers(@PathVariable String business,
                                                       @RequestParam(defaultValue = "") String keyword,
                                                       @RequestParam(required = false) List<Long> ids) {
        return reviewers.find(business, keyword, ids);
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
