package com.leis.permission.auth;

import com.leis.common.common.ApiResponse;
import com.leis.permission.auth.vo.CsrfVO;
import com.leis.permission.auth.vo.MeVO;
import com.leis.permission.menu.service.MenuQueryService;
import com.leis.permission.menu.vo.MenuVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提供当前用户信息、菜单、CSRF 令牌和个人密码修改接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CurrentActor current;
    private final AccountQueryService accounts;
    private final MenuQueryService menuQuery;
    private final PasswordChangeService passwords;


    /**
     * 提供 CSRF 令牌及请求头名称，供前端提交写请求时使用。
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfVO> csrf(CsrfToken token) {
        return ApiResponse.ok(new CsrfVO(token.getToken(), token.getHeaderName()));
    }

    /**
     * 返回当前请求的账号信息和可用权限标识。
     */
    @GetMapping("/me")
    public ApiResponse<MeVO> me() {
        var actor = current.get();
        return ApiResponse.ok(new MeVO(actor.id().toString(), actor.username(), actor.deptId().toString(), actor.admin(), actor.permissions()));
    }

    /**
     * 根据用户的有效角色返回已授权且祖先链有效的菜单。
     */
    @GetMapping("/menus")
    public ApiResponse<List<MenuVO>> menus() {
        var actor = current.get();
        var ids = accounts.effectiveRoles(actor.id()).stream().map(role -> role.getId()).toList();
        return ApiResponse.ok(menuQuery.grantedMenus(ids, actor.admin()).stream().map(MenuVO::from).toList());
    }


    /**
     * 修改当前用户的密码，成功后立即销毁本次请求的会话。
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request, HttpServletRequest servletRequest) {
        passwords.change(request);
        servletRequest.getSession().invalidate();
        return ApiResponse.ok();
    }

}
