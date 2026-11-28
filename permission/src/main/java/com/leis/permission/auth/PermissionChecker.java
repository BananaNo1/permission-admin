package com.leis.permission.auth;


import com.leis.common.common.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 检查当前操作者是否具备指定操作权限或超级管理员权限。
 */
@Component("permissionChecker")
public class PermissionChecker {

    private final CurrentActor currentActor;

    /**
     * 注入用于读取当前请求鉴权快照的组件。
     */
    public PermissionChecker(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    /**
     * 判断当前用户是否拥有超级管理员身份。
     */
    public boolean isAdmin() {
        return this.currentActor.get().admin();
    }

    /**
     * 判断当前用户是否拥有指定权限，超级管理员直接通过。
     */
    public boolean has(String permission) {
        ActorSnapshot actor = this.currentActor.get();
        return actor.admin() || actor.permissions().contains(permission);
    }

    /**
     * 强制要求指定操作权限，校验失败时返回 403。
     */
    public void require(String permission) {
        if (!has(permission)) {
            throw  new BusinessException(403, "没有操作权限");
        }
    }

    /**
     * 强制要求超级管理员身份，校验失败时返回 403。
     */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw  new BusinessException(403, "需要超级管理员权限");
        }
    }
}
