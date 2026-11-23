package com.leis.permission.log.vo;

public record LoginLogVO(String id, String username, boolean success, String ip, String traceId, String createdAt) {
}
