package com.leis.workflow;

import com.leis.common.common.BusinessException;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static com.leis.workflow.ApprovalService.Action.*;
import static com.leis.workflow.ApprovalService.Mode.*;

@SpringBootTest(classes = ApprovalServiceTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.datasource.url=jdbc:h2:mem:approval-test", "flowable.database-schema-update=true"})
class ApprovalServiceTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({ApprovalService.class, ApprovalEngineConfiguration.class})
    static class TestApplication {
    }

    @Autowired
    private ApprovalService approvals;

    @MockitoBean
    private AccountQueryService accounts;

    @BeforeEach
    void setUp() {
        var approver = new User();
        approver.setStatus("ENABLED");
        when(accounts.findById(2L)).thenReturn(approver);
        when(accounts.findById(3L)).thenReturn(approver);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recordsDecisionAndRestrictsAccess(boolean approved) {
        var instance = approvals.start(1L, new ApprovalService.StartRequest("采购申请", 2L, "办公用品"));
        assertThat(instance.status()).isEqualTo("PENDING");
        assertThat(approvals.application(1L, instance.id())).isEqualTo(instance);
        assertThatThrownBy(() -> approvals.application(3L, instance.id()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        assertThat(approvals.applications(3L, 1, 20).records()).isEmpty();
        assertThat(approvals.tasks(3L, 1, 20).records()).isEmpty();
        var task = approvals.tasks(2L, 1, 20).records().getFirst();
        assertThat(task.title()).isEqualTo("采购申请");
        var decision = new ApprovalService.DecisionRequest(approved, "已审核");
        assertThatThrownBy(() -> approvals.decide(3L, task.id(), decision))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(404));

        approvals.decide(2L, task.id(), decision);

        assertThat(approvals.tasks(2L, 1, 20).records()).isEmpty();
        assertThat(approvals.applications(1L, 1, 20).records())
                .filteredOn(application -> application.id().equals(instance.id())).singleElement()
                .satisfies(application -> {
                    assertThat(application.status()).isEqualTo(approved ? "APPROVED" : "REJECTED");
                    assertThat(application.comment()).isEqualTo("已审核");
                    assertThat(application.endTime()).isNotNull();
                });
        assertThatThrownBy(() -> approvals.decide(2L, task.id(), decision)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> approvals.start(2L, new ApprovalService.StartRequest("申请", 2L, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void parallelCountersignWaitsForEveryoneAndRejectEndsEarly() {
        var instance = start(ALL);
        vote(2L, instance.id(), APPROVE);
        assertThat(approvals.application(1L, instance.id()).status()).isEqualTo("PENDING");
        vote(3L, instance.id(), APPROVE);
        assertThat(approvals.application(1L, instance.id()).status()).isEqualTo("APPROVED");

        var rejected = start(ALL);
        vote(2L, rejected.id(), REJECT);
        assertThat(approvals.application(1L, rejected.id()).status()).isEqualTo("REJECTED");
        assertThat(approvals.tasks(3L, 1, 20).records()).noneMatch(t -> t.instanceId().equals(rejected.id()));
    }

    @Test
    void anyApproverCanApproveAndAllMustReject() {
        var instance = start(ANY);
        vote(2L, instance.id(), REJECT);
        assertThat(approvals.application(1L, instance.id()).status()).isEqualTo("PENDING");
        vote(3L, instance.id(), APPROVE);
        assertThat(approvals.application(1L, instance.id()).status()).isEqualTo("APPROVED");

        var rejected = start(ANY);
        vote(2L, rejected.id(), REJECT);
        vote(3L, rejected.id(), REJECT);
        assertThat(approvals.application(1L, rejected.id()).status()).isEqualTo("REJECTED");

        var early = start(ANY);
        vote(2L, early.id(), APPROVE);
        assertThat(approvals.tasks(3L, 1, 20).records()).noneMatch(t -> t.instanceId().equals(early.id()));
    }

    @Test
    void sequentialReviewFollowsProvidedOrder() {
        var instance = start(SEQUENTIAL);
        assertThat(approvals.tasks(3L, 1, 20).records()).noneMatch(t -> t.instanceId().equals(instance.id()));
        vote(2L, instance.id(), APPROVE);
        vote(3L, instance.id(), APPROVE);
        assertThat(approvals.application(1L, instance.id()).status()).isEqualTo("APPROVED");
    }

    @Test
    void returnCancelsOtherTasksAndResubmissionRestartsReview() {
        var instance = start(ALL);
        var oldTask = pending(3L, instance.id());
        vote(2L, instance.id(), RETURN);
        assertThat(approvals.application(1L, instance.id()).status()).isEqualTo("RETURNED");
        assertThat(approvals.tasks(3L, 1, 20).records()).noneMatch(t -> t.instanceId().equals(instance.id()));
        assertThatThrownBy(() -> approvals.decide(3L, oldTask.id(), new ApprovalService.DecisionRequest(APPROVE, null)))
                .isInstanceOf(BusinessException.class);

        var revision = pending(1L, instance.id());
        assertThat(revision.type()).isEqualTo("RESUBMIT");
        var resubmit = new ApprovalService.ResubmitRequest("采购申请（修订）", "新材料");
        assertThatThrownBy(() -> approvals.resubmit(2L, revision.id(), resubmit)).isInstanceOf(BusinessException.class);
        approvals.resubmit(1L, revision.id(), resubmit);
        assertThat(pending(3L, instance.id()).id()).isNotEqualTo(oldTask.id());
        assertThat(pending(2L, instance.id()).title()).isEqualTo("采购申请（修订）");
        vote(2L, instance.id(), APPROVE);
        vote(3L, instance.id(), APPROVE);
        assertThat(approvals.application(1L, instance.id()).status()).isEqualTo("APPROVED");
        assertThat(approvals.actions(1L, instance.id(), 1, 20).records())
                .extracting(ApprovalService.RecordedAction::action)
                .containsExactlyInAnyOrder("RETURN", "RESUBMIT", "APPROVE", "APPROVE");
    }

    private ApprovalService.Application start(ApprovalService.Mode mode) {
        return approvals.start(1L, new ApprovalService.StartRequest("采购申请", null, "办公用品",
                mode, List.of(2L, 3L)));
    }

    private ApprovalService.PendingTask pending(Long assignee, String instanceId) {
        return approvals.tasks(assignee, 1, 20).records().stream()
                .filter(t -> t.instanceId().equals(instanceId)).findFirst().orElseThrow();
    }

    private void vote(Long assignee, String instanceId, ApprovalService.Action action) {
        approvals.decide(assignee, pending(assignee, instanceId).id(),
                new ApprovalService.DecisionRequest(action, "审核意见"));
    }
}
