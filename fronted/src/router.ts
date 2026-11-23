import {createRouter, createWebHistory, type RouteRecordRaw} from 'vue-router'
import {useAuth} from '@/stores/auth'
import {ApiError} from '@/api/http'
import type {Component} from 'vue'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {path: '/login', component: () => import('@/views/Login.vue')},
        {
            path: '/', name: 'layout', component: () => import('@/views/Layout.vue'), children: [
                {path: '', component: () => import('@/views/Home.vue')},
                {path: 'profile', component: () => import('@/views/Profile.vue')},
                {
                    path: '403',
                    component: () => import('@/views/Status.vue'),
                    props: {code: '403', title: '没有页面访问权限'}
                },
                {
                    path: 'unavailable',
                    component: () => import('@/views/Status.vue'),
                    props: {code: '503', title: '服务暂时不可用，请刷新重试'}
                },
            ]
        },
        {
            path: '/:pathMatch(.*)*',
            name: 'not-found',
            component: () => import('@/views/Status.vue'),
            props: {code: '404', title: '页面不存在'}
        },
    ],
})

const pageComponents: Record<string, () => Promise<{ default: Component }>> = {
    'workflow/templates': () => import('@/views/workflow/TemplatePage.vue'),
    'system/user': () => import('@/views/user/UserPage.vue'),
    'system/role': () => import('@/views/role/RolePage.vue'),
    'system/dept': () => import('@/views/dept/DeptPage.vue'),
    'system/menu': () => import('@/views/menu/MenuPage.vue'),
    'audit/login': () => import('@/views/log/LoginLogPage.vue'),
    'audit/operation': () => import('@/views/log/OperationLogPage.vue'),
}
let signature = ''
let removers: Array<() => void> = []

export function clearRoutes() {
    removers.forEach(remove => remove())
    removers = [];
    signature = ''
}

router.beforeEach(async to => {
    if (to.path === '/login' || to.path === '/unavailable') return true
    const auth = useAuth()
    try {
        await auth.refresh()
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
            auth.clear();
            clearRoutes()
            return {path: '/login', query: {redirect: to.fullPath}}
        }
        return '/unavailable'
    }
    const nextSignature = JSON.stringify(auth.menus)
    if (nextSignature !== signature) {
        clearRoutes()
        signature = nextSignature
        for (const menu of auth.menus) {
            const component = pageComponents[menu.componentKey || '']
            if (menu.type !== 'PAGE' || !component || !menu.path || !menu.routeName) continue
            if (!['system/user','workflow/templates'].includes(menu.componentKey ?? '') && !auth.me?.admin) continue
            const route: RouteRecordRaw = {
                path: menu.path, name: `menu-${menu.id}`,
                component,
            }
            removers.push(router.addRoute('layout', route))
        }
        return {path: to.path, query: to.query, hash: to.hash, replace: true}
    }
    if (to.name === 'not-found' && /^\/(system|audit|workflow)\//.test(to.path)) return '/403'
    return true
})

export default router
