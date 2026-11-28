package com.leis.permission.auth;

import com.leis.common.common.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 提供当前请求中已经解析的登录身份，供服务层执行权限检查。
 */
@Component
public class CurrentActor {
    /**
     * 从当前请求取得鉴权快照，无法取得时返回 401。
     */
    public ActorSnapshot get() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null || !(attributes.getRequest().getAttribute("actor") instanceof ActorSnapshot actor)) {
            throw new BusinessException(401, "请先登录");
        }
        return actor;
    }
}
