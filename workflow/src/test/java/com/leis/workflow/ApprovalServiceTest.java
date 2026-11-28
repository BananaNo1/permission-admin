package com.leis.workflow;

import com.leis.common.common.BusinessException;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.auth.ActorSnapshot;
import com.leis.permission.auth.CurrentActor;
import com.leis.permission.dept.service.DeptQueryService;
import com.leis.permission.user.entity.User;
import com.leis.workflow.access.BusinessAccess;
import com.leis.workflow.execution.TemplateExecutionService;
import com.leis.workflow.execution.mapper.InstanceOperationMapper;
import com.leis.workflow.template.TemplateCompiler.Step;
import com.leis.workflow.template.TemplateService;
import com.leis.workflow.template.mapper.TemplateMapper;
import com.leis.workflow.template.mapper.ReviewerMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.test.context.jdbc.Sql;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static com.leis.workflow.ApprovalService.Action.*;
import static com.leis.workflow.ApprovalService.Mode.*;

@SpringBootTest(classes = ApprovalServiceTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = { "spring.datasource.url=jdbc:h2:mem:approval-test", "flowable.database-schema-update=true" })
@Sql(statements = "set mode PostgreSQL", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = { "create table wf_business(code varchar(64) primary key)", "insert into wf_business values('purchase')" },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = { "/db/migration/V5__workflow_templates.sql", "/db/migration/V6__workflow_diagram_layout.sql",
        "/db/migration/V8__workflow_template_availability.sql", "/db/migration/V10__workflow_instance_operations.sql" },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ApprovalServiceTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = { InstanceOperationMapper.class, TemplateMapper.class })
    @Import({ ApprovalService.class, ApprovalEngineConfiguration.class, TemplateService.class, TemplateExecutionService.class })
    static class TestApplication {
    }

    @Autowired
    private ApprovalService approvals;

    @Autowired
    private TemplateService templates;
    @Autowired
    private TemplateExecutionService execution;

    @MockitoBean
    private CurrentActor actor;
    @MockitoBean
    private BusinessAccess businessAccess;
    @MockitoBean
    private DeptQueryService departments;

    @MockitoBean
    private AccountQueryService accounts;
    @MockitoBean
    private ReviewerMapper reviewerMapper;

    @BeforeEach
    void setUp() {
        var approver = new User();
        approver.setStatus("ENABLED");
        when(accounts.findById(2L)).thenReturn(approver);
        when(accounts.findById(3L)).thenReturn(approver);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void recordsDecisionAndRestrictsAccess(boolean approved) {
        var instance = start(1L, "采购申请", "办公用品", SINGLE);
        assertThat(instance.status()).isEqualTo("PENDING");
        assertThat(approvals.application(1L, instance.id())).isEqualTo(instance);
        assertThatThrownBy(() -> approvals.application(3L, instance.id()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        assertThat(approvals.applications(3L, 1, 20).records()).isEmpty();
        assertThat(approvals.tasks(3L, 1, 20).records()).isEmpty();
        var task = approvals.tasks(2L, 1, 20).records().getFirst();
        assertThat(task.title()).isEqualTo("采购申请");
        var decision = new ApprovalService.DecisionRequest(approved ? APPROVE : REJECT, "已审核");
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
        assertThatThrownBy(() -> start(2L, "申请", null, SINGLE))
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
        assertThat(approvals.applications(1L, 1, 20).records())
                .filteredOn(item -> item.id().equals(instance.id())).singleElement()
                .satisfies(item -> assertThat(item.currentNodeNames()).containsExactly("采购审批"));
        var oldTask = pending(3L, instance.id());
        assertThat(oldTask.returnAllowed()).isTrue();
        vote(2L, instance.id(), RETURN);
        assertThat(approvals.application(1L, instance.id()).status()).isEqualTo("RETURNED");
        assertThat(approvals.tasks(3L, 1, 20).records()).noneMatch(t -> t.instanceId().equals(instance.id()));
        assertThatThrownBy(() -> approvals.decide(3L, oldTask.id(), new ApprovalService.DecisionRequest(APPROVE, null)))
                .isInstanceOf(BusinessException.class);

        assertThat(approvals.application(1L, instance.id()).currentNodeNames()).containsExactly("修改申请");
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
        assertThat(approvals.application(1L, instance.id()).currentNodeNames()).isEmpty();
        assertThat(approvals.actions(1L, instance.id(), 1, 20).records())
                .extracting(ApprovalService.RecordedAction::action)
                .containsExactlyInAnyOrder("RETURN", "RESUBMIT", "APPROVE", "APPROVE");
    }

    @Test
    void inboxShowsCurrentAndCompletedWorkOnlyToActualParticipants() {
        var instance = start(1L, "打印机采购", "财务审批", ANY);
        when(accounts.displayNames(java.util.Set.of(1L, 2L, 3L))).thenReturn(java.util.Map.of("2", "张三", "3", "李四"));
        var filter = new ApprovalService.InboxFilter("打印机", null, null);
        var waiting = approvals.tasks(2L, 1, 20, filter).records();
        assertThat(waiting).singleElement().satisfies(task -> assertThat(task.instanceId()).isEqualTo(instance.id()));
        assertThat(approvals.detail(1L, instance.id(), 1, 20).active())
                .extracting(ApprovalService.ActiveWork::actorName).containsExactlyInAnyOrder("张三", "李四");
        var detail = approvals.detail(2L, instance.id(), 1, 20);
        assertThat(detail.ownTasks()).extracting(ApprovalService.PendingTask::id).containsExactly(waiting.getFirst().id());
        assertThatThrownBy(() -> approvals.detail(4L, instance.id(), 1, 20))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(404));

        approvals.decide(2L, waiting.getFirst().id(), new ApprovalService.DecisionRequest(APPROVE, "确认采购"));
        assertThat(approvals.completed(2L, 1, 20, filter).records())
                .extracting(ApprovalService.CompletedTask::instanceId).containsExactly(instance.id());
        assertThat(approvals.completed(3L, 1, 20, filter).records()).isEmpty();
        assertThat(approvals.tasks(2L, 1, 20, filter).records()).isEmpty();
        assertThatThrownBy(() -> approvals.detail(3L, instance.id(), 1, 20))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        var completed = approvals.detail(2L, instance.id(), 1, 20);
        assertThat(completed.active()).isEmpty();
        assertThat(completed.application().applicantId()).isEqualTo("1");
        assertThat(completed.application().amount()).isEqualTo("0");
        assertThat(completed.actions().records()).extracting(ApprovalService.RecordedAction::nodeName)
                .containsExactly("采购审批");
        assertThat(completed.actions().records()).extracting(ApprovalService.RecordedAction::comment).containsExactly("确认采购");
        var approved = new ApprovalService.InboxFilter("打印机", null, "APPROVED");
        assertThat(approvals.applications(1L, 1, 20, approved).records())
                .extracting(ApprovalService.Application::id).containsExactly(instance.id());
        var pending = new ApprovalService.InboxFilter("打印机", null, "PENDING");
        assertThat(approvals.applications(1L, 1, 20, pending).records()).isEmpty();
    }

    @Test
    void transfersToTheApplicantAndPreservesOperationHistory() {
        var applicant = new User();
        applicant.setStatus("ENABLED");
        when(accounts.findById(11L)).thenReturn(applicant);
        var instance = start(11L, "转办模拟", "", SINGLE);
        var task = pending(2L, instance.id());
        assertThatThrownBy(() -> approvals.transfer(3L, task.id(), new ApprovalService.TransferRequest(11L, "交接")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(404));

        approvals.transfer(2L, task.id(), new ApprovalService.TransferRequest(11L, "请申请人接手"));
        assertThat(approvals.tasks(2L, 1, 20).records()).noneMatch(item -> item.instanceId().equals(instance.id()));
        assertThat(pending(11L, instance.id()).id()).isEqualTo(task.id());
        assertThat(approvals.detail(2L, instance.id(), 1, 20).operations()).singleElement().satisfies(action -> {
            assertThat(action.operation()).isEqualTo("TRANSFER");
            assertThat(action.actorId()).isEqualTo("2");
            assertThat(action.targetId()).isEqualTo("11");
        });
        vote(11L, instance.id(), APPROVE);
        assertThat(approvals.application(11L, instance.id()).status()).isEqualTo("APPROVED");
    }

    @Test
    void withdrawsBeforeApprovalOrWhileWaitingForResubmission() {
        var before = start(19L, "撤回模拟", null, SINGLE);
        var request = new ApprovalService.OperationRequest("申请信息需要调整");
        assertThat(approvals.detail(19L, before.id(), 1, 20).canWithdraw()).isTrue();
        approvals.withdraw(19L, before.id(), request);
        assertThat(approvals.application(19L, before.id()).status()).isEqualTo("CANCELLED");
        assertThat(approvals.detail(19L, before.id(), 1, 20).operations())
                .extracting(ApprovalService.InstanceOperation::operation).containsExactly("WITHDRAW");

        var parallel = start(19L, "撤回会签模拟", null, ALL);
        vote(2L, parallel.id(), APPROVE);
        assertThatThrownBy(() -> approvals.withdraw(19L, parallel.id(), request))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        vote(3L, parallel.id(), RETURN);
        assertThat(pending(19L, parallel.id()).type()).isEqualTo("RESUBMIT");
        approvals.withdraw(19L, parallel.id(), request);
        assertThat(approvals.detail(19L, parallel.id(), 1, 20).active()).isEmpty();
    }

    @Test
    void limitsOperationsMonitoringAndTerminationToAdministrators() {
        var admin = new ActorSnapshot(20L, "admin", null, true, Set.of(), true, Set.of(), true);
        var normal = new ActorSnapshot(21L, "user", null, false, Set.of(), false, Set.of(), true);
        var instance = start(22L, "管理员终止模拟", null, SINGLE);
        var filter = new ApprovalService.InboxFilter("管理员终止模拟", null, "ACTIVE");
        var request = new ApprovalService.OperationRequest("重复申请已确认");
        assertThatThrownBy(() -> approvals.monitor(normal, 1, 20, filter))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        assertThatThrownBy(() -> approvals.terminate(normal, instance.id(), request))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        assertThat(approvals.monitor(admin, 1, 20, filter).records()).extracting(ApprovalService.Application::id)
                .containsExactly(instance.id());
        assertThat(approvals.detail(admin.id(), true, instance.id(), 1, 20).canTerminate()).isTrue();
        approvals.terminate(admin, instance.id(), request);
        assertThat(approvals.detail(22L, instance.id(), 1, 20).operations())
                .extracting(ApprovalService.InstanceOperation::reason).containsExactly("重复申请已确认");
        assertThat(approvals.monitor(admin, 1, 20, filter).records()).isEmpty();
    }

    @Test
    void concurrentApprovalAndWithdrawalCannotBothSucceed() throws Exception {
        var instance = start(31L, "并发撤回模拟", null, SINGLE);
        var task = pending(2L, instance.id());
        var go = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var approval = executor.submit(() -> compete(go,
                    () -> approvals.decide(2L, task.id(), new ApprovalService.DecisionRequest(APPROVE, "审批"))));
            var withdrawal = executor.submit(() -> compete(go,
                    () -> approvals.withdraw(31L, instance.id(), new ApprovalService.OperationRequest("撤回"))));
            go.countDown();
            assertThat(List.of(approval.get(15, TimeUnit.SECONDS), withdrawal.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(approvals.application(31L, instance.id()).status()).isIn("APPROVED", "CANCELLED");
    }

    private static boolean compete(CountDownLatch condition, Runnable action) throws InterruptedException {
        condition.await();
        try {
            action.run();
            return true;
        } catch (BusinessException error) {
            assertThat(error.getStatus()).isIn(404, 409);
            return false;
        }
    }

    private ApprovalService.Application start(ApprovalService.Mode mode) {
        return start(1L, "采购申请", "办公用品", mode);
    }

    private ApprovalService.Application start(long applicantId, String title, String description, ApprovalService.Mode mode) {
        when(actor.get()).thenReturn(new ActorSnapshot(applicantId, "applicant", null, false, Set.of(), false, Set.of(), true));
        String template = "review_" + UUID.randomUUID().toString().replace("-", "");
        List<Long> reviewers = mode == SINGLE ? List.of(2L) : List.of(2L, 3L);
        var step = new Step("采购审批", mode, reviewers, null, "APPLICANT");
        templates.save("purchase", template, new TemplateService.Save(title, 0, List.of(step)));
        templates.publish("purchase", template, 1);
        var instance = execution.start("purchase", template,
                new TemplateExecutionService.Start(template, title, description, BigDecimal.ZERO));
        return approvals.application(applicantId, instance.instanceId());
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
