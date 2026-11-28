<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NModal, NForm, NFormItem, NInput, NInputNumber, NSelect, NButton } from 'naive-ui'
import type { DeptVO } from '@/types/dept'
import { createDept, managerCandidates, updateDept } from '@/api/dept'

const props = defineProps<{ dept: DeptVO | null; depts: DeptVO[] }>()
const emit = defineEmits<{ saved: []; close: [] }>()
const name = ref(props.dept?.name || '')
const parentId = ref<string | null>(props.dept?.parentId || null)
const sortOrder = ref(props.dept?.sortOrder || 0)
const status = ref(props.dept?.status || 'ENABLED')
const busy = ref(false)
const managerId = ref<string | null>(props.dept?.managerUserId ?? null)
const managerChanged = ref(false)
const managerLoading = ref(false)
const managerError = ref('')
const members = ref<{ value: string; label: string }[]>([])
const knownMembers = new Map<string, string>()
let managerRequest = 0
const managerOptions = computed(() => managerId.value && !members.value.some(member => member.value === managerId.value)
  ? [{ value: managerId.value, label: knownMembers.get(managerId.value) ?? '账号 #' + managerId.value }, ...members.value]
  : members.value)

async function searchManagers(keyword: string) {
  if (!props.dept) return
  const request = ++managerRequest
  managerLoading.value = true
  managerError.value = ''
  try {
    const rows = await managerCandidates(props.dept.id, keyword)
    if (request !== managerRequest) return
    members.value = rows.map(row => ({ value: row.id, label: row.nickname + '（' + row.username + '）' }))
    for (const option of members.value) knownMembers.set(option.value, option.label)
  } catch {
    if (request === managerRequest) managerError.value = '负责人查询失败，请重新搜索'
  } finally {
    if (request === managerRequest) managerLoading.value = false
  }
}

onMounted(() => searchManagers(''))
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
  return props.depts.map(row => ({ label: row.name, value: row.id, disabled: excluded.has(row.id) }))
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
      version: props.dept.version,
      ...(managerChanged.value ? { managerAssignment: { userId: managerId.value } } : {})
    })
    else await createDept({ name: name.value, parentId: parentId.value, sortOrder: sortOrder.value })
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
        <NInput v-model:value="name" />
      </NFormItem>
      <NFormItem label="父部门">
        <NSelect v-model:value="parentId" :options="parentOptions" clearable />
      </NFormItem>
      <NFormItem label="排序">
        <NInputNumber v-model:value="sortOrder" :min="0" />
      </NFormItem>
      <NFormItem v-if="dept" label="状态">
        <NSelect v-model:value="status" :options="[{ label: '启用', value: 'ENABLED' }, { label: '禁用', value: 'DISABLED' }]" />
      </NFormItem>
      <NFormItem v-if="dept" label="部门负责人">
        <div class="manager-field">
          <NSelect v-model:value="managerId" :options="managerOptions" :loading="managerLoading"
            filterable remote clearable :input-props="{ 'aria-label': '部门负责人' }" placeholder="搜索本部门启用人员"
            @search="searchManagers" @update:value="managerChanged = true" />
          <small v-if="managerError" role="alert">{{ managerError }}</small>
        </div>
      </NFormItem>
      <NButton attr-type="submit" type="primary" :loading="busy">保存</NButton>
    </NForm>
  </NModal>
</template>

<style scoped>
.manager-field { width: 100%; }
.manager-field small { display: block; margin-top: 4px; color: #ac2338; }
</style>
