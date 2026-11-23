import {api} from './http'
import type {MenuVO, MenuCreateRequest, MenuUpdateRequest} from '@/types/menu'

const root = '/system/menus'

export function listMenus() {
    return api<MenuVO[]>(root)
}

export function createMenu(body: MenuCreateRequest) {
    return api<MenuVO>(root, 'POST', body)
}

export function updateMenu(id: string, body: MenuUpdateRequest) {
    return api<MenuVO>(root + '/' + id, 'PUT', body)
}

export function deleteMenu(id: string, version: number) {
    return api<void>(root + '/' + id + '?version=' + version, 'DELETE')
}