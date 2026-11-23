package com.leis.permission.log.service;

import com.leis.permission.auth.CurrentActor;
import com.leis.permission.log.dto.AuditCommand;
import com.leis.permission.log.mapper.AuditMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final AuditMapper mapper;
    private final CurrentActor current;

    /**
     * 注入审计存储和当前请求身份组件。
     */
    public AuditService(AuditMapper mapper, CurrentActor current) {
        this.mapper = mapper;
        this.current = current;
    }

    /**
     * 在业务事务内写入成功审计，使审计随业务变更一起提交或回滚。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void success(AuditCommand auditCommand){
        mapper.operation(auditCommand);
    }

    /**
     * 使用独立事务保存失败审计，避免业务回滚丢失失败记录。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(AuditCommand command) {
        mapper.operation(command);
    }

    /**
     * 使用独立事务记录登录结果、来源 IP 和请求追踪 ID。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void login(String username, Long userId, boolean success, String ip, String traceId) {
        mapper.login(username, userId, success, ip, traceId);
    }

    /**
     * 从当前请求补齐操作者和追踪 ID，在业务事务内记录一次成功操作。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, Long targetId) {
        var actor = current.get();
        var request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();
        mapper.operation(new AuditCommand(actor.id(), actor.username(), action, "SYSTEM",
                targetId.toString(), true, "操作成功", (String) request.getAttribute("traceId")));
    }

}
