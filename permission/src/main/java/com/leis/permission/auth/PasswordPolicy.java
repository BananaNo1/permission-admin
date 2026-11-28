package com.leis.permission.auth;

import com.leis.common.common.BusinessException;

import java.nio.charset.StandardCharsets;

/**
 * 集中定义账号密码的输入约束，供初始化和密码变更使用。
 */
public final class PasswordPolicy {

    /**
     * 禁止实例化密码规则工具类。
     */
    private PasswordPolicy() {}

    /**
     * 校验密码字符数，并限制 UTF-8 字节数以满足密码编码器输入上限。
     */
    public static void validate(String password) {
        if (password == null || password.length() < 6 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(400, "密码需为6～64字符，UTF-8长度最多72字节");
        }
    }
}
