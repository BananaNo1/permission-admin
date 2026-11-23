<script setup lang="ts">
import {computed, ref} from 'vue'
import {NModal, NForm, NFormItem, NInput, NInputNumber, NSelect, NButton} from 'naive-ui'
import type {DeptVO} from '@/types/dept'
import {createDept, updateDept} from '@/api/dept'

const props = defineProps<{ dept: DeptVO | null; depts: DeptVO[] }>()
const emit = defineEmits<{ saved: []; close: [] }>()
const name = ref(props.dept?.name || '')
const parentId = ref<string | null>(props.dept?.parentId || null)
const sortOrder = ref(props.dept?.sortOrder || 0)
const status = ref(props.dept?.status || 'ENABLED')
const busy = ref(false)
const parentOptions = computed(() => {
  const excluded = new Set<string>()
  if (props.dept) excluded.add(props.dept.id)
  let changed = true
  while (changed) {
    changed = false
    for (const row of props.depts) if (row.parentId && excluded.has(row.parentId) && !excluded.has(row.id)) {
      excluded.add(row.id);
      changed = true
    }
  }
  return props.depts.map(row => ({label: row.name, value: row.id, disabled: excluded.has(row.id)}))
})

async function save() {
  if (busy.value) return
  busy.value = true
  try {
    if (props.dept) await updateDept(props.dept.id, {
      name: name.value,
      parentId: parentId.value,
      sortOrder: sortOrder.value,
      status: status.value,
      version: props.dept.version
    })
    else await createDept({name: name.value, parentId: parentId.value, sortOrder: sortOrder.value})
    emit('saved')
  } catch { /* 保留输入。 */
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <NModal :show="true" preset="card" title="部门维护" style="width: 480px"
          @update:show="value => { if (!value) emit('close') }">
    <NForm @submit.prevent="save">
      <NFormItem label="名称" required>
        <NInput v-model:value="name"/>
      </NFormItem>
      <NFormItem label="父部门">
        <NSelect v-model:value="parentId" :options="parentOptions" clearable/>
      </NFormItem>
      <NFormItem label="排序">
        <NInputNumber v-model:value="sortOrder" :min="0"/>
      </NFormItem>
      <NFormItem v-if="dept" label="状态">
        <NSelect v-model:value="status" :options="[{label:'启用',value:'ENABLED'},{label:'禁用',value:'DISABLED'}]"/>
      </NFormItem>
      <NButton attr-type="submit" type="primary" :loading="busy">保存</NButton>
    </NForm>
  </NModal>
</template>