package com.leis.permission.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.leis.permission.auth.ActorSnapshot;
import com.leis.permission.user.entity.User;
import org.springframework.stereotype.Service;

@Service
public class UserScopeService {

    /**
     * 合并可见部门与本人范围，空授权范围使用无匹配条件。
     */
    public QueryWrapper<User> scoped(ActorSnapshot actor) {
        var query = new QueryWrapper<User>();
        if (actor.all()) return query;
        if (actor.deptIds().isEmpty() && !actor.includeSelf()) return query.eq("id", -1L);
        return query.and(scope -> {
                    if (!actor.deptIds().isEmpty()) {
                        scope.in("dept_id", actor.deptIds());
                    }
                    if (actor.includeSelf()) {
                        if (!actor.deptIds().isEmpty()) {
                            scope.or();
                        }
                        scope.eq("id", actor.id());
                    }
                }
        );
    }
}
