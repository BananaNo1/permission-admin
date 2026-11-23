<script setup lang="ts">
import {h, onMounted, ref, type VNode} from 'vue'
import {NCard, NInput, NButton, NSpace, NDataTable, NPagination, useDialog, type DataTableColumns} from 'naive-ui'
import type {UserVO} from '@/types/user'
import {listUsers, deleteUser, changeUserStatus} from '@/api/user'
import {useAuth} from '@/stores/auth'
import UserEditDialog from './UserEditDialog.vue'
import UserRoleDialog from './UserRoleDialog.vue'
import UserPasswordDialog from './UserPasswordDialog.vue'

const auth = useAuth()
const dialog = useDialog()
const rows = ref<UserVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const loading = ref(false)
const editOpen = ref(false)
const editing = ref<UserVO | null>(null)
const assigning = ref<UserVO | null>(null)
const resetting = ref<UserVO | null>(null)

async function load() {
  loading.value = true
  try {
    const result = await listUsers(keyword.value, page.value, pageSize.value);
    rows.value = result.records;
    total.value = result.total
  } catch { /* 全局反馈。 */
  } finally {
    loading.value = false
  }
}

function edit(row: UserVO | null) {
  editing.value = row;
  editOpen.value = true
}

async function saved() {
  editOpen.value = false;
  assigning.value = null;
  resetting.value = null;
  await load()
}

function remove(row: UserVO) {
  dialog.warning({
    title: '删除用户', content: '确认删除 ' + row.username + '？', positiveText: '删除', negativeText: '取消',
    async onPositiveClick() {
      try {
        await deleteUser(row.id, row.version);
        if (rows.value.length === 1 && page.value > 1) page.value--;
        await load()
      } catch {
        return false
      }
    },
  })
}

function toggle(row: UserVO) {
  const status = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  dialog.warning({
    title: '修改状态', content: '确认修改 ' + row.username + ' 的状态？', positiveText: '确认', negativeText: '取消',
    async onPositiveClick() {
      try {
        await changeUserStatus(row.id, {status, version: row.version});
        await load()
      } catch {
        return false
      }
    },
  })
}

const columns: DataTableColumns<UserVO> = [
  {title: '账号', key: 'username'}, {title: '姓名', key: 'nickname'},
  {title: '部门ID', key: 'deptId'}, {title: '状态', key: 'status'},
  {
    title: '操作', key: 'actions', width: 420, render(row) {
      const buttons: VNode[] = []
      if (auth.has('system:user:update')) {
        buttons.push(h(NButton, {size: 'small', onClick: () => edit(row)}, {default: () => '编辑'}))
        buttons.push(h(NButton, {
          size: 'small',
          onClick: () => toggle(row)
        }, {default: () => row.status === 'ENABLED' ? '禁用' : '启用'}))
      }
      if (auth.me?.admin) {
        buttons.push(h(NButton, {
          size: 'small',
          onClick: () => assigning.value = row
        }, {default: () => '分配角色'}))
      }
      if (auth.has('system:user:reset-password')) {
        buttons.push(h(NButton, {
          size: 'small',
          onClick: () => resetting.value = row
        }, {default: () => '重置密码'}))
      }
      if (auth.has('system:user:delete')) {
        buttons.push(h(NButton, {
          size: 'small',
          type: 'error',
          onClick: () => remove(row)
        }, {default: () => '删除'}))
      }
      return h(NSpace, null, {default: () => buttons})
    }
  },
]
onMounted(load)
</script>
<template>
  <h1 class="management-heading">用户管理</h1>
  <NCard class="management-card">
    <div class="toolbar">
      <NSpace>
        <NInput v-model:value="keyword" placeholder="账号或姓名" clearable @keyup.enter="page = 1; load()"/>
        <NButton @click="page = 1; load()">查询</NButton>
      </NSpace>
      <NButton v-if="auth.has('system:user:create')" type="primary" @click="edit(null)">新增用户</NButton>
    </div>
    <NDataTable striped :columns="columns" :data="rows" :loading="loading" :row-key="row => row.id" :scroll-x="1000"/>
    <div class="pagination">
      <NPagination v-model:page="page" v-model:page-size="pageSize" :item-count="total" show-size-picker
                   :page-sizes="[10,20,50,100]" @update:page="load" @update:page-size="page = 1; load()"/>
    </div>
  </NCard>
  <UserEditDialog v-if="editOpen" :user="editing" @saved="saved" @close="editOpen = false"/>
  <UserRoleDialog v-if="assigning" :user="assigning" @saved="saved" @close="assigning = null"/>
  <UserPasswordDialog v-if="resetting" :user="resetting" @saved="saved" @close="resetting = null"/>
</template>