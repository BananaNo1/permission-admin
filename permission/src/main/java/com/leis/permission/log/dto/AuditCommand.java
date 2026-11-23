package com.leis.permission.log.dto;

public record AuditCommand(Long actorId, String actorUsername, String action,
                           String targetType, String targetId, boolean success,
                           String summary, String traceId) {
}
