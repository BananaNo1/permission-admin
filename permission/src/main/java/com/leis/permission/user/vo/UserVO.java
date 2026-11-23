package com.leis.permission.user.vo;

import com.leis.permission.user.entity.User;

public record UserVO(String id, String username, String nickname,
                     String deptId, String status, Integer version) {

    /**
     * 转换可返回给前端的用户资料和并发版本，不暴露密码摘要。
     */
    public static UserVO from(User row) {
        return new UserVO(
                (row.getId() == null ? null : row.getId().toString()),
                row.getUsername(),
                row.getNickname(),
                (row.getDeptId() == null ? null : row.getDeptId().toString()),
                row.getStatus(),
                row.getVersion()
        );
    }


}
