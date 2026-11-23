package com.leis.permission.log.service;

import com.leis.common.common.BusinessException;
import com.leis.common.common.PageResult;
import com.leis.permission.auth.PermissionChecker;
import com.leis.permission.log.mapper.AuditMapper;
import com.leis.permission.log.vo.LoginLogVO;
import com.leis.permission.log.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogQueryService {

    private final AuditMapper mapper;
    private final PermissionChecker permissions;

    /**
     * 限制审计查询仅向管理员开放，并校验页码和单页条数。
     */
    private void validate(int page, int pageSize) {
        permissions.requireAdmin();
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数无效");
        }
    }

    /**
     * 查询当前页登录记录及总数，构造分页响应。
     */
    public PageResult<LoginLogVO> loginLogs(int page, int pageSize) {
        validate(page, pageSize);
        return new PageResult<>(mapper.logins(pageSize, (long) (page - 1) * pageSize), mapper.loginCount(), page,
                pageSize);
    }

    /**
     * 查询当前页操作记录及总数，构造分页响应。
     */
    public PageResult<OperationLogVO> operationLogs(int page, int pageSize) {
        validate(page, pageSize);
        return new PageResult<>(mapper.operations(pageSize, (long) (page - 1) * pageSize), mapper.operationCount(),
                page, pageSize);
    }
}
