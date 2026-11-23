<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { NDropdown, type DropdownOption } from 'naive-ui'
import { useAuth } from '@/stores/auth'

type CloseAction = 'left' | 'current' | 'right' | 'all'

interface Visit {
  name: string
  path: string
  fullPath: string
}

const auth = useAuth()
const route = useRoute()
const router = useRouter()
const history = ref<Visit[]>([])
const contextVisible = ref(false)
const context = ref<{ name: string | null; x: number; y: number }>({ name: null, x: 0, y: 0 })
const bar = ref<HTMLElement | null>(null)
const menus = computed(() => new Map(auth.menus
  .filter(menu => menu.type === 'PAGE' && menu.visible && menu.path && menu.routeName
    && (auth.me?.admin || ['system/user','workflow/templates'].includes(menu.componentKey ?? '')))
  .map(menu => [`menu-${menu.id}`, menu])))

const tabs = computed(() => history.value.flatMap(visit => {
  const menu = menus.value.get(visit.name)
  return menu?.path === visit.path ? [{ ...visit, label: menu.name }] : []
}))

watch([() => route.fullPath, () => route.name, menus], () => {
  history.value = history.value.filter(visit => menus.value.get(visit.name)?.path === visit.path)
  const name = route.name
  if (typeof name !== 'string' || menus.value.get(name)?.path !== route.path) return

  const visit = { name, path: route.path, fullPath: route.fullPath }
  const index = history.value.findIndex(entry => entry.name === name)
  if (index === -1) {
    history.value.push(visit)
  } else {
    history.value[index] = visit
  }
}, { immediate: true })

watch([() => route.path, tabs], () => {
  const nav = bar.value
  const active = nav?.querySelector('.visit-tab-active')
  if (!nav || !active) return
  const tabBounds = active.getBoundingClientRect()
  const barBounds = nav.getBoundingClientRect()
  if (tabBounds.right > barBounds.right) nav.scrollLeft += tabBounds.right - barBounds.right
  if (tabBounds.left < barBounds.left) nav.scrollLeft -= barBounds.left - tabBounds.left
}, { flush: 'post' })

function openContextMenu(event: MouseEvent | KeyboardEvent, name: string | null) {
  const target = event.currentTarget
  if (!(target instanceof HTMLElement)) return
  const rect = target.getBoundingClientRect()
  const usePointer = event instanceof MouseEvent && (event.clientX !== 0 || event.clientY !== 0)
  context.value = {
    name,
    x: usePointer ? event.clientX : rect.left,
    y: usePointer ? event.clientY : rect.bottom,
  }
  contextVisible.value = true
}

const contextOptions = computed<DropdownOption[]>(() => {
  const index = tabs.value.findIndex(tab => tab.name === context.value.name)
  return [
    { key: 'left', label: '关闭左侧标签', disabled: index <= 0 },
    { key: 'current', label: '关闭当前标签', disabled: index === -1 },
    { key: 'right', label: '关闭右侧标签', disabled: index === tabs.value.length - 1 },
    { key: 'all', label: '关闭全部标签', disabled: tabs.value.length === 0 },
  ]
})

function selectAction(action: string | number) {
  contextVisible.value = false
  if (action === 'left' || action === 'current' || action === 'right' || action === 'all') {
    void closeTabs(action, context.value.name)
  }
}

async function closeTabs(action: CloseAction, name: string | null) {
  const open = tabs.value
  const index = open.findIndex(tab => tab.name === name)
  if (name !== null && index === -1) return

  const selected = open[index]
  const toClose = {
    current: selected ? [selected] : [],
    left: index > 0 ? open.slice(0, index) : [],
    right: open.slice(index + 1),
    all: open,
  }[action]
  if (!toClose.length) return

  if ((action === 'all' && route.path !== '/') || toClose.some(tab => tab.path === route.path)) {
    const fallback = action === 'current' ? open[index - 1] : selected
    const destination = action === 'all' ? '/' : fallback?.fullPath ?? '/'
    if (await router.replace(destination)) return
  }

  const closed = new Set(toClose.map(tab => tab.name))
  history.value = history.value.filter(visit => !closed.has(visit.name))
}

watch(() => route.fullPath, () => { contextVisible.value = false })
</script>

<template>
  <nav ref="bar" class="workspace-tabs" aria-label="已访问页面">
    <div class="visit-tab" :class="{ 'visit-tab-active': route.path === '/' }"
         @contextmenu.prevent="openContextMenu($event, null)"
         @keydown.shift.f10.prevent="openContextMenu($event, null)"
         @keydown.context-menu.prevent="openContextMenu($event, null)">
      <RouterLink to="/" aria-haspopup="menu">首页</RouterLink>
    </div>
    <div v-for="tab in tabs" :key="tab.name" class="visit-tab" :class="{ 'visit-tab-active': route.path === tab.path }"
         @contextmenu.prevent="openContextMenu($event, tab.name)"
         @keydown.shift.f10.prevent="openContextMenu($event, tab.name)"
         @keydown.context-menu.prevent="openContextMenu($event, tab.name)">
      <RouterLink :to="tab.fullPath" aria-haspopup="menu">{{ tab.label }}</RouterLink>
      <button type="button" :aria-label="'关闭' + tab.label" @click="closeTabs('current', tab.name)">×</button>
    </div>
  </nav>
  <NDropdown v-model:show="contextVisible" trigger="manual" placement="bottom-start"
             :x="context.x" :y="context.y" :options="contextOptions"
             :menu-props="() => ({ role: 'menu', 'aria-label': '历史标签操作' })"
             :node-props="option => ({ role: 'menuitem', 'aria-disabled': option.disabled ? 'true' : 'false' } as const)"
             @select="selectAction" @clickoutside="contextVisible = false" />
</template>

<style scoped>
.workspace-tabs {
    display: flex;
    gap: 6px;
    padding: 6px 16px;
    overflow-x: auto;
    border-bottom: 1px solid #e2eaf5;
    background: #fff;
}

.visit-tab {
    display: flex;
    flex: none;
    align-items: center;
    border: 1px solid #dce5f2;
    border-radius: 6px;
    color: #344761;
    font-size: 13px;
}

.visit-tab:hover {
    background: #f3f7ff;
}

.visit-tab-active,
.visit-tab-active:hover {
    border-color: #2453b1;
    background: #2453b1;
    color: #fff;
}

.visit-tab a,
.visit-tab button {
    color: inherit;
}

.visit-tab a {
    padding: 5px 10px;
    line-height: 18px;
    text-decoration: none;
}

.visit-tab button {
    width: 24px;
    height: 24px;
    margin-right: 2px;
    padding: 0;
    border: 0;
    border-radius: 4px;
    background: transparent;
    cursor: pointer;
    font-size: 16px;
}

.visit-tab button:hover {
    background: rgb(117 143 181 / 26%);
}

.visit-tab :focus-visible {
    outline: 2px solid currentColor;
    outline-offset: -2px;
}
</style>
