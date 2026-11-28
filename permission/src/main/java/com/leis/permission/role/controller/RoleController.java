package com.leis.permission.role.controller;

import com.leis.common.common.ApiResponse;
import java.util.List;

import com.leis.permission.role.dto.RoleCreateRequest;
import com.leis.permission.role.dto.RoleGrantRequest;
import com.leis.permission.role.dto.RoleStatusRequest;
import com.leis.permission.role.dto.RoleUpdateRequest;
import com.leis.permission.role.service.RoleGrantService;
import com.leis.permission.role.service.RoleService;
import com.leis.permission.role.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 提供角色维护、状态变更和菜单授权接口。
 */
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roles;
    private final RoleGrantService grants;

    /**
     * 提供角色维护和用户分配角色所需的角色列表。
     */
    @GetMapping
    public ApiResponse<List<RoleVO>> list() {
        return ApiResponse.ok(roles.list());
    }

    /**
     * 接收新增角色请求并返回创建结果。
     */
    @PostMapping
    public ApiResponse<RoleVO> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(roles.create(request));
    }

    /**
     * 按角色 ID 修改名称和数据范围。
     */
    @PutMapping("/{id}")
    public ApiResponse<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.ok(roles.update(id, request));
    }

    /**
     * 启用或禁用指定角色。
     */
    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody RoleStatusRequest request) {
        roles.changeStatus(id, request);
        return ApiResponse.ok();
    }

    /**
     * 返回指定角色当前保存的菜单授权 ID。
     */
    @GetMapping("/{id}/menus")
    public ApiResponse<List<String>> menuIds(@PathVariable Long id) {
        return ApiResponse.ok(grants.menuIds(id));
    }

    /**
     * 用提交的完整菜单 ID 集合更新角色授权。
     */
    @PutMapping("/{id}/menus")
    public ApiResponse<Void> grant(@PathVariable Long id, @Valid @RequestBody RoleGrantRequest request) {
        grants.grant(id, request);
        return ApiResponse.ok();
    }

    /**
     * 按 ID 和版本删除指定角色。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        roles.delete(id, version);
        return ApiResponse.ok();
    }
}