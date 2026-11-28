<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NAlert, NButton, NCheckbox, NEmpty, NInput, NModal, NSpace, NSpin, NTag } from 'naive-ui'
import { listRoles } from '@/api/role'
import { assignUserRoles, listUserRoleIds } from '@/api/user'
import type { RoleVO } from '@/types/role'
import type { UserVO } from '@/types/user'
import AssignmentReview from '@/components/assignment/AssignmentReview.vue'

const props = defineProps<{ user: UserVO }>()
const emit = defineEmits<{ saved: []; close: [] }>()
const roles = ref<RoleVO[]>([])
const originalIds = ref<string[]>([])
const ids = ref<string[]>([])
const keyword = ref('')
const selectedOnly = ref(false)
const busy = ref(false)
const loadState = ref<'loading' | 'ready' | 'error'>('loading')
const scopeLabels: Record<string, string> = {
  ALL: '全部', DEPT: '本部门', DEPT_AND_CHILDREN: '本部门及下级', SELF: '本人',
}

const selected = computed(() => new Set(ids.value))
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return roles.value.filter(role => (!selectedOnly.value || selected.value.has(role.id))
      && [role.name, role.code].some(value => value.toLowerCase().includes(query)))
})
const bulkIds = computed(() => filtered.value
    .filter(role => role.status === 'ENABLED' && role.code !== 'SUPER_ADMIN')
    .map(role => role.id))
const labels = computed(() => new Map(roles.value.map(role => [role.id, `${role.name} · ${role.code}`])))
const entries = (keys: string[]) => keys.map(id => ({ id, label: labels.value.get(id) ?? id }))
const added = computed(() => entries(ids.value.filter(id => !originalIds.value.includes(id))))
const removed = computed(() => entries(originalIds.value.filter(id => !selected.value.has(id))))
const hasChanges = computed(() => added.value.length > 0 || removed.value.length > 0)

function setSelected(keys: string[], checked: boolean) {
  const next = new Set(ids.value)
  for (const key of keys) {
    if (checked) next.add(key)
    else next.delete(key)
  }
  ids.value = [...next]
}

async function load() {
  loadState.value = 'loading'
  try {
    const [availableRoles, assignedIds] = await Promise.all([listRoles(), listUserRoleIds(props.user.id)])
    roles.value = availableRoles
    originalIds.value = assignedIds
    ids.value = [...assignedIds]
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

async function save() {
  if (loadState.value !== 'ready' || busy.value || !hasChanges.value) return
  busy.value = true
  try {
    await assignUserRoles(props.user.id, { roleIds: ids.value, version: props.user.version })
    emit('saved')
  } catch { /* 保留当前选择，由公共请求层显示错误。 */
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <NModal :show="true" preset="card" title="分配用户角色" style="width: min(960px, calc(100vw - 32px))"
          :mask-closable="false" :closable="!busy" :close-on-esc="!busy"
          @update:show="value => { if (!value && !busy) emit('close') }">
    <div class="assignment-subject">
      <span>用户</span><strong>{{ user.username }}</strong>
      <span v-if="user.nickname">{{ user.nickname }}</span>
    </div>
    <NSpin v-if="loadState === 'loading'">正在加载角色</NSpin>
    <NAlert v-else-if="loadState === 'error'" type="error" title="角色加载失败">
      <NButton size="small" @click="load">重试</NButton>
    </NAlert>
    <div v-else class="assignment-grid">
      <section class="assignment-panel" aria-label="角色列表">
        <h3>角色列表</h3>
        <NInput v-model:value="keyword" clearable placeholder="搜索角色名称或编码"
                :input-props="{ 'aria-label': '搜索角色' }" />
        <NSpace class="assignment-controls" align="center" :size="8">
          <NCheckbox v-model:checked="selectedOnly">仅看已选</NCheckbox>
          <NButton size="small" :disabled="busy || !bulkIds.some(id => !selected.has(id))"
                   @click="setSelected(bulkIds, true)">勾选筛选结果
          </NButton>
          <NButton size="small" :disabled="busy || !bulkIds.some(id => selected.has(id))"
                   @click="setSelected(bulkIds, false)">取消筛选结果
          </NButton>
        </NSpace>
        <p class="assignment-hint">批量操作仅包含筛选结果中的普通启用角色。</p>
        <div class="assignment-list">
          <NEmpty v-if="!filtered.length" description="未找到角色" />
          <div v-for="role in filtered" :key="role.id" class="assignment-row">
            <NCheckbox :checked="selected.has(role.id)" :disabled="busy"
                       @update:checked="checked => setSelected([role.id], checked)">
              <strong>{{ role.name }}</strong>
              <span class="assignment-code">{{ role.code }} · {{ scopeLabels[role.dataScope] ?? role.dataScope }}</span>
            </NCheckbox>
            <NTag v-if="role.status !== 'ENABLED'" size="small">已禁用</NTag>
            <NTag v-else-if="role.code === 'SUPER_ADMIN'" size="small" type="warning">高权限</NTag>
          </div>
        </div>
      </section>
      <AssignmentReview :selected="entries(ids)" :added="added" :removed="removed" />
    </div>
    <NSpace class="assignment-footer" justify="end">
      <NButton :disabled="busy" @click="emit('close')">取消</NButton>
      <NButton type="primary" :loading="busy" :disabled="loadState !== 'ready' || !hasChanges" @click="save">保存角色
      </NButton>
    </NSpace>
  </NModal>
</template>

<style scoped src="@/components/assignment/assignment-dialog.css"></style>
