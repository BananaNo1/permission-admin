<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NAlert, NButton, NEmpty, NInput, NModal, NSpace, NSpin, NTree, type TreeOption } from 'naive-ui'
import { listMenus } from '@/api/menu'
import { grantRoleMenus, listRoleMenuIds } from '@/api/role'
import type { MenuVO } from '@/types/menu'
import type { RoleVO } from '@/types/role'
import AssignmentReview from '@/views/AssignmentReview.vue'

import { describePermission } from './permissionDisplay'

const props = defineProps<{ role: RoleVO }>()
const emit = defineEmits<{ saved: []; close: [] }>()
const menus = ref<MenuVO[]>([])
const originalIds = ref<string[]>([])
const ids = ref<string[]>([])
const expanded = ref<(string | number)[]>([])
const keyword = ref('')
const busy = ref(false)
const loadState = ref<'loading' | 'ready' | 'error'>('loading')

const menuById = computed(() => new Map(menus.value.map(menu => [menu.id, menu])))
const childrenByParent = computed(() => {
  const groups = new Map<string | null, MenuVO[]>()
  for (const menu of menus.value) {
    const siblings = groups.get(menu.parentId)
    if (siblings) siblings.push(menu)
    else groups.set(menu.parentId, [menu])
  }
  return groups
})
function matchesMenu(menu: MenuVO, pattern: string) {
  const { title, description, code } = describePermission(menu)
  return [title, description, code, menu.name].some(value => value?.toLowerCase().includes(pattern))
}

function filterTree(pattern: string, option: TreeOption) {
  const menu = menuById.value.get(String(option.key))
  return !!menu && matchesMenu(menu, pattern)
}

function renderPermissionLabel({ option }: { option: TreeOption }) {
  const menu = menuById.value.get(String(option.key))
  if (!menu) return option.label
  const { title, description, code } = describePermission(menu)

  return h('span', { class: 'permission-node' }, [
    h('span', { class: 'permission-title' }, title),
    description && h('small', { class: 'permission-description' }, description),
    code && h('code', { class: 'permission-code' }, code),
  ])
}

function tree(parentId: string | null): TreeOption[] {
  return (childrenByParent.value.get(parentId) ?? []).map(menu => {
    const children = tree(menu.id)
    return { key: menu.id, label: describePermission(menu).title, ...(children.length ? { children } : {}) }
  })
}

const options = computed(() => tree(null))
const branchIds = computed(() => [...childrenByParent.value.keys()].filter((id): id is string => id !== null))
const query = computed(() => keyword.value.trim().toLowerCase())
const matches = computed(() => query.value
  ? menus.value.filter(menu => matchesMenu(menu, query.value)).map(menu => menu.id)
  : [])
const checked = computed(() => new Set(ids.value))
const effective = computed(() => {
  const result = new Set(ids.value)
  for (const id of ids.value) {
    let parent = menuById.value.get(id)?.parentId
    while (parent && !result.has(parent)) {
      result.add(parent)
      parent = menuById.value.get(parent)?.parentId
    }
  }
  return [...result]
})
const effectiveSet = computed(() => new Set(effective.value))
const originalSet = computed(() => new Set(originalIds.value))
const entries = (keys: string[]) => keys.map(id => {
  const menu = menuById.value.get(id)
  if (!menu) return { id, label: id }
  const { title, code, description } = describePermission(menu)
  return { id, label: code ? [title, code].join(' · ') : title, description }
})
const added = computed(() => entries(effective.value.filter(id => !originalSet.value.has(id))))
const removed = computed(() => entries(originalIds.value.filter(id => !effectiveSet.value.has(id))))
const hasChanges = computed(() => added.value.length > 0 || removed.value.length > 0)

function setMatches(value: boolean) {
  const matched = new Set(matches.value)
  ids.value = value ? [...new Set([...ids.value, ...matched])] : ids.value.filter(id => !matched.has(id))
}

async function load() {
  loadState.value = 'loading'
  try {
    const [availableMenus, assignedIds] = await Promise.all([listMenus(), listRoleMenuIds(props.role.id)])
    menus.value = availableMenus
    originalIds.value = assignedIds
    ids.value = [...assignedIds]
    expanded.value = (childrenByParent.value.get(null) ?? []).map(menu => menu.id)
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

async function save() {
  if (loadState.value !== 'ready' || busy.value || !hasChanges.value) return
  busy.value = true
  try {
    await grantRoleMenus(props.role.id, { menuIds: effective.value, version: props.role.version })
    emit('saved')
  } catch { /* 保留当前选择，由公共请求层显示错误。 */ }
  finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <NModal :show="true" preset="card" title="角色授权" style="width: min(1040px, calc(100vw - 32px))"
          :mask-closable="false" :closable="!busy" :close-on-esc="!busy"
          @update:show="value => { if (!value && !busy) emit('close') }">
    <div class="assignment-subject">
      <span>角色</span><strong>{{ role.name }}</strong><span>{{ role.code }}</span>
    </div>
    <p v-if="role.code === 'SUPER_ADMIN'" class="assignment-hint">超级管理员拥有全部权限，此处的菜单配置不会缩小其权限范围。</p>
    <NSpin v-if="loadState === 'loading'">正在加载权限</NSpin>
    <NAlert v-else-if="loadState === 'error'" type="error" title="权限加载失败">
      <NButton size="small" @click="load">重试</NButton>
    </NAlert>
    <div v-else class="assignment-grid">
      <section class="assignment-panel" aria-label="权限树">
        <h3>菜单与操作</h3>
        <NInput v-model:value="keyword" clearable placeholder="搜索名称、用途或权限标识" :input-props="{ 'aria-label': '搜索权限' }" />
        <NSpace v-if="!query" class="assignment-controls" :size="8">
          <NButton size="small" @click="expanded = branchIds">展开全部</NButton>
          <NButton size="small" @click="expanded = []">折叠全部</NButton>
        </NSpace>
        <NSpace v-else class="assignment-controls" :size="8">
          <NButton size="small" :disabled="busy || !matches.some(id => !checked.has(id))"
                   @click="setMatches(true)">勾选匹配项</NButton>
          <NButton size="small" :disabled="busy || !matches.some(id => checked.has(id))"
                   @click="setMatches(false)">取消匹配项</NButton>
        </NSpace>
        <p class="assignment-hint">每次勾选一个目录、页面或操作。保存时自动补齐上级菜单，下级权限需单独勾选。</p>
        <div class="assignment-list">
          <NEmpty v-if="!options.length || (query && !matches.length)" description="未找到权限" />
          <NTree v-show="options.length && (!query || matches.length)" v-model:expanded-keys="expanded" :checked-keys="ids"
                 :data="options" :pattern="query" :filter="filterTree" :render-label="renderPermissionLabel" :show-irrelevant-nodes="false" :selectable="false"
                 checkable :cascade="false" :disabled="busy"
                 @update:checked-keys="keys => ids = keys.map(String)" />
        </div>
      </section>
      <AssignmentReview :selected="entries(effective)" :added="added" :removed="removed" />
    </div>
    <NSpace class="assignment-footer" justify="end">
      <NButton :disabled="busy" @click="emit('close')">取消</NButton>
      <NButton type="primary" :loading="busy" :disabled="loadState !== 'ready' || !hasChanges" @click="save">保存授权</NButton>
    </NSpace>
  </NModal>
</template>

<style scoped src="@/views/assignment-dialog.css"></style>

<style scoped>
.assignment-list :deep(.permission-node) {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    padding: 3px 0;
    line-height: 1.45;
    overflow-wrap: anywhere;
}

.assignment-list :deep(.permission-description),
.assignment-list :deep(.permission-code) {
    color: #495d78;
    font-size: 12px;
}

.assignment-list :deep(.permission-title) {
    font-weight: 600;
}
</style>
