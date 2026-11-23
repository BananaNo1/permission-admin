<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { NCard, NSpace, NTag } from 'naive-ui'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const router = useRouter()
const descriptions: Record<string, string> = {
  'system/user': '账号、状态与角色分配',
  'system/role': '角色与菜单授权',
  'system/dept': '部门与组织层级',
  'system/menu': '菜单层级与访问权限',
  'audit/login': '登录时间与结果',
  'audit/operation': '操作时间与变更记录',
}

const groups = computed(() => {
  const menusById = new Map(auth.menus.map(menu => [menu.id, menu]))
  const groupsById = new Map<string | null, {
    title: string
    entries: { id: string; name: string; path: string; description?: string }[]
  }>()

  for (const menu of auth.menus) {
    if (menu.type !== 'PAGE' || !menu.visible || !menu.path || !router.hasRoute(`menu-${menu.id}`)) continue

    let group = groupsById.get(menu.parentId)
    if (!group) {
      const parent = menu.parentId ? menusById.get(menu.parentId) : undefined
      group = { title: parent?.name ?? '管理功能', entries: [] }
      groupsById.set(menu.parentId, group)
    }
    group.entries.push({
      id: menu.id,
      name: menu.name,
      path: menu.path,
      description: descriptions[menu.componentKey ?? ''],
    })
  }

  return [...groupsById.entries()]
})
</script>

<template>
  <div class="workspace-home">
    <header class="workspace-heading">
      <h1>工作台</h1>
      <NTag v-if="auth.me?.admin" size="small" :bordered="false">超级管理员</NTag>
    </header>

    <div v-if="groups.length" class="workspace-groups">
      <section v-for="[id, group] in groups" :key="id ?? 'root'" class="workspace-group">
        <h2>{{ group.title }}</h2>
        <ul class="workspace-links">
          <li v-for="entry in group.entries" :key="entry.id">
            <RouterLink :to="entry.path">
              <span>{{ entry.name }}</span>
              <small v-if="entry.description">{{ entry.description }}</small>
            </RouterLink>
          </li>
        </ul>
      </section>
    </div>
    <NCard v-else>暂无可访问的管理页面。</NCard>

    <NCard v-if="!auth.me?.admin" title="当前权限" size="small">
      <NSpace v-if="auth.me?.permissions.length">
        <NTag v-for="permission in auth.me.permissions" :key="permission">{{ permission }}</NTag>
      </NSpace>
      <span v-else>暂无已分配的操作权限。</span>
    </NCard>
  </div>
</template>
