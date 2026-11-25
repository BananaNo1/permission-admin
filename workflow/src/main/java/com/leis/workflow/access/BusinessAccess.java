package com.leis.workflow.access;

import com.leis.common.common.BusinessException;
import com.leis.permission.auth.CurrentActor;
import com.leis.permission.log.dto.AuditCommand;
import com.leis.permission.log.service.AuditService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class BusinessAccess {
    public record Business(String code, String name, boolean edit, boolean publish, boolean start) {}
    public record Create(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,64}") String code,
                         @NotBlank @Size(max=128) String name) {}
    public record Grant(long userId, boolean edit, boolean publish, boolean start) {}
    public record Rights(boolean edit, boolean publish, boolean start) {}
    private final JdbcTemplate db;
    private final CurrentActor actor;

    private final AuditService audit;

    public BusinessAccess(JdbcTemplate db, CurrentActor actor, AuditService audit) {
        this.db = db;
        this.actor = actor;
        this.audit = audit;
    }

    public enum Capability { VIEW_DRAFT, EDIT, PUBLISH, START }

    /**
     * 校验业务操作权限。保留业务行锁，使写操作与授权变更按顺序执行。
     */
    @Transactional
    public void require(String business, Capability capability) {
        var user = actor.get();
        if (db.query("select code from wf_business where code=? for update",
                (rs, i) -> rs.getString(1), business).isEmpty()) {
            throw new BusinessException(403, "无此业务权限");
        }
        if (user.admin()) return;

        var grants = db.query("""
                select can_edit, can_publish, can_start
                from wf_business_grant where business_code=? and user_id=?
                """, (rs, i) -> new Rights(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3)),
                business, user.id());
        boolean allowed = !grants.isEmpty() && switch (capability) {
            case VIEW_DRAFT -> grants.getFirst().edit() || grants.getFirst().publish();
            case EDIT -> grants.getFirst().edit();
            case PUBLISH -> grants.getFirst().publish();
            case START -> grants.getFirst().start();
        };
        if (!allowed) throw new BusinessException(403, "无此业务权限");
    }

    public List<Business> list() {
        var user = actor.get();
        return db.query("""
            select b.code,b.name,? or g.can_edit,? or g.can_publish,? or g.can_start
            from wf_business b left join wf_business_grant g on g.business_code=b.code and g.user_id=?
            where ? or g.user_id is not null order by b.code
            """, (rs,i) -> new Business(rs.getString(1),rs.getString(2),rs.getBoolean(3),
                rs.getBoolean(4),rs.getBoolean(5)), user.admin(),user.admin(),user.admin(),user.id(),user.admin());
    }

    private void admin() {
        if (!actor.get().admin()) throw new BusinessException(403, "需要超级管理员权限");
    }

    private void checkBusiness(String code) {
        if (!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from wf_business where code=?)",
                Boolean.class,code))) throw new BusinessException(404,"业务不存在");
    }

    @Transactional
    public void create(Create request) {
        admin();
        try {
            db.update("insert into wf_business(code,name) values (?,?)",
                request.code(), request.name().strip());
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new BusinessException(409,"业务编码已存在");
        }
        record("WORKFLOW_BUSINESS_CREATE", request.code(), "创建业务");
    }

    private void record(String action, String code, String summary) {
        var user = actor.get();
        var request = RequestContextHolder.getRequestAttributes();
        var traceId = request == null ? null : (String) request.getAttribute("traceId", RequestAttributes.SCOPE_REQUEST);
        audit.success(new AuditCommand(user.id(), user.username(), action, "WORKFLOW_BUSINESS",
                code, true, summary, traceId));
    }

    public List<Grant> grants(String code) {
        admin();
        checkBusiness(code);
        return db.query("select user_id,can_edit,can_publish,can_start from wf_business_grant where business_code=? order by user_id",
            (rs,i) -> new Grant(rs.getLong(1),rs.getBoolean(2),rs.getBoolean(3),rs.getBoolean(4)),code);
    }

    private void lockBusiness(String code) {
        if (db.query("select code from wf_business where code=? for update", (rs,i) -> rs.getString(1),code).isEmpty())
            throw new BusinessException(404,"业务不存在");
    }

    @Transactional
    public void grant(String code, long userId, Rights rights) {
        admin();
        lockBusiness(code);
        if (!rights.edit() && !rights.publish() && !rights.start()) {
            db.update("delete from wf_business_grant where business_code=? and user_id=?",code,userId);
            record("WORKFLOW_BUSINESS_REVOKE", code, rightsSummary(userId,rights));
            return;
        }
        if (db.update("update wf_business_grant set can_edit=?,can_publish=?,can_start=? where business_code=? and user_id=?",
                rights.edit(),rights.publish(),rights.start(),code,userId) == 0 &&
                db.update("""
            insert into wf_business_grant(business_code,user_id,can_edit,can_publish,can_start)
            select ?,id,?,?,? from sys_user where id=?
            """,code,rights.edit(),rights.publish(),rights.start(),userId) == 0)
            throw new BusinessException(404,"用户不存在");
        record("WORKFLOW_BUSINESS_GRANT", code, rightsSummary(userId,rights));
    }

    private String rightsSummary(long userId, Rights rights) {
        return "userId=%d edit=%b publish=%b start=%b".formatted(userId,rights.edit(),rights.publish(),rights.start());
    }
}
