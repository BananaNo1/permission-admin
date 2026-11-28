package com.leis.permission.dept.controller;

import com.leis.common.common.ApiResponse;
import com.leis.permission.dept.service.DeptService;
import com.leis.permission.dept.vo.DeptCreateRequest;
import com.leis.permission.dept.vo.DeptManagerCandidate;
import com.leis.permission.dept.vo.DeptUpdateRequest;
import com.leis.permission.dept.vo.DeptVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 暴露部门树查询和部门维护接口。
 */
@RestController
@RequestMapping("/api/system/depts")
@RequiredArgsConstructor
public class DeptController {
    private final DeptService service;

    /**
     * 返回当前用户可见的组织结构及可选部门。
     */
    @GetMapping
    public ApiResponse<List<DeptVO>> list() {
        return ApiResponse.ok(service.list());
    }

    /** 查询本部门可以设为负责人的成员。 */
    @GetMapping("/{id}/manager-candidates")
    public ApiResponse<List<DeptManagerCandidate>> managerCandidates(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(service.managerCandidates(id, keyword));
    }

    /**
     * 接收新增部门请求并返回创建结果。
     */
    @PostMapping
    public ApiResponse<DeptVO> create(@Valid @RequestBody DeptCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /**
     * 按部门 ID 和请求版本更新部门信息。
     */
    @PutMapping("/{id}")
    public ApiResponse<DeptVO> update(@PathVariable Long id, @Valid @RequestBody DeptUpdateRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /**
     * 按部门 ID 和版本删除指定部门。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        service.delete(id, version);
        return ApiResponse.ok();
    }
}
