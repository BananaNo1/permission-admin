package com.leis.workflow;

import com.leis.workflow.template.TemplateCompiler;
import com.leis.workflow.template.TemplateCompiler.Step;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static com.leis.workflow.ApprovalService.Mode.SINGLE;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TemplateCompilerTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.datasource.url=jdbc:h2:mem:compiler-test", "flowable.database-schema-update=true"})
class TemplateCompilerTest {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestApplication {}

    @Autowired RepositoryService repository;
    @Autowired RuntimeService runtime;
    @Autowired TaskService tasks;

    @ParameterizedTest
    @CsvSource({"99.99,review0", "100.00,review1"})
    void returnsToLastExecutedStep(String amount, String previous) {
        var steps = List.of(new Step("初审", SINGLE, List.of(2L), null, "APPLICANT"),
                new Step("财务", SINGLE, List.of(3L), new BigDecimal("100.00"), "PREVIOUS"),
                new Step("终审", SINGLE, List.of(4L), null, "PREVIOUS"));
        String key = "conditionalReview";
        repository.createDeployment().addBpmnModel(key + ".bpmn20.xml",
                TemplateCompiler.compile(key, "采购", steps)).deploy();
        var instance = runtime.startProcessInstanceByKey(key, Map.of("applicantId", "1",
                "decision", "", "amount", new BigDecimal(amount),
                "stepUsers0", new ArrayList<>(List.of("2")), "stepUsers1", new ArrayList<>(List.of("3")),
                "stepUsers2", new ArrayList<>(List.of("4"))));
        var query = tasks.createTaskQuery().processInstanceId(instance.getId());
        var task = query.singleResult();
        assertThat(task.getName()).isEqualTo("初审");
        tasks.complete(task.getId(), Map.of("decision", "APPROVE"));
        if (previous.equals("review1")) {
            assertThat(query.singleResult().getName()).isEqualTo("财务");
            tasks.complete(query.singleResult().getId(), Map.of("decision", "APPROVE"));
        }
        assertThat(query.singleResult().getName()).isEqualTo("终审");
        tasks.complete(query.singleResult().getId(), Map.of("decision", "RETURN"));
        assertThat(query.singleResult().getTaskDefinitionKey()).isEqualTo(previous);
        assertThat(runtime.getVariable(instance.getId(), "decision")).isEqualTo("");
        tasks.complete(query.singleResult().getId(), Map.of("decision", "REJECT"));
        assertThat(query.count()).isZero();
    }
}

