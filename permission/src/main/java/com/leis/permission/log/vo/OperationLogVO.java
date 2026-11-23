package com.leis.permission.log.vo;

public record OperationLogVO(String id, String actorUsername, String action, String targetType,
                             String targetId, boolean success, String summary, String traceId, String createdAt) {
}