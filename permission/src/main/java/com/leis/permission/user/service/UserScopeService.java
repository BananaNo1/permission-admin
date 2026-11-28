package com.leis.permission.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leis.permission.auth.ActorSnapshot;
import com.leis.permission.user.entity.User;
import org.springframework.stereotype.Service;

/**
 * 为用户查询构造基于组织范围与本人权限的数据过滤条件。
 */
@Service
public class UserScopeService {

    /**
     * 合并可见部门与本人范围，空授权范围使用无匹配条件。
     */
    public LambdaQueryWrapper<User> scoped(ActorSnapshot actor) {
        var query = new LambdaQueryWrapper<User>();
        if (actor.all()) {
            return query;
        }
        if (actor.deptIds().isEmpty() && !actor.includeSelf()) {
            return query.eq(User::getId, -1L);
        }
        return query.and(scope -> {
                    if (!actor.deptIds().isEmpty()) {
                        scope.in(User::getDeptId, actor.deptIds());
                    }
                    if (actor.includeSelf()) {
                        if (!actor.deptIds().isEmpty()) {
                            scope.or();
                        }
                        scope.eq(User::getId, actor.id());
                    }
                }
        );
    }
}
