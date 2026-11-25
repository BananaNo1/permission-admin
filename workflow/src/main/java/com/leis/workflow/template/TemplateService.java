package com.leis.workflow.template;

import com.leis.common.common.BusinessException;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.auth.CurrentActor;
import com.leis.workflow.access.BusinessAccess;
import jakarta.validation.constraints.*;
import org.flowable.engine.RepositoryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class TemplateService {
    public record Draft(String code, String name, int revision, int publishedVersion, boolean dirty,
                        List<TemplateCompiler.Step> steps, DiagramLayout diagramLayout) {}
    public record Save(@NotBlank @Size(max=100) String name, @PositiveOrZero int revision,
                       @NotEmpty @Size(max=30) List<TemplateCompiler.Step> steps, DiagramLayout diagramLayout) {
        public Save(String name, int revision, List<TemplateCompiler.Step> steps) {
            this(name, revision, steps, null);
        }
    }
    public record Publish(@Positive int revision) {}
    public record Release(String code, String name, int version, String definitionId,
                          List<TemplateCompiler.Step> steps, DiagramLayout diagramLayout) {}
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RepositoryService repository;
    private final BusinessAccess access;
    private final CurrentActor actor;
    private final AccountQueryService accounts;

    public TemplateService(JdbcTemplate db, ObjectMapper json, RepositoryService repository, BusinessAccess access,
                           CurrentActor actor, AccountQueryService accounts) {
        this.db = db;
        this.json = json;
        this.repository = repository;
        this.access = access;
        this.actor = actor;
        this.accounts = accounts;
    }

    private List<TemplateCompiler.Step> steps(String raw) {
        return Arrays.asList(json.readValue(raw, TemplateCompiler.Step[].class));
    }

    private DiagramLayout layout(String raw) {
        return raw == null ? null : json.readValue(raw, DiagramLayout.class);
    }

    public List<Draft> drafts(String business) {
        access.require(business, BusinessAccess.Capability.VIEW_DRAFT);
        return db.query("select * from wf_template where business=? order by code", (rs,i) ->
                new Draft(rs.getString("code"), rs.getString("name"), rs.getInt("revision"),
                        rs.getInt("published_version"), rs.getInt("revision") != rs.getInt("published_revision"),
                        steps(rs.getString("steps")), layout(rs.getString("diagram_layout"))), business);
    }

    @Transactional
    public void save(String business, String code, Save request) {
        access.require(business, BusinessAccess.Capability.EDIT);
        if (!code.matches("[a-zA-Z][a-zA-Z0-9_-]{0,47}")) throw new BusinessException(400,"模板编码格式无效");
        String key = "wfTpl" + UUID.randomUUID().toString().replace("-", "");
        TemplateCompiler.compile(key, request.name(), request.steps());
        String data = json.writeValueAsString(request.steps());
        String diagram = null;
        if (request.diagramLayout() != null) {
            request.diagramLayout().validate(request.steps().size());
            diagram = json.writeValueAsString(request.diagramLayout());
        }
        long editor = actor.get().id();
        if (request.revision() == 0) {
            db.update("insert into wf_template(business,code,engine_key,name,steps,diagram_layout,revision,edited_by) values(?,?,?,?,?,?,1,?)",
                    business,code,key,request.name(),data,diagram,editor);
        } else if (db.update("""
                update wf_template set name=?, steps=?, diagram_layout=?, revision=revision+1, edited_by=?, edited_at=current_timestamp
                where business=? and code=? and revision=?
                """,request.name(),data,diagram,editor,business,code,request.revision()) != 1) {
            throw new BusinessException(409,"模板已修改，请刷新");
        }
    }

    @Transactional
    public void publish(String business, String code, int revision) {
        access.require(business, BusinessAccess.Capability.PUBLISH);
        var drafts = db.queryForList("select * from wf_template where business=? and code=? for update", business, code);
        if (drafts.isEmpty()) throw new BusinessException(404,"模板不存在");
        var draft = drafts.getFirst();
        if (((Number) draft.get("revision")).intValue() != revision) throw new BusinessException(409,"模板已修改，请刷新");
        if (((Number) draft.get("published_revision")).intValue() == revision) throw new BusinessException(409,"当前草稿已经发布");
        String key = (String) draft.get("engine_key");
        String name = (String) draft.get("name");
        String data = (String) draft.get("steps");
        var nodes = steps(data);
        for (Long id : nodes.stream().flatMap(s -> s.approverIds().stream()).distinct().toList()) {
            var user = accounts.findById(id);
            if (user == null || !"ENABLED".equals(user.getStatus())) throw new BusinessException(400,"模板包含无效审批人");
        }
        var deployment = repository.createDeployment().name(name)
                .addBpmnModel(key + ".bpmn20.xml", TemplateCompiler.compile(key,name,nodes)).deploy();
        var definition = repository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
        int version = ((Number)draft.get("published_version")).intValue() + 1;
        db.update("insert into wf_template_release(business,code,version,name,steps,diagram_layout,definition_id,published_by) values(?,?,?,?,?,?,?,?)",
                business,code,version,name,data,draft.get("diagram_layout"),definition.getId(),actor.get().id());
        db.update("update wf_template set published_version=?, published_revision=revision+1, revision=revision+1 where business=? and code=?",version,business,code);
    }

    public Release release(String business, String code) {
        access.require(business, BusinessAccess.Capability.START);
        var rows = db.query("""
                select r.* from wf_template t join wf_template_release r on t.business=r.business and t.code=r.code
                    and t.published_version=r.version where t.business=? and t.code=?
                """,(rs,i) -> new Release(rs.getString("code"),rs.getString("name"),rs.getInt("version"),
                rs.getString("definition_id"),steps(rs.getString("steps")),layout(rs.getString("diagram_layout"))),business,code);
        if (rows.isEmpty()) throw new BusinessException(404,"已发布的模板不存在");
        return rows.getFirst();
    }

    public List<Release> published(String business) {
        access.require(business, BusinessAccess.Capability.START);
        return db.query("""
                select r.* from wf_template t join wf_template_release r on t.business=r.business and t.code=r.code
                    and t.published_version=r.version where t.business=? order by r.code
                """,(rs,i) -> new Release(rs.getString("code"),rs.getString("name"),rs.getInt("version"),
                rs.getString("definition_id"),steps(rs.getString("steps")),layout(rs.getString("diagram_layout"))),business);
    }
}


