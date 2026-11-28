<script setup lang="ts">
import { h, onMounted, ref, computed, type VNode } from 'vue'
import { NCard, NButton, NSpace, NDataTable, useDialog, type DataTableColumns } from 'naive-ui'
import type { DeptVO } from '@/types/dept'
import { listDepts, deleteDept } from '@/api/dept'
import DeptEditDialog from './DeptEditDialog.vue'

const dialog = useDialog()
const rows = ref<DeptVO[]>([])
const loading = ref(false)
const editOpen = ref(false)
const editing = ref<DeptVO | null>(null)

async function load() {
  loading.value = true
  try {
    rows.value = await listDepts()
  } catch { /* 全局反馈。 */
  } finally {
    loading.value = false
  }
}

function edit(row: DeptVO | null) {
  editing.value = row;
  editOpen.value = true
}

async function saved() {
  editOpen.value = false;
  await load()
}

function remove(row: DeptVO) {
  dialog.warning({
    title: '删除部门', content: '确认删除 ' + row.name + '？', positiveText: '删除', negativeText: '取消',
    async onPositiveClick() {
      try {
        await deleteDept(row.id, row.version);
        await load()
      } catch {
        return false
      }
    },
  })
}

type DeptRow = DeptVO & { children?: DeptRow[] }

function tree(parentId: string | null): DeptRow[] {
  return rows.value.filter(row => row.parentId === parentId).map(row => {
    const children = tree(row.id)
    return { ...row, ...(children.length ? { children } : {}) }
  })
}

const tableRows = computed(() => tree(null))
const columns: DataTableColumns<DeptVO> = [
  { title: '部门', key: 'name' },
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
  <h1 class="management-heading">部门管理</h1>
  <NCard class="management-card">
    <div class="toolbar">
      <NButton @click="load">刷新</NButton>
      <NButton type="primary" @click="edit(null)">新增部门</NButton>
    </div>
    <NDataTable striped :columns="columns" :data="tableRows" :loading="loading" :row-key="row => row.id" :scroll-x="900" />
  </NCard>
  <DeptEditDialog v-if="editOpen" :dept="editing" :depts="rows" @saved="saved" @close="editOpen = false" />
</template>