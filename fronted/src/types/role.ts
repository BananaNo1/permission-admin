export interface RoleVO {
    id: string;
    code: string;
    name: string;
    status: string;
    dataScope: string;
    version: number
}

export interface RoleCreateRequest {
    code: string;
    name: string;
    dataScope: string
}

export interface RoleUpdateRequest {
    name: string;
    dataScope: string;
    version: number
}

export interface RoleStatusRequest {
    status: string;
    version: number
}

export interface RoleGrantRequest {
    menuIds: string[];
    version: number
}