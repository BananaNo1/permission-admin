package com.leis.permission.user.service;


import com.leis.permission.auth.PasswordPolicy;
import com.leis.permission.auth.PermissionChecker;
import com.leis.permission.log.service.AuditService;
import com.leis.permission.support.VersionGuard;
import com.leis.permission.user.dto.UserPasswordResetRequest;
import com.leis.permission.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理管理员重置密码并推进会话授权版本。
 */
@Service
@RequiredArgsConstructor
public class UserPasswordService {

    private final UserMapper userMapper;
    private final UserAccessService access;
    private final PermissionChecker permissions;
    private final PasswordEncoder encoder;
    private final AuditService audit;


    /**
     * 检查重置权限和目标账号范围，保存新密码并提高鉴权版本以撤销旧会话。
     */
    @Transactional
    public void reset(Long id, UserPasswordResetRequest request) {
        permissions.require("system:user:reset-password");
        var user = access.writable(id, request.version());
        PasswordPolicy.validate(request.newPassword());
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setAuthVersion(user.getAuthVersion() + 1);
        VersionGuard.updated(userMapper.updateById(user));
        audit.record("USER_PASSWORD_RESET", id);
    }

}
