package com.leis.workflow;

import com.leis.common.common.PageResult;
import com.leis.permission.auth.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow")
public class ApprovalController {
    private final ApprovalService approvals;
    private final CurrentActor currentActor;

    public ApprovalController(ApprovalService approvals, CurrentActor currentActor) {
        this.approvals = approvals;
        this.currentActor = currentActor;
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalService.Application start(@Valid @RequestBody ApprovalService.StartRequest request) {
        return approvals.start(currentActor.get().id(), request);
    }

    @GetMapping("/applications")
    public PageResult<ApprovalService.Application> applications(@RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int pageSize) {
        return approvals.applications(currentActor.get().id(), page, pageSize);
    }

    @GetMapping("/applications/{id}")
    public ApprovalService.Application application(@PathVariable String id) {
        return approvals.application(currentActor.get().id(), id);
    }

    @GetMapping("/applications/{id}/actions")
    public PageResult<ApprovalService.RecordedAction> actions(@PathVariable String id,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int pageSize) {
        return approvals.actions(currentActor.get().id(), id, page, pageSize);
    }

    @GetMapping("/tasks")
    public PageResult<ApprovalService.PendingTask> tasks(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int pageSize) {
        return approvals.tasks(currentActor.get().id(), page, pageSize);
    }

    @PostMapping("/tasks/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(@PathVariable String id, @Valid @RequestBody ApprovalService.DecisionRequest request) {
        approvals.decide(currentActor.get().id(), id, request);
    }

    @PostMapping("/tasks/{id}/resubmit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resubmit(@PathVariable String id, @Valid @RequestBody ApprovalService.ResubmitRequest request) {
        approvals.resubmit(currentActor.get().id(), id, request);
    }
}
