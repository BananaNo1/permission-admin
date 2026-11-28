package com.leis.permission.menu.controller;

import com.leis.common.common.ApiResponse;

import com.leis.permission.menu.dto.MenuCreateRequest;
import com.leis.permission.menu.dto.MenuUpdateRequest;
import com.leis.permission.menu.service.MenuService;
import com.leis.permission.menu.vo.MenuVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提供后台菜单与权限项的查询和维护接口。
 */
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController {
    private final MenuService menuService;

    /**
     * 返回管理员用于菜单维护的完整菜单列表。
     */
    @GetMapping
    public ApiResponse<List<MenuVO>> list() {
        return ApiResponse.ok(menuService.list());
    }

    /**
     * 接收菜单新增请求并返回持久化后的菜单信息。
     */
    @PostMapping
    public ApiResponse<MenuVO> create(@Valid @RequestBody MenuCreateRequest request) {
        return ApiResponse.ok(menuService.create(request));
    }

    /**
     * 保存指定菜单的配置和显示说明，使用请求版本检查并发修改。
     */
    @PutMapping("/{id}")
    public ApiResponse<MenuVO> update(@PathVariable Long id, @Valid @RequestBody MenuUpdateRequest request) {
        return ApiResponse.ok(menuService.update(id, request));
    }


    /**
     * 按 ID 和版本删除指定菜单。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        menuService.delete(id, version);
        return ApiResponse.ok();
    }

}
