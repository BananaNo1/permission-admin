package com.leis.workflow.access;

import com.leis.common.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 提供流程业务范围查询、创建和用户授权接口。
 */
@RestController
@RequestMapping("/api/workflow/businesses")
public class BusinessController {
    private final BusinessAccess access;

    /**
     * 注入流程业务范围和授权服务。
     */
    public BusinessController(BusinessAccess access) {
        this.access = access;
    }

    /**
     * 返回当前用户可见的流程业务及操作能力。
     */
    @GetMapping
    public ApiResponse<List<BusinessAccess.Business>> list() {
        return ApiResponse.ok(access.list());
    }

    /**
     * 接收新建流程业务范围的请求。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> create(@Valid @RequestBody BusinessAccess.Create request) {
        access.create(request);
        return ApiResponse.created(null);
    }

    /**
     * 查询指定业务已有的用户授权。
     */
    @GetMapping("/{code}/grants")
    public ApiResponse<List<BusinessAccess.Grant>> grants(@PathVariable String code) {
        return ApiResponse.ok(access.grants(code));
    }

    /**
     * 提交指定用户在该业务下的完整权限设置。
     */
    @PutMapping("/{code}/grants/{userId}")
    public ApiResponse<Void> grant(@PathVariable String code, @PathVariable long userId, @RequestBody BusinessAccess.Rights request) {
        access.grant(code, userId, request);
        return ApiResponse.ok();
    }
}
