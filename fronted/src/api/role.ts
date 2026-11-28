import { api, changesAuthorization } from './http'
import type { RoleVO, RoleCreateRequest, RoleUpdateRequest, RoleStatusRequest, RoleGrantRequest } from '@/types/role'

const root = '/system/roles'

export function listRoles() {
    return api<RoleVO[]>(root)
}

export function createRole(body: RoleCreateRequest) {
    return api<RoleVO>(root, 'POST', body)
}

export function updateRole(id: string, body: RoleUpdateRequest) {
    return api<RoleVO>(root + '/' + id, 'PUT', body)
}

export function changeRoleStatus(id: string, body: RoleStatusRequest) {
    return changesAuthorization(api<void>(root + '/' + id + '/status', 'PUT', body))
}

export function listRoleMenuIds(id: string) {
    return api<string[]>(root + '/' + id + '/menus')
}

export function grantRoleMenus(id: string, body: RoleGrantRequest) {
    return changesAuthorization(api<void>(root + '/' + id + '/menus', 'PUT', body))
}

export function deleteRole(id: string, version: number) {
    return changesAuthorization(api<void>(root + '/' + id + '?version=' + version, 'DELETE'))
}