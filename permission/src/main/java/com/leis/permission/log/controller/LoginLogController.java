package com.leis.permission.log.controller;

import com.leis.common.common.PageResult;
import com.leis.permission.log.service.LogQueryService;
import com.leis.permission.log.vo.LoginLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/audit/login-logs")
public class LoginLogController {

    private final LogQueryService service;

    /**
     * 分页查询登录审计记录。
     */
    @GetMapping
    public PageResult<LoginLogVO> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        return service.loginLogs(page, pageSize);
    }
}
