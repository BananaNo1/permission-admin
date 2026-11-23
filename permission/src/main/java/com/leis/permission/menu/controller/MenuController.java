package com.leis.permission.menu.controller;


import com.leis.permission.menu.dto.MenuCreateRequest;
import com.leis.permission.menu.dto.MenuUpdateRequest;
import com.leis.permission.menu.service.MenuService;
import com.leis.permission.menu.vo.MenuVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController {
    private final MenuService menuService;

    /**
     * 返回管理员用于菜单维护的完整菜单列表。
     */
    @GetMapping
    public List<MenuVO> list() {
        return menuService.list();
    }

    /**
     * 接收菜单新增请求并返回持久化后的菜单信息。
     */
    @PostMapping
    public MenuVO create(@Valid @RequestBody MenuCreateRequest request) {
        return menuService.create(request);
    }

    /**
     * 保存指定菜单的配置和显示说明，使用请求版本检查并发修改。
     */
    @PutMapping("/{id}")
    public MenuVO update(@PathVariable Long id, @Valid @RequestBody MenuUpdateRequest request) {
        return menuService.update(id, request);
    }


    /**
     * 按 ID 和版本删除指定菜单。
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @RequestParam Integer version) {
        menuService.delete(id, version);
    }

}
