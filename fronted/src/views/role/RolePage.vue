<script setup lang="ts">
import { h, onMounted, ref, computed, type VNode } from 'vue'
import { NCard, NButton, NSpace, NDataTable, useDialog, type DataTableColumns } from 'naive-ui'
import type { RoleVO } from '@/types/role'
import { listRoles, deleteRole, changeRoleStatus } from '@/api/role'
import RoleEditDialog from './RoleEditDialog.vue'
import RoleGrantDialog from './RoleGrantDialog.vue'

const dialog = useDialog()
const rows = ref<RoleVO[]>([])
const loading = ref(false)
const editOpen = ref(false)
const editing = ref<RoleVO | null>(null)
const granting = ref<RoleVO | null>(null)

async function load() {
  loading.value = true
  try {
    rows.value = await listRoles()
  } catch { /* 全局反馈。 */
  } finally {
    loading.value = false
  }
}

function edit(row: RoleVO | null) {
  editing.value = row;
  editOpen.value = true
}

async function saved() {
  editOpen.value = false;
  granting.value = null;
  await load()
}

function remove(row: RoleVO) {
  dialog.warning({
    title: '删除角色', content: '确认删除 ' + row.name + '？', positiveText: '删除', negativeText: '取消',
    async onPositiveClick() {
      try {
        await deleteRole(row.id, row.version);
        await load()
      } catch {
        return false
      }
    },
  })
}

function toggle(row: RoleVO) {
  const status = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  dialog.warning({
    title: '修改角色状态', content: '确认修改 ' + row.name + ' 的状态？', positiveText: '确认', negativeText: '取消',
    async onPositiveClick() {
      try {
        await changeRoleStatus(row.id, { status, version: row.version });
        await load()
      } catch {
        return false
      }
    },
  })
}

const columns: DataTableColumns<RoleVO> = [
  { title: '编码', key: 'code' },
  { title: '名称', key: 'name' },
  { title: '数据范围', key: 'dataScope' },
  { title: '状态', key: 'status' },
  {
    title: '操作', key: 'actions', width: 340, render(row) {
      const buttons: VNode[] = []
      buttons.push(h(NButton, { size: 'small', onClick: () => edit(row) }, { default: () => '编辑' }))
      buttons.push(h(NButton, { size: 'small', onClick: () => granting.value = row }, { default: () => '授权' }))
      buttons.push(h(NButton, {
        size: 'small',
        onClick: () => toggle(row)
      }, { default: () => row.status === 'ENABLED' ? '禁用' : '启用' }))
      buttons.push(h(NButton, { size: 'small', type: 'error', onClick: () => remove(row) }, { default: () => '删除' }))
      return h(NSpace, null, { default: () => buttons })
    }
  },
]
onMounted(load)
</script>
<template>
  <h1 class="management-heading">角色管理</h1>
  <NCard class="management-card">
    <div class="toolbar">
      <NButton @click="load">刷新</NButton>
      <NButton type="primary" @click="edit(null)">新增角色</NButton>
    </div>
    <NDataTable striped :columns="columns" :data="rows" :loading="loading" :row-key="row => row.id" :scroll-x="900" />
  </NCard>
  <RoleEditDialog v-if="editOpen" :role="editing" @saved="saved" @close="editOpen = false" />
  <RoleGrantDialog v-if="granting" :role="granting" @saved="saved" @close="granting = null" />
</template>