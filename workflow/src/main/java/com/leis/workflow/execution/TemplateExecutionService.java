package com.leis.workflow.execution;

import com.leis.common.common.BusinessException;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.auth.CurrentActor;
import com.leis.workflow.template.TemplateService;
import jakarta.validation.constraints.*;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.RuntimeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class TemplateExecutionService {
    public record Start(@NotBlank @Size(max=120) String businessKey, @NotBlank @Size(max=120) String title,
                        @Size(max=1000) String description, @NotNull @PositiveOrZero @Digits(integer=16,fraction=2) BigDecimal amount) {}
    public record Started(String instanceId, String templateCode, int version, String businessKey) {}

    private final TemplateService templates;
    private final JdbcTemplate db;
    private final CurrentActor actor;
    private final AccountQueryService accounts;
    private final RuntimeService engine;

    public TemplateExecutionService(TemplateService templates, JdbcTemplate db, CurrentActor actor,
                                    AccountQueryService accounts, RuntimeService engine) {
        this.templates = templates;
        this.db = db;
        this.actor = actor;
        this.accounts = accounts;
        this.engine = engine;
    }

    @Transactional
    public Started start(String business, String code, Start request) {
        var release = templates.release(business,code);
        long applicant = actor.get().id();
        var variables = new HashMap<String,Object>();
        var reviewers = new LinkedHashSet<String>();
        for (int i=0; i<release.steps().size(); i++) {
            var step = release.steps().get(i);
            var ids = step.approverIds().stream().map(String::valueOf).toList();
            variables.put("stepUsers"+i, new ArrayList<>(ids));
            if (step.minAmount()!=null && request.amount().compareTo(step.minAmount())<0) continue;
            for (Long id : step.approverIds()) {
                if (!reviewers.add(id.toString())) continue;
                var user = accounts.findById(id);
                if (id == applicant || user == null || !"ENABLED".equals(user.getStatus()))
                    throw new BusinessException(400,"审批节点包含申请人或未启用的账号");
            }
        }
        variables.put("approverIds",new ArrayList<>(reviewers));
        variables.put("applicantId",Long.toString(applicant));
        variables.put("title",request.title().strip());
        variables.put("description",request.description()==null ? "" : request.description().strip());
        variables.put("decision","");
        variables.put("amount",request.amount());
        variables.put("templateCode",code);
        variables.put("businessCode",business);
        variables.put("templateVersion",release.version());
        var context = Authentication.getAuthenticationContext();
        var previous = context.getPrincipal();
        String businessKey = request.businessKey().strip();
        try {
            Authentication.setAuthenticatedUserId(Long.toString(applicant));
            var instance = engine.startProcessInstanceById(release.definitionId(),businessKey,variables);
            db.update("insert into wf_template_instance(business,code,business_key,version,instance_id) values(?,?,?,?,?)",
                    business,code,businessKey,release.version(),instance.getId());
            return new Started(instance.getId(),code,release.version(),businessKey);
        } finally {
            context.setPrincipal(previous);
        }
    }
}
