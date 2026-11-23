package com.leis.permission.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leis.common.common.BusinessException;
import com.leis.common.common.PageResult;
import com.leis.permission.auth.CurrentActor;
import com.leis.permission.auth.PasswordPolicy;
import com.leis.permission.auth.PermissionChecker;
import com.leis.permission.dept.mapper.DeptMapper;
import com.leis.permission.log.service.AuditService;
import com.leis.permission.support.VersionGuard;
import com.leis.permission.user.dto.UserCreateRequest;
import com.leis.permission.user.dto.UserStatusRequest;
import com.leis.permission.user.dto.UserUpdateRequest;
import com.leis.permission.user.entity.User;
import com.leis.permission.user.mapper.UserMapper;
import com.leis.permission.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final UserAccessService access;
    private final UserScopeService scope;
    private final CurrentActor current;
    private final PermissionChecker permissions;
    private final PasswordEncoder encoder;
    private final AuditService audit;


    /**
     * 检查列表权限，按数据范围和转义后的关键词执行用户分页查询。
     */
    public PageResult<UserVO> list(String keyword, int page, int pageSize) {
        permissions.require("system:user:list");
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数无效");
        }
        var query = scope.scoped(current.get());
        if (keyword != null && !keyword.isBlank()) {
            String escaped = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.and(filter -> filter.like("username", escaped).or().like("nickname", escaped));
        }
        query.orderByDesc("id");
        Page<User> result = userMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResult<>(result.getRecords().stream().map(UserVO::from).toList(), result.getTotal(), page,
                pageSize);
    }

    /**
     * 检查详情权限，在当前数据范围内按 ID 查找用户。
     */
    public UserVO detail(Long id) {
        permissions.require("system:user:query");
        User row = userMapper.selectOne(scope.scoped(current.get()).eq("id", id));
        if (row == null) throw new BusinessException(404, "用户不存在或不可访问");
        return UserVO.from(row);
    }

    /**
     * 检查新增权限和目标部门，在事务内写入启用账号及编码后的初始密码并记录审计。
     */
    @Transactional
    public UserVO create(UserCreateRequest request) {
        permissions.require("system:user:create");
        deptMapper.lockTree();
        access.targetDepartment(request.deptId(), null);
        PasswordPolicy.validate(request.password());
        User row = new User();
        row.setUsername(request.username());
        row.setNickname(request.nickname());
        row.setDeptId(request.deptId());
        row.setPasswordHash(encoder.encode(request.password()));
        row.setStatus("ENABLED");
        row.setProtectedAccount(false);
        row.setAuthVersion(0L);
        row.setVersion(0);
        userMapper.insert(row);
        audit.record("USER_CREATE", row.getId());
        return UserVO.from(row);
    }


    /**
     * 检查操作权限和目标用户的数据范围，校验目标部门后更新姓名与归属。
     */
    @Transactional
    public UserVO update(Long id, UserUpdateRequest request) {
        permissions.require("system:user:update");
        User row = access.writable(id, request.version());
        access.targetDepartment(request.deptId(), row);
        row.setNickname(request.nickname());
        row.setDeptId(request.deptId());
        VersionGuard.updated(userMapper.updateById(row));
        audit.record("USER_UPDATE", id);
        return UserVO.from(row);
    }

    /**
     * 校验账号保护规则和目标部门，状态变化时提高鉴权版本以撤销旧会话。
     */
    @Transactional
    public void changeStatus(Long id, UserStatusRequest request) {
        permissions.require("system:user:update");
        User row = access.writable(id, request.version());
        if ("DISABLED".equals(request.status())
                && (row.getProtectedAccount() || Objects.equals(id, current.get().id()))) {
            throw new BusinessException(400, "该账号必须保持启用");
        }
        if ("ENABLED".equals(request.status())) {
            access.targetDepartment(row.getDeptId(), row);
        }
        if (!row.getStatus().equals(request.status())) {
            row.setAuthVersion(row.getAuthVersion() + 1);
        }
        row.setStatus(request.status());
        VersionGuard.updated(userMapper.updateById(row));
        audit.record("USER_STATUS", id);
    }

    /**
     * 在可写范围内按版本删除用户，保护初始管理员和当前登录账号。
     */
    @Transactional
    public void delete(Long id, Integer version) {
        permissions.require("system:user:delete");
        User row = access.writable(id, version);
        if (row.getProtectedAccount() || Objects.equals(id, current.get().id()))
            throw new BusinessException(400, "该账号不能删除");
        VersionGuard.updated(userMapper.delete(new QueryWrapper<User>()
                .eq("id", id)
                .eq("version", version)));
        audit.record("USER_DELETE", id);
    }
}
