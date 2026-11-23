export interface DeptVO {
    id: string;
    parentId: string | null;
    name: string;
    sortOrder: number;
    status: string;
    version: number;
    selectable: boolean
}

export interface DeptCreateRequest {
    parentId: string | null;
    name: string;
    sortOrder: number
}

export interface DeptUpdateRequest extends DeptCreateRequest {
    status: string;
    version: number
}