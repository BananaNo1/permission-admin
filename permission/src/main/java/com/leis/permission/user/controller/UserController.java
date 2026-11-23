package com.leis.permission.user.controller;


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
    public PageResult<UserVO> list(@RequestParam(defaultValue = "") String keyword,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        return users.list(keyword, page, pageSize);
    }

    /**
     * 读取数据范围内的单个用户详情。
     */
    @GetMapping("/{id}")
    public UserVO detail(@PathVariable Long id) {
        return users.detail(id);
    }

    /**
     * 接收新增用户请求并返回创建后的用户信息。
     */
    @PostMapping
    public UserVO create(@Valid @RequestBody UserCreateRequest request) {
        return users.create(request);
    }

    /**
     * 修改用户姓名与所属部门。
     */
    @PutMapping("/{id}")
    public UserVO update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return users.update(id, request);
    }

    /**
     * 按请求版本启用或禁用用户账号。
     */
    @PutMapping("/{id}/status")
    public void changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        users.changeStatus(id, request);
    }

    /**
     * 返回指定用户当前绑定的角色 ID。
     */
    @GetMapping("/{id}/roles")
    public List<String> roleIds(@PathVariable Long id) {
        return roles.roleIds(id);
    }

    /**
     * 用提交的完整角色集合更新用户角色分配。
     */
    @PutMapping("/{id}/roles")
    public void assignRoles(@PathVariable Long id, @Valid @RequestBody UserRoleAssignRequest request) {
        roles.assign(id, request);
    }

    /**
     * 为指定用户设置新密码。
     */
    @PutMapping("/{id}/password")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody UserPasswordResetRequest request) {
        passwords.reset(id, request);
    }

    /**
     * 按用户 ID 和版本删除账号。
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @RequestParam Integer version) {
        users.delete(id, version);
    }

}
