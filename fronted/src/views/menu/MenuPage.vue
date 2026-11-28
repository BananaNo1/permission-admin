<script setup lang="ts">
import { h, onMounted, ref, computed, type VNode } from 'vue'
import { NCard, NButton, NSpace, NDataTable, useDialog, type DataTableColumns } from 'naive-ui'
import type { MenuVO } from '@/types/menu'
import { listMenus, deleteMenu } from '@/api/menu'
import MenuEditDialog from './MenuEditDialog.vue'

const dialog = useDialog()
const rows = ref<MenuVO[]>([])
const loading = ref(false)
const editOpen = ref(false)
const editing = ref<MenuVO | null>(null)

async function load() {
  loading.value = true
  try {
    rows.value = await listMenus()
  } catch { /* 全局反馈。 */
  } finally {
    loading.value = false
  }
}

function edit(row: MenuVO | null) {
  editing.value = row;
  editOpen.value = true
}

async function saved() {
  editOpen.value = false;
  await load()
}

function remove(row: MenuVO) {
  dialog.warning({
    title: '删除菜单', content: '确认删除 ' + row.name + '？', positiveText: '删除', negativeText: '取消',
    async onPositiveClick() {
      try {
        await deleteMenu(row.id, row.version);
        await load()
      } catch {
        return false
      }
    },
  })
}

type MenuRow = MenuVO & { children?: MenuRow[] }

function tree(parentId: string | null): MenuRow[] {
  return rows.value.filter(row => row.parentId === parentId).map(row => {
    const children = tree(row.id)
    return { ...row, ...(children.length ? { children } : {}) }
  })
}

const tableRows = computed(() => tree(null))
const columns: DataTableColumns<MenuVO> = [
  { title: '菜单', key: 'name' },
  { title: '类型', key: 'type' },
  { title: '权限标识', key: 'permissionCode' },
  { title: '状态', key: 'status' },
  {
    title: '操作', key: 'actions', width: 200, render(row) {
      const buttons: VNode[] = []
      buttons.push(h(NButton, { size: 'small', onClick: () => edit(row) }, { default: () => '编辑' }))
      buttons.push(h(NButton, { size: 'small', type: 'error', onClick: () => remove(row) }, { default: () => '删除' }))
      return h(NSpace, null, { default: () => buttons })
    }
  },
]
onMounted(load)
</script>
<template>
  <h1 class="management-heading">菜单管理</h1>
  <NCard class="management-card">
    <div class="toolbar">
      <NButton @click="load">刷新</NButton>
      <NButton type="primary" @click="edit(null)">新增菜单</NButton>
    </div>
    <NDataTable striped :columns="columns" :data="tableRows" :loading="loading" :row-key="row => row.id" :scroll-x="900" />
  </NCard>
  <MenuEditDialog v-if="editOpen" :menu="editing" :menus="rows" @saved="saved" @close="editOpen = false" />
</template>