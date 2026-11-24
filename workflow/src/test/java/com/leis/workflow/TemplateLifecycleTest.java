package com.leis.workflow;

import com.leis.common.common.BusinessException;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.auth.ActorSnapshot;
import com.leis.permission.auth.CurrentActor;
import com.leis.permission.user.entity.User;
import com.leis.workflow.access.BusinessAccess;
import com.leis.workflow.execution.TemplateExecutionService;
import com.leis.workflow.template.DiagramLayout;
import com.leis.workflow.template.TemplateCompiler.Position;
import com.leis.workflow.template.TemplateCompiler.Step;
import com.leis.workflow.template.TemplateService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TemplateLifecycleTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.datasource.url=jdbc:h2:mem:template-lifecycle", "flowable.database-schema-update=true"})
class TemplateLifecycleTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({ApprovalEngineConfiguration.class, TemplateService.class, TemplateExecutionService.class, ApprovalService.class})
    static class Config {}

    @Autowired TemplateService templates;
    @Autowired TemplateExecutionService execution;
    @Autowired ApprovalService approvals;
    @MockitoBean CurrentActor actor;
    @MockitoBean BusinessAccess access;
    @MockitoBean AccountQueryService accounts;

    @Test
    @Sql(statements = {"create table wf_business(code varchar(64) primary key)", "insert into wf_business values('purchase')"})
    @Sql({"/db/migration/V5__workflow_templates.sql", "/db/migration/V6__workflow_diagram_layout.sql"})
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
        assertThat(approvals.applications(1L,1,20).total()).isEqualTo(1);
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
        approvals.decide(2L, originalTask.id(), new ApprovalService.DecisionRequest(ApprovalService.Action.APPROVE, "ok"));
        assertThat(approvals.application(1L, original.instanceId()).status()).isEqualTo("APPROVED");
        assertThat(approvals.actions(1L, original.instanceId(), 1, 20).records()).singleElement()
                .satisfies(action -> {
                    assertThat(action.action()).isEqualTo("APPROVE");
                    assertThat(action.actorId()).isEqualTo("2");
                });
        assertThat(approvals.application(1L, updated.instanceId()).status()).isEqualTo("PENDING");
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
        var step = new Step("审批", ApprovalService.Mode.SINGLE, List.of(reviewer), null, "APPLICANT", position);
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
