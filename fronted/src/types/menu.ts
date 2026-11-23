export interface MenuVO {
    id: string
    parentId: string | null
    name: string
    description: string | null
    type: string
    routeName: string | null
    path: string | null
    componentKey: string | null
    permissionCode: string | null
    sortOrder: number
    visible: boolean
    status: string
    version: number
}

export interface MenuCreateRequest {
    parentId: string | null
    name: string
    description?: string
    type: string
    routeName: string | null
    path: string | null
    componentKey: string | null
    permissionCode: string | null
    sortOrder: number
    visible: boolean
    status: string
}

export interface MenuUpdateRequest extends MenuCreateRequest {
    version: number
}