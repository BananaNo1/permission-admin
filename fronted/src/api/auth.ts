import { api } from './http'
import type { MenuVO } from '@/types/menu'

export interface MeVO {
    id: string;
    username: string;
    deptId: string;
    admin: boolean;
    permissions: string[]
}

export function getMe() {
    return api<MeVO>('/auth/me')
}

export function getMyMenus() {
    return api<MenuVO[]>('/auth/menus')
}

export function login(username: string, password: string) {
    return api<void>('/auth/login', 'POST', new URLSearchParams({ username, password }))
}

export function logout() {
    return api<void>('/auth/logout', 'POST')
}

export function changePassword(oldPassword: string, newPassword: string) {
    return api<void>('/auth/password', 'PUT', { oldPassword, newPassword })
}