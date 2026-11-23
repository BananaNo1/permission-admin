import {defineStore} from 'pinia'
import {ref} from 'vue'
import * as authApi from '@/api/auth'
import {refreshCsrf} from '@/api/http'
import type {MeVO} from '@/api/auth'
import type {MenuVO} from '@/types/menu'

export type {MenuVO as Menu} from '@/types/menu'
export const useAuth = defineStore('auth', () => {
    const me = ref<MeVO | null>(null)
    const menus = ref<MenuVO[]>([])

    function has(permission: string) {
        return !!me.value?.admin || !!me.value?.permissions.includes(permission)
    }

    async function refresh() {
        me.value = await authApi.getMe();
        menus.value = await authApi.getMyMenus()
    }

    async function login(username: string, password: string) {
        await refreshCsrf()
        await authApi.login(username.trim().toLowerCase(), password)
        await refreshCsrf()
        await refresh()
    }

    function clear() {
        me.value = null;
        menus.value = []
    }

    async function logout() {
        await authApi.logout();
        clear();
        await refreshCsrf()
    }

    return {me, menus, has, refresh, login, logout, clear}
})