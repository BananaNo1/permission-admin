package com.leis.permission.auth;

import com.leis.common.common.BusinessException;
import com.leis.permission.log.service.AuditService;
import com.leis.permission.support.VersionGuard;
import com.leis.permission.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final UserMapper userMapper;
    private final CurrentActor current;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    /**
     * 锁定当前账号并验证原密码，保存新密码后提升鉴权版本，使旧会话在下次校验时失效。
     */
    @Transactional
    public void change(PasswordChangeRequest request) {
        var user = userMapper.lock(current.get().id());
        if (user == null) {
            throw new BusinessException(401, "账号已失效");
        }
        if (!encoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(400,
                    "原密码错误");
        }

        PasswordPolicy.validate(request.newPassword());
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setAuthVersion(user.getAuthVersion() + 1);
        VersionGuard.updated(userMapper.updateById(user));
        audit.record("PASSWORD_CHANGE", user.getId());
    }
}
