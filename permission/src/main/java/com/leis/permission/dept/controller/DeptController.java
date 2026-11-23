package com.leis.permission.dept.controller;

import com.leis.permission.dept.service.DeptService;
import com.leis.permission.dept.vo.DeptCreateRequest;
import com.leis.permission.dept.vo.DeptUpdateRequest;
import com.leis.permission.dept.vo.DeptVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/depts")
@RequiredArgsConstructor
public class DeptController {
    private final DeptService service;

    /**
     * 返回当前用户可见的组织结构及可选部门。
     */
    @GetMapping
    public List<DeptVO> list() {
        return service.list();
    }

    /**
     * 接收新增部门请求并返回创建结果。
     */
    @PostMapping
    public DeptVO create(@Valid @RequestBody DeptCreateRequest request) {
        return service.create(request);
    }

    /**
     * 按部门 ID 和请求版本更新部门信息。
     */
    @PutMapping("/{id}")
    public DeptVO update(@PathVariable Long id, @Valid @RequestBody DeptUpdateRequest request) {
        return service.update(id, request);
    }

    /**
     * 按部门 ID 和版本删除指定部门。
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @RequestParam Integer version) {
        service.delete(id, version);
    }
}
