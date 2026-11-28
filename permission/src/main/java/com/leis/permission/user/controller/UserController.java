package com.leis.permission.user.controller;

import com.leis.common.common.ApiResponse;

import com.leis.common.common.PageResult;
import com.leis.permission.user.dto.*;
import com.leis.permission.user.service.UserPasswordService;
import com.leis.permission.user.service.UserRoleService;
import com.leis.permission.user.service.UserService;
import com.leis.permission.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提供用户查询、维护、密码重置和角色分配接口。
 */
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService users;
    private final UserRoleService roles;
    private final UserPasswordService passwords;

    /**
     * 接收账号或姓名关键词，分页返回当前用户可访问的账号。
     */
    @GetMapping
    public ApiResponse<PageResult<UserVO>> list(@RequestParam(defaultValue = "") String keyword,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(users.list(keyword, page, pageSize));
    }

    /**
     * 读取数据范围内的单个用户详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<UserVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(users.detail(id));
    }

    /**
     * 接收新增用户请求并返回创建后的用户信息。
     */
    @PostMapping
    public ApiResponse<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(users.create(request));
    }

    /**
     * 修改用户姓名与所属部门。
     */
    @PutMapping("/{id}")
    public ApiResponse<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(users.update(id, request));
    }

    /**
     * 按请求版本启用或禁用用户账号。
     */
    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        users.changeStatus(id, request);
        return ApiResponse.ok();
    }

    /**
     * 返回指定用户当前绑定的角色 ID。
     */
    @GetMapping("/{id}/roles")
    public ApiResponse<List<String>> roleIds(@PathVariable Long id) {
        return ApiResponse.ok(roles.roleIds(id));
    }

    /**
     * 用提交的完整角色集合更新用户角色分配。
     */
    @PutMapping("/{id}/roles")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody UserRoleAssignRequest request) {
        roles.assign(id, request);
        return ApiResponse.ok();
    }

    /**
     * 为指定用户设置新密码。
     */
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody UserPasswordResetRequest request) {
        passwords.reset(id, request);
        return ApiResponse.ok();
    }

    /**
     * 按用户 ID 和版本删除账号。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        users.delete(id, version);
        return ApiResponse.ok();
    }

}
