package com.leis.permission;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

public final class SessionUser implements UserDetails, CredentialsContainer {
    @Serial
    private static final long serialVersionUID = 0L;

    private final Long id;
    private final String username;
    private final Long authVersion;
    private transient @Nullable String passwordHash;

    /**
     * 创建认证主体，并保留用于登录校验的临时密码摘要及会话版本。
     */
    public SessionUser(Long id, String username, String passwordHash, Long authVersion) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.authVersion = authVersion;
    }

    /**
     * 返回会话绑定的用户主键。
     */
    public Long id() {
        return id;
    }

    /**
     * 返回业务层使用的登录账号。
     */
    public String username() {
        return username;
    }

    /**
     * 返回登录时的鉴权版本，供后续请求判断会话是否仍有效。
     */
    public Long authVersion() {
        return authVersion;
    }

    /**
     * 返回空的框架权限集合，业务权限由请求级鉴权快照提供。
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    /**
     * 取得尚未清除的密码摘要，供登录认证使用。
     */
    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    /**
     * 向 Spring Security 提供登录账号。
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 认证结束后清除内存中的临时密码摘要。
     */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
