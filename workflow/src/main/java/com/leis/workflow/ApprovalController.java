package com.leis.workflow;

import com.leis.common.common.ApiResponse;
import com.leis.common.common.PageResult;
import com.leis.permission.auth.CurrentActor;
import com.leis.workflow.template.ReviewerDirectory;
import jakarta.validation.Valid;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供申请查询、待办办理、重新提交和流程运维接口。
 */
@RestController
@RequestMapping("/api/workflow")
public class ApprovalController {
    private final ApprovalService approvals;
    private final CurrentActor currentActor;

    /**
     * 注入审批服务和当前请求身份解析组件。
     */
    public ApprovalController(ApprovalService approvals, CurrentActor currentActor) {
        this.approvals = approvals;
        this.currentActor = currentActor;
    }

    /**
     * 分页返回当前用户发起的审批申请。
     */
    @GetMapping("/applications")
    public ApiResponse<PageResult<ApprovalService.Application>> applications(@RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int pageSize,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) String business,
                                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(approvals.applications(currentActor.get().id(), page, pageSize, new ApprovalService.InboxFilter(keyword, business, status)));
    }

    /**
     * 查询当前用户发起的单个审批申请。
     */
    @GetMapping("/applications/{id}")
    public ApiResponse<ApprovalService.Application> application(@PathVariable String id) {
        return ApiResponse.ok(approvals.application(currentActor.get().id(), id));
    }

    /**
     * 分页返回当前用户所发申请的已记录办理动作。
     */
    @GetMapping("/applications/{id}/actions")
    public ApiResponse<PageResult<ApprovalService.RecordedAction>> actions(@PathVariable String id,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(approvals.actions(currentActor.get().id(), id, page, pageSize));
    }

    /**
     * 分页返回分配给当前登录用户的待办任务。
     */
    @GetMapping("/tasks")
    public ApiResponse<PageResult<ApprovalService.PendingTask>> tasks(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String business) {
        return ApiResponse.ok(approvals.tasks(currentActor.get().id(), page, pageSize, new ApprovalService.InboxFilter(keyword, business, null)));
    }

    /**
     * 分页返回由当前用户实际办理过的任务。
     */
    @GetMapping("/tasks/completed")
    public ApiResponse<PageResult<ApprovalService.CompletedTask>> completed(@RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String business) {
        return ApiResponse.ok(approvals.completed(currentActor.get().id(), page, pageSize, new ApprovalService.InboxFilter(keyword, business, null)));
    }

    /**
     * 返回当前用户可访问的审批实例详情与办理历史。
     */
    @GetMapping("/instances/{id}")
    public ApiResponse<ApprovalService.InstanceDetail> detail(@PathVariable String id,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        var actor = currentActor.get();
        return ApiResponse.ok(approvals.detail(actor.id(), actor.admin(), id, page, pageSize));
    }

    /** 分页查询在途审批实例或有引擎作业异常的在途实例。 */
    @GetMapping("/operations/instances")
    public ApiResponse<PageResult<ApprovalService.Application>> monitor(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int pageSize,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String business,
                                                          @RequestParam(required = false) String status) {
        return ApiResponse.ok(approvals.monitor(currentActor.get(), page, pageSize, new ApprovalService.InboxFilter(keyword, business, status)));
    }

    /** 搜索当前任务可选择的转办账号。 */
    @GetMapping("/tasks/{id}/transfer-candidates")
    public ApiResponse<List<ReviewerDirectory.Reviewer>> transferCandidates(@PathVariable String id,
                                                                 @RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(approvals.transferCandidates(currentActor.get().id(), id, keyword));
    }

    /** 将当前名下的审批任务转办给另一个启用账号。 */
    @PostMapping("/tasks/{id}/transfer")
    public ApiResponse<Void> transfer(@PathVariable String id, @Valid @RequestBody ApprovalService.TransferRequest request) {
        approvals.transfer(currentActor.get().id(), id, request);
        return ApiResponse.ok();
    }

    /** 申请人按撤回规则结束自己的在途申请。 */
    @PostMapping("/instances/{id}/withdraw")
    public ApiResponse<Void> withdraw(@PathVariable String id, @Valid @RequestBody ApprovalService.OperationRequest request) {
        approvals.withdraw(currentActor.get().id(), id, request);
        return ApiResponse.ok();
    }

    /** 超级管理员终止在途审批实例。 */
    @PostMapping("/instances/{id}/terminate")
    public ApiResponse<Void> terminate(@PathVariable String id, @Valid @RequestBody ApprovalService.OperationRequest request) {
        approvals.terminate(currentActor.get(), id, request);
        return ApiResponse.ok();
    }

    /**
     * 以当前登录用户身份办理指定审批任务。
     */
    @PostMapping("/tasks/{id}/decision")
    public ApiResponse<Void> decide(@PathVariable String id, @Valid @RequestBody ApprovalService.DecisionRequest request) {
        approvals.decide(currentActor.get().id(), id, request);
        return ApiResponse.ok();
    }

    /**
     * 以当前登录用户身份重新提交退回待办。
     */
    @PostMapping("/tasks/{id}/resubmit")
    public ApiResponse<Void> resubmit(@PathVariable String id, @Valid @RequestBody ApprovalService.ResubmitRequest request) {
        approvals.resubmit(currentActor.get().id(), id, request);
        return ApiResponse.ok();
    }
}
