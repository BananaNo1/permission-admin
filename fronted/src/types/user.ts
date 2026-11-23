export interface UserVO {
    id: string
    username: string
    nickname: string
    deptId: string
    status: string
    version: number
}

export interface UserCreateRequest {
    username: string;
    nickname: string;
    deptId: string;
    password: string
}

export interface UserUpdateRequest {
    nickname: string;
    deptId: string;
    version: number
}

export interface UserStatusRequest {
    status: string;
    version: number
}

export interface UserRoleAssignRequest {
    roleIds: string[];
    version: number
}

export interface UserPasswordResetRequest {
    newPassword: string;
    version: number
}