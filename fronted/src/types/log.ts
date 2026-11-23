export interface LoginLogVO {
    id: string;
    username: string;
    success: boolean;
    ip: string;
    traceId: string;
    createdAt: string
}

export interface OperationLogVO {
    id: string;
    actorUsername: string;
    action: string;
    targetType: string;
    targetId: string;
    success: boolean;
    summary: string;
    traceId: string;
    createdAt: string
}