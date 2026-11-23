<script setup lang="ts">
import {ref} from 'vue'
import {NModal, NForm, NFormItem, NInput, NSelect, NButton} from 'naive-ui'
import type {RoleVO} from '@/types/role'
import {createRole, updateRole} from '@/api/role'

const props = defineProps<{ role: RoleVO | null }>()
const emit = defineEmits<{ saved: []; close: [] }>()
const code = ref(props.role?.code || '')
const name = ref(props.role?.name || '')
const dataScope = ref(props.role?.dataScope || 'SELF')
const busy = ref(false)
const scopes = [{label: '全部', value: 'ALL'}, {label: '本部门', value: 'DEPT'}, {
  label: '本部门及下级',
  value: 'DEPT_AND_CHILDREN'
}, {label: '本人', value: 'SELF'}]

async function save() {
  if (busy.value) return
  busy.value = true
  try {
    if (props.role) await updateRole(props.role.id, {
      name: name.value,
      dataScope: dataScope.value,
      version: props.role.version
    })
    else await createRole({code: code.value, name: name.value, dataScope: dataScope.value})
    emit('saved')
  } catch { /* 保留输入。 */
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <NModal :show="true" preset="card" :title="role ? '编辑角色' : '新增角色'" style="width: 480px"
          @update:show="value => { if (!value) emit('close') }">
    <NForm @submit.prevent="save">
      <NFormItem label="编码（大写字母开头）" required>
        <NInput v-model:value="code" :disabled="!!role"/>
      </NFormItem>
      <NFormItem label="角色名称" required>
        <NInput v-model:value="name"/>
      </NFormItem>
      <NFormItem label="数据范围">
        <NSelect v-model:value="dataScope" :options="scopes"/>
      </NFormItem>
      <NButton attr-type="submit" type="primary" :loading="busy">保存</NButton>
    </NForm>
  </NModal>
</template>