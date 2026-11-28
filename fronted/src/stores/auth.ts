import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import { refreshCsrf } from '@/api/http'
import type { MeVO } from '@/api/auth'
import type { MenuVO } from '@/types/menu'

export type { MenuVO as Menu } from '@/types/menu'
const AUTH_CACHE_TTL_MS = 30_000

export const useAuth = defineStore('auth', () => {
    const me = ref<MeVO | null>(null)
    const menus = ref<MenuVO[]>([])
    let loadedAt: number | null = null
    let pending: Promise<void> | null = null
    let generation = 0

    function has(permission: string) {
        return !!me.value?.admin || !!me.value?.permissions.includes(permission)
    }

    async function ensureLoaded() {
        if (loadedAt !== null && Date.now() - loadedAt < AUTH_CACHE_TTL_MS) return
        if (pending) return pending

        const current = generation
        const request = (async () => {
            const identity = await authApi.getMe()
            if (current !== generation) return
            const grantedMenus = await authApi.getMyMenus()
            if (current !== generation) return

            me.value = identity
            menus.value = grantedMenus
            loadedAt = Date.now()
        })().finally(() => {
            if (pending === request) pending = null
        })
        pending = request
        return request
    }

    function invalidate() {
        generation++
        pending = null
        loadedAt = null
    }

    async function login(username: string, password: string) {
        await refreshCsrf()
        await authApi.login(username.trim().toLowerCase(), password)
        await refreshCsrf()
        invalidate()
        await ensureLoaded()
    }

    function clear() {
        invalidate()
        me.value = null;
        menus.value = []
    }

    async function logout() {
        await authApi.logout();
        clear();
        await refreshCsrf()
    }

    return { me, menus, has, ensureLoaded, invalidate, login, logout, clear }
})