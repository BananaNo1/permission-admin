import { api, changesAuthorization } from './http'
import type { PageResult } from '@/types/common'
import type {
    UserVO,
    UserCreateRequest,
    UserUpdateRequest,
    UserStatusRequest,
    UserRoleAssignRequest,
    UserPasswordResetRequest
} from '@/types/user'

const root = '/system/users'

export function listUsers(keyword: string, page: number, pageSize: number) {
    const query = new URLSearchParams({ keyword, page: String(page), pageSize: String(pageSize) })
    return api<PageResult<UserVO>>(root + '?' + query)
}

export function getUser(id: string) {
    return api<UserVO>(root + '/' + id)
}

export function createUser(body: UserCreateRequest) {
    return api<UserVO>(root, 'POST', body)
}

export function updateUser(id: string, body: UserUpdateRequest) {
    return changesAuthorization(api<UserVO>(root + '/' + id, 'PUT', body))
}

export function changeUserStatus(id: string, body: UserStatusRequest) {
    return api<void>(root + '/' + id + '/status', 'PUT', body)
}

export function listUserRoleIds(id: string) {
    return api<string[]>(root + '/' + id + '/roles')
}

export function assignUserRoles(id: string, body: UserRoleAssignRequest) {
    return changesAuthorization(api<void>(root + '/' + id + '/roles', 'PUT', body))
}

export function resetUserPassword(id: string, body: UserPasswordResetRequest) {
    return api<void>(root + '/' + id + '/password', 'PUT', body)
}

export function deleteUser(id: string, version: number) {
    return api<void>(root + '/' + id + '?version=' + version, 'DELETE')
}