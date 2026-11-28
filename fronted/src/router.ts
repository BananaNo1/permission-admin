import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { ApiError } from '@/api/http'
import type { Component } from 'vue'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        { path: '/login', component: () => import('@/views/Login.vue') },
        {
            path: '/', name: 'layout', component: () => import('@/views/Layout.vue'), children: [
                { path: '', component: () => import('@/views/Home.vue') },
                { path: 'profile', component: () => import('@/views/Profile.vue') },
                {
                    path: '403',
                    component: () => import('@/views/Status.vue'),
                    props: { code: '403', title: '没有页面访问权限' }
                },
                {
                    path: 'unavailable',
                    component: () => import('@/views/Status.vue'),
                    props: { code: '503', title: '服务暂时不可用，请刷新重试' }
                },
            ]
        },
        {
            path: '/:pathMatch(.*)*',
            name: 'not-found',
            component: () => import('@/views/Status.vue'),
            props: { code: '404', title: '页面不存在' }
        },
    ],
})

const pageComponents: Record<string, () => Promise<{ default: Component }>> = {
    'workflow/templates': () => import('@/views/workflow/TemplatePage.vue'),
    'workflow/tasks': () => import('@/views/workflow/ApprovalCenterPage.vue'),
    'workflow/completed': () => import('@/views/workflow/ApprovalCenterPage.vue'),
    'workflow/applications': () => import('@/views/workflow/ApprovalCenterPage.vue'),
    'workflow/operations': () => import('@/views/workflow/ApprovalCenterPage.vue'),
    'system/user': () => import('@/views/user/UserPage.vue'),
    'system/role': () => import('@/views/role/RolePage.vue'),
    'system/dept': () => import('@/views/dept/DeptPage.vue'),
    'system/menu': () => import('@/views/menu/MenuPage.vue'),
    'audit/login': () => import('@/views/log/LoginLogPage.vue'),
    'audit/operation': () => import('@/views/log/OperationLogPage.vue'),
}
const approvalViews: Record<string, 'tasks' | 'completed' | 'applications' | 'operations'> = {
    'workflow/tasks': 'tasks',
    'workflow/completed': 'completed',
    'workflow/applications': 'applications',
    'workflow/operations': 'operations',
}

export function isMemberPage(componentKey: string | null | undefined) {
    return componentKey === 'system/user' || componentKey === 'workflow/templates'
        || (componentKey !== 'workflow/operations' && Object.hasOwn(approvalViews, componentKey ?? ''))
}

let signature = ''
let removers: Array<() => void> = []

export function clearRoutes() {
    removers.forEach(remove => remove())
    removers = [];
    signature = ''
}

function syncRoutes() {
    const auth = useAuth()
    const nextSignature = JSON.stringify([auth.me?.admin, auth.menus])
    if (nextSignature === signature) return false

    clearRoutes()
    signature = nextSignature
    for (const menu of auth.menus) {
        const component = pageComponents[menu.componentKey || '']
        if (menu.type !== 'PAGE' || !component || !menu.path || !menu.routeName) continue
        if (!auth.me?.admin && !isMemberPage(menu.componentKey)) continue
        const route: RouteRecordRaw = {
            path: menu.path, name: `menu-${menu.id}`,
            component,
            props: Object.hasOwn(approvalViews, menu.componentKey ?? '')
                ? { initialView: approvalViews[menu.componentKey!] } : undefined,
        }
        removers.push(router.addRoute('layout', route))
    }
    return true
}

export async function refreshCurrentRoute() {
    const auth = useAuth()
    const current = router.currentRoute.value
    if (!auth.me || current.path === '/login' || current.path === '/unavailable') return
    try {
        await auth.ensureLoaded()
    } catch {
        return
    }
    if (auth.me && router.currentRoute.value === current && syncRoutes()) {
        await router.replace(current.fullPath)
    }
}

router.beforeEach(async to => {
    if (to.path === '/login' || to.path === '/unavailable') return true
    const auth = useAuth()
    try {
        await auth.ensureLoaded()
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
            auth.clear();
            clearRoutes()
            return { path: '/login', query: { redirect: to.fullPath } }
        }
        return '/unavailable'
    }
    if (!auth.me) return { path: '/login', query: { redirect: to.fullPath } }
    if (syncRoutes()) {
        return { path: to.path, query: to.query, hash: to.hash, replace: true }
    }
    if (to.name === 'not-found' && /^\/(system|audit|workflow)\//.test(to.path)) return '/403'
    return true
})

export default router
