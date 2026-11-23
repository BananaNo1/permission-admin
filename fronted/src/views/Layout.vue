<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NLayout, NLayoutHeader, NLayoutSider, NMenu, NSpace, type MenuOption } from 'naive-ui'
import { useAuth, type Menu } from '@/stores/auth'
import { clearRoutes } from '@/router'
import VisitedPages from './VisitedPages.vue'

const auth = useAuth()
const route = useRoute()
const router = useRouter()
const collapsed = ref(window.matchMedia('(max-width: 760px)').matches)

function tree(parentId: string | null): MenuOption[] {
  return auth.menus.filter(row => row.parentId === parentId && row.visible && row.type !== 'BUTTON')
      .flatMap((row: Menu) => {
        if (row.type === 'PAGE') {
          if (!auth.me?.admin && row.componentKey !== 'system/user') return []
          return [{ label: row.name, key: row.path || row.id }]
        }
        const children = tree(row.id)
        return children.length ? [{ label: row.name, key: row.id, children }] : []
      })
}

const options = computed<MenuOption[]>(() => [{ label: '首页', key: '/' }, ...tree(null)])
const locationLabel = computed(() => {
  if (route.path === '/') return '首页'
  if (route.path === '/profile') return '个人设置'
  const current = auth.menus.find(menu => menu.path === route.path)
  return auth.menus.find(menu => menu.id === current?.parentId)?.name ?? '权限管理后台'
})

async function logout() {
  try {
    await auth.logout()
    clearRoutes()
    await router.replace('/login')
  } catch { /* 保留当前界面以便重试。 */ }
}
</script>

<template>
  <NLayout has-sider class="shell">
    <NLayoutSider v-model:collapsed="collapsed" inverted :width="224" collapse-mode="width" :collapsed-width="64" show-trigger>
      <div class="brand" :class="{ 'brand-collapsed': collapsed }">
        <span class="brand-mark" aria-hidden="true">权</span>
        <span v-if="!collapsed">权限管理后台</span>
      </div>
      <nav aria-label="后台导航">
        <NMenu inverted :value="route.path" :options="options" @update:value="key => router.push(key)" />
      </nav>
    </NLayoutSider>
    <NLayout class="workspace-layout">
      <NLayoutHeader bordered class="topbar">
        <span class="workspace-location">{{ locationLabel }}</span>
        <div class="workspace-account">
          <div v-if="auth.me" class="workspace-identity">
            <span class="workspace-avatar" aria-hidden="true">{{ auth.me.username.slice(0, 1).toUpperCase() }}</span>
            <span>{{ auth.me.username }}</span>
          </div>
          <NSpace v-if="auth.me" :size="8">
            <NButton secondary size="small" @click="router.push('/profile')">修改密码</NButton>
            <NButton quaternary size="small" @click="logout">退出登录</NButton>
          </NSpace>
        </div>
      </NLayoutHeader>
      <VisitedPages v-if="auth.me" :key="auth.me.id" />
      <main class="content"><RouterView :key="route.path" /></main>
    </NLayout>
  </NLayout>
</template>
