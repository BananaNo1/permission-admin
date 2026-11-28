package com.leis.workflow;

import com.leis.common.common.BusinessException;
import com.leis.common.common.GlobalExceptionHandler;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.auth.ActorSnapshot;
import com.leis.permission.auth.CurrentActor;
import com.leis.permission.dept.service.DeptQueryService;
import com.leis.workflow.template.mapper.ReviewerMapper;
import com.leis.permission.user.entity.User;
import com.leis.workflow.access.BusinessAccess;
import com.leis.workflow.execution.TemplateExecutionService;
import com.leis.workflow.execution.mapper.TemplateInstanceMapper;
import com.leis.workflow.template.DiagramLayout;
import com.leis.workflow.template.RoutingGraph;
import com.leis.workflow.template.TemplateCompiler;
import com.leis.workflow.template.TemplateCompiler.Position;
import com.leis.workflow.template.TemplateCompiler.Step;
import com.leis.workflow.template.TemplateController;
import com.leis.workflow.template.TemplateService;
import com.leis.workflow.template.mapper.TemplateMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TemplateLifecycleTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = { "spring.datasource.url=jdbc:h2:mem:template-lifecycle", "flowable.database-schema-update=true" })
@Sql(statements = { "create table wf_business(code varchar(64) primary key)",
        "insert into wf_business values('purchase'), ('default_options'), ('dynamic_purchase'), ('gateway_purchase')" },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = "set mode PostgreSQL", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = { "/db/migration/V5__workflow_templates.sql", "/db/migration/V6__workflow_diagram_layout.sql",
        "/db/migration/V8__workflow_template_availability.sql", "/db/migration/V10__workflow_instance_operations.sql" },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class TemplateLifecycleTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = { TemplateMapper.class, TemplateInstanceMapper.class })
    @Import({ ApprovalEngineConfiguration.class, TemplateService.class, TemplateExecutionService.class, ApprovalService.class })
    static class Config {}

    @Autowired TemplateService templates;
    @Autowired JsonMapper json;
    @Autowired TemplateExecutionService execution;
    @Autowired ApprovalService approvals;
    @MockitoBean CurrentActor actor;
    @MockitoBean BusinessAccess access;
    @MockitoBean AccountQueryService accounts;
    @MockitoBean DeptQueryService departments;
    @MockitoBean ReviewerMapper reviewers;

    @Test
    void runningApplicationsUseTheirPublishedDefinition() {
        when(actor.get()).thenReturn(new ActorSnapshot(1L, "applicant", null, false, Set.of(), false, Set.of(), true));
        var enabled = new User();
        enabled.setStatus("ENABLED");
        when(accounts.findById(2L)).thenReturn(enabled);
        when(accounts.findById(3L)).thenReturn(enabled);
        save(0, 2L);
        assertThat(templates.drafts("purchase").getFirst().diagramLayout()).isNull();
        int revision = revision();
        templates.publish("purchase", "order", revision);
        assertConflict(revision);
        assertConflict(revision());
        var original = start("first");
        assertThatThrownBy(() -> start("first")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(approvals.applications(1L, 1, 20).total()).isEqualTo(1);
        save(revision(), 3L);
        assertThat(templates.drafts("purchase").getFirst().steps().getFirst().position())
                .isEqualTo(new Position(320, 160));
        assertThat(templates.drafts("purchase").getFirst().diagramLayout()).isEqualTo(diagramLayout());
        assertThat(templates.release("purchase", "order").diagramLayout()).isNull();
        assertConflict(revision);
        templates.publish("purchase", "order", revision());
        assertThat(templates.release("purchase", "order").diagramLayout()).isEqualTo(diagramLayout());
        var updated = start("second");
        assertThat(original.version()).isEqualTo(1);
        assertThat(updated.version()).isEqualTo(2);
        assertThat(approvals.tasks(3L, 1, 20).records()).singleElement()
                .satisfies(t -> assertThat(t.instanceId()).isEqualTo(updated.instanceId()));
        var originalTask = approvals.tasks(2L, 1, 20).records().getFirst();
        assertThat(originalTask.instanceId()).isEqualTo(original.instanceId());
        assertThat(originalTask.formKey()).isEqualTo(ApprovalService.FORM_REQUIRED_OPINION);
        assertThat(originalTask.nodeEnteredAt()).isNotBlank();
        assertThatThrownBy(() -> approvals.decide(2L, originalTask.id(),
                new ApprovalService.DecisionRequest(ApprovalService.Action.APPROVE, " ")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        approvals.decide(2L, originalTask.id(), new ApprovalService.DecisionRequest(ApprovalService.Action.APPROVE, "ok"));
        assertThat(approvals.application(1L, original.instanceId()).status()).isEqualTo("APPROVED");
        assertThat(approvals.actions(1L, original.instanceId(), 1, 20).records()).singleElement()
                .satisfies(action -> {
                    assertThat(action.action()).isEqualTo("APPROVE");
                    assertThat(action.actorId()).isEqualTo("2");
                    assertThat(action.listenerCompletedAt()).isNotBlank();
                });
        assertThat(approvals.application(1L, updated.instanceId()).status()).isEqualTo("PENDING");
        assertThat(templates.versions("purchase", "order")).extracting(TemplateService.VersionSummary::version)
                .containsExactly(2, 1);
        assertThat(templates.version("purchase", "order", 1).steps().getFirst().approverIds()).containsExactly(2L);
        int beforeRestore = revision();
        templates.restore("purchase", "order", 1, beforeRestore);
        assertThat(templates.drafts("purchase").getFirst().dirty()).isTrue();
        assertThat(templates.drafts("purchase").getFirst().steps().getFirst().approverIds()).containsExactly(2L);
        assertThat(templates.release("purchase", "order").version()).isEqualTo(2);
        int dirtyRevision = revision();
        assertThatThrownBy(() -> templates.restore("purchase", "order", 2, dirtyRevision))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        templates.setAvailability("purchase", "order", new TemplateService.Availability(dirtyRevision, false));
        assertThat(templates.published("purchase")).isEmpty();
        assertThat(templates.drafts("purchase").getFirst().dirty()).isTrue();
        assertThatThrownBy(() -> start("stopped"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        assertThat(approvals.tasks(3L, 1, 20).records()).extracting(ApprovalService.PendingTask::instanceId)
                .contains(updated.instanceId());
        templates.setAvailability("purchase", "order", new TemplateService.Availability(revision(), true));
        assertThat(start("resumed").version()).isEqualTo(2);
    }

    @Test
    void simulatedPurchaseResolvesDepartmentAndAmountBasedRoleReviewers() {
        when(actor.get()).thenReturn(new ActorSnapshot(11L, "applicant", 66L, false, Set.of(), false, Set.of(), true));
        var manager = new User();
        manager.setStatus("ENABLED");
        manager.setDeptId(66L);
        when(accounts.findById(2L)).thenReturn(manager);
        when(departments.managerId(66L)).thenReturn(2L);
        when(reviewers.roleEnabled(9L)).thenReturn(true);
        when(reviewers.activeRoleMembers(9L, 11L)).thenReturn(List.of(3L, 4L));

        var departmentStep = new Step("部门审批", ApprovalService.Mode.SINGLE, List.of(), null, "APPLICANT",
                null, null, false, false, TemplateCompiler.ReviewerSource.DEPARTMENT_MANAGER, null);
        var financeStep = new Step("财务会签", ApprovalService.Mode.ALL, List.of(), new BigDecimal("10000.00"), "PREVIOUS",
                null, null, false, false, TemplateCompiler.ReviewerSource.ROLE, 9L);
        String business = "dynamic_purchase";
        String template = "approval_rules";
        templates.save(business, template, new TemplateService.Save("模拟采购", 0, List.of(departmentStep, financeStep)));
        templates.publish(business, template, templates.drafts(business).getFirst().revision());

        var low = execution.start(business, template,
                new TemplateExecutionService.Start("SIM-LOW", "低金额采购", "模拟采购单", new BigDecimal("9999.99")));
        var high = execution.start(business, template,
                new TemplateExecutionService.Start("SIM-HIGH", "高金额采购", "模拟采购单", new BigDecimal("10000.00")));
        var filter = new ApprovalService.InboxFilter("SIM-HIGH", business, null);
        var tasks = approvals.tasks(2L, 1, 20, filter).records();
        assertThat(tasks).extracting(ApprovalService.PendingTask::instanceId).containsExactly(high.instanceId());
        for (var instance : List.of(low, high)) {
            var task = approvals.tasks(2L, 1, 20).records().stream()
                    .filter(item -> item.instanceId().equals(instance.instanceId())).findFirst().orElseThrow();
            approvals.decide(2L, task.id(), new ApprovalService.DecisionRequest(ApprovalService.Action.APPROVE, "部门通过"));
        }
        assertThat(approvals.application(11L, low.instanceId()).status()).isEqualTo("APPROVED");
        assertThat(approvals.application(11L, high.instanceId()).status()).isEqualTo("PENDING");
        for (long reviewer : List.of(3L, 4L)) {
            var task = approvals.tasks(reviewer, 1, 20, filter).records().getFirst();
            assertThat(task.nodeName()).isEqualTo("财务会签");
            approvals.decide(reviewer, task.id(), new ApprovalService.DecisionRequest(ApprovalService.Action.APPROVE, "财务通过"));
        }
        assertThat(approvals.application(11L, high.instanceId()).status()).isEqualTo("APPROVED");

        when(departments.managerId(66L)).thenReturn(11L);
        assertThatThrownBy(() -> execution.start(business, template,
                new TemplateExecutionService.Start("SIM-SELF", "负责人自审", "", BigDecimal.TEN)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    void publishesExclusiveRoutesAndReturnsToTheActualPreviousStep() {
        when(actor.get()).thenReturn(new ActorSnapshot(12L, "applicant", null, false, Set.of(), false, Set.of(), true));
        var enabled = new User();
        enabled.setStatus("ENABLED");
        for (long reviewer : List.of(2L, 3L, 4L)) {
            when(accounts.findById(reviewer)).thenReturn(enabled);
        }
        var nodes = List.of(
                new Step("部门审批", ApprovalService.Mode.SINGLE, List.of(2L), null, "APPLICANT"),
                new Step("财务审批", ApprovalService.Mode.SINGLE, List.of(3L), null, "PREVIOUS"),
                new Step("最终审批", ApprovalService.Mode.SINGLE, List.of(4L), null, "PREVIOUS"));
        var gate = new RoutingGraph.Gateway("AmountGate", new Position(360, 140));
        var condition = new RoutingGraph.Link("High", gate.id(), "Finance", RoutingGraph.Operator.GTE, new BigDecimal("10000.00"));
        var fallback = new RoutingGraph.Link("Fallback", gate.id(), "Final", RoutingGraph.Operator.DEFAULT, null);
        var links = List.of(
                new RoutingGraph.Link("StartLink", RoutingGraph.START, "Department", RoutingGraph.Operator.NONE, null),
                new RoutingGraph.Link("GateLink", "Department", gate.id(), RoutingGraph.Operator.NONE, null),
                condition, fallback,
                new RoutingGraph.Link("MergeLink", "Finance", "Final", RoutingGraph.Operator.NONE, null),
                new RoutingGraph.Link("EndLink", "Final", RoutingGraph.END, RoutingGraph.Operator.NONE, null));
        var graph = new RoutingGraph(List.of("Department", "Finance", "Final"), List.of(gate), links);
        var geometry = java.util.Collections.nCopies(links.size(), List.of(new Position(50, 140), new Position(620, 140)));
        var layout = new DiagramLayout(new Position(40, 140), new Position(620, 140), geometry, graph);
        var badLinks = new java.util.ArrayList<>(links);
        badLinks.set(3, new RoutingGraph.Link("Fallback", gate.id(), "Final", RoutingGraph.Operator.LT, BigDecimal.ONE));
        var bad = new DiagramLayout(layout.start(), layout.end(), geometry, new RoutingGraph(graph.taskIds(), graph.gateways(), badLinks));
        assertThatThrownBy(() -> templates.save("gateway_purchase", "branch",
                new TemplateService.Save("采购分流", 0, nodes, bad)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).contains("默认连线"));

        templates.save("gateway_purchase", "branch", new TemplateService.Save("采购分流", 0, nodes, layout));
        var draft = templates.drafts("gateway_purchase").getFirst();
        assertThat(draft.diagramLayout()).isEqualTo(layout);
        templates.publish("gateway_purchase", "branch", draft.revision());
        assertThat(templates.release("gateway_purchase", "branch").diagramLayout().routing()).isEqualTo(graph);
        for (String amount : List.of("9999.99", "10000")) {
            boolean high = amount.equals("10000");
            var application = execution.start("gateway_purchase", "branch",
                    new TemplateExecutionService.Start("ROUTE-" + amount, "采购分流", "", new BigDecimal(amount)));
            voteOn(application.instanceId(), 2L, "部门审批", ApprovalService.Action.APPROVE);
            if (high) {
                voteOn(application.instanceId(), 3L, "财务审批", ApprovalService.Action.APPROVE);
            }
            voteOn(application.instanceId(), 4L, "最终审批", ApprovalService.Action.RETURN);
            voteOn(application.instanceId(), high ? 3L : 2L, high ? "财务审批" : "部门审批", ApprovalService.Action.APPROVE);
            voteOn(application.instanceId(), 4L, "最终审批", ApprovalService.Action.APPROVE);
            assertThat(approvals.application(12L, application.instanceId()).status()).isEqualTo("APPROVED");
        }
    }

    private void voteOn(String instanceId, long reviewer, String nodeName, ApprovalService.Action action) {
        var task = approvals.tasks(reviewer, 1, 100).records().stream()
                .filter(item -> item.instanceId().equals(instanceId)).findFirst().orElseThrow();
        assertThat(task.nodeName()).isEqualTo(nodeName);
        approvals.decide(reviewer, task.id(), new ApprovalService.DecisionRequest(action, "模拟采购办理"));
    }

    @Test
    void savesDraftWhenListenerFlagsAreOmitted() throws Exception {
        when(actor.get()).thenReturn(new ActorSnapshot(1L, "applicant", null, false, Set.of(), false, Set.of(), true));
        var mvc = MockMvcBuilders.standaloneSetup(new TemplateController(templates, null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .build();
        mvc.perform(put("/api/workflow/businesses/default_options/templates/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"名称","revision":0,"steps":[{"name":"1","mode":"SINGLE",
                                "approverIds":[1],"minAmount":null,"returnTo":"APPLICANT"}],"diagramLayout":null}
                                """))
                .andExpect(result -> assertThat(result.getResolvedException()).isNull())
                .andExpect(status().isOk());
        var saved = templates.drafts("default_options").getFirst().steps().getFirst();
        assertThat(saved.name()).isEqualTo("1");
        assertThat(saved.recordEntered()).isFalse();
        assertThat(saved.recordCompleted()).isFalse();
    }


    private void assertConflict(int revision) {
        assertThatThrownBy(() -> templates.publish("purchase", "order", revision))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    private int revision() {
        return templates.drafts("purchase").getFirst().revision();
    }

    private void save(int revision, long reviewer) {
        var position = revision == 0 ? null : new Position(320, 160);
        var step = new Step("审批", ApprovalService.Mode.SINGLE, List.of(reviewer), null, "APPLICANT", position,
                ApprovalService.FORM_REQUIRED_OPINION, true, true);
        if (revision == 0) {
            templates.save("purchase", "order", new TemplateService.Save("采购审批", revision, List.of(step)));
            return;
        }
        var invalid = new DiagramLayout(new Position(40, 155), new Position(540, 180), List.of());
        assertThatThrownBy(() -> templates.save("purchase", "order",
                new TemplateService.Save("采购审批", revision, List.of(step), invalid)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        templates.save("purchase", "order", new TemplateService.Save("采购审批", revision, List.of(step), diagramLayout()));
    }

    private DiagramLayout diagramLayout() {
        return new DiagramLayout(new Position(40, 155), new Position(540, 180), List.of(
                List.of(new Position(76, 173), new Position(120, 173), new Position(320, 197)),
                List.of(new Position(450, 197), new Position(540, 198))));
    }

    private TemplateExecutionService.Started start(String key) {
        return execution.start("purchase", "order", new TemplateExecutionService.Start(key, "采购", "", BigDecimal.TEN));
    }
}
