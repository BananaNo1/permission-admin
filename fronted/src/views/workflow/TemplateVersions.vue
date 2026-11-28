<script setup lang="ts">
import { ref, watch } from 'vue'
import { NAlert, NButton, NEmpty, NModal, NSpace, NTag } from 'naive-ui'
import { getTemplateVersion, listTemplateVersions, restoreTemplateVersion, setTemplateAvailability } from '@/api/workflowTemplate'
import type { TemplateVersion, TemplateVersionDetail } from '@/types/workflow'

const props = defineProps<{
  show: boolean; business: string; code: string; revision: number; publishedVersion: number;
  enabled: boolean; canRestore: boolean; canChangeAvailability: boolean;
}>()
const emit = defineEmits<{ 'update:show': [show: boolean]; changed: [] }>()
const versions = ref<TemplateVersion[]>([])
const selection = ref<number | null>(null)
const snapshot = ref<TemplateVersionDetail | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
let request = 0

const modes: Record<string, string> = { SINGLE: '单人审批', ANY: '或签', ALL: '会签', SEQUENTIAL: '依次审批' }
const message = (cause: unknown) => cause instanceof Error ? cause.message : '操作失败，请重试'

async function loadVersions() {
  const operation = ++request
  versions.value = []
  selection.value = null
  snapshot.value = null
  error.value = ''
  loading.value = true
  try {
    const rows = await listTemplateVersions(props.business, props.code)
    if (operation === request) versions.value = rows
  } catch (cause) { if (operation === request) error.value = message(cause) }
  finally { if (operation === request) loading.value = false }
}

async function inspect(version: number) {
  const operation = ++request
  selection.value = version
  snapshot.value = null
  error.value = ''
  loading.value = true
  try {
    const result = await getTemplateVersion(props.business, props.code, version)
    if (operation === request) snapshot.value = result
  } catch (cause) { if (operation === request) error.value = message(cause) }
  finally { if (operation === request) loading.value = false }
}

async function change(action: 'restore' | 'availability') {
  if (saving.value || (action === 'restore' && (!props.canRestore || !selection.value || !snapshot.value))
      || (action === 'availability' && !props.canChangeAvailability)) return
  saving.value = true
  error.value = ''
  try {
    if (action === 'restore') {
      await restoreTemplateVersion(props.business, props.code, selection.value!, props.revision)
    } else {
      await setTemplateAvailability(props.business, props.code, props.revision, !props.enabled)
    }
    emit('update:show', false)
    emit('changed')
  } catch (cause) { error.value = message(cause) }
  finally { saving.value = false }
}

watch(() => props.show, show => {
  if (show) void loadVersions()
  else request++
})
</script>

<template>
  <NModal :show="show" preset="card" title="模板版本" style="width: min(760px, calc(100vw - 32px))"
    :mask-closable="!saving" :close-on-esc="!saving" :closable="!saving"
    @update:show="value => { if (!saving) emit('update:show', value) }">
    <NSpace align="center" justify="space-between">
      <NTag size="small" :type="enabled ? 'success' : 'warning'">{{ enabled ? '接受新申请' : '已停用' }}</NTag>
      <NButton v-if="canChangeAvailability" :loading="saving" @click="change('availability')">
        {{ enabled ? '停用新申请' : '启用新申请' }}
      </NButton>
    </NSpace>
    <p>启停仅影响新申请，在途审批继续使用已绑定的发布版本。</p>
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <p v-if="loading" role="status">正在加载版本</p>
    <NEmpty v-if="!versions.length && !loading && !error" description="暂无发布记录" />
    <ul v-else class="version-list">
      <li v-for="item in versions" :key="item.version">
        <span><strong>v{{ item.version }} · {{ item.name }}</strong>
          <small>{{ new Date(item.publishedAt).toLocaleString('zh-CN') }} · 发布人 {{ item.publishedBy }}</small>
        </span>
        <NSpace align="center">
          <NTag v-if="item.version === publishedVersion" size="small">当前发布</NTag>
          <NButton size="small" :disabled="saving" :aria-pressed="selection === item.version"
            @click="inspect(item.version)">查看 v{{ item.version }}</NButton>
        </NSpace>
      </li>
    </ul>
    <section v-if="snapshot" class="version-snapshot" :aria-label="'版本 v' + selection + ' 配置'">
      <h3>v{{ selection }} · {{ snapshot.name }}</h3>
      <ol>
        <li v-for="(step, index) in snapshot.steps" :key="index">
          {{ step.name }} · {{ modes[step.mode] ?? step.mode }} · {{ step.approverIds.length }} 位审批人
          <small v-if="step.minAmount !== null">最低金额 {{ step.minAmount }}</small>
        </li>
      </ol>
      <NButton v-if="canRestore && selection !== publishedVersion" type="primary" :loading="saving"
        @click="change('restore')">复制为待发布草稿</NButton>
      <p v-else-if="!canRestore && selection !== publishedVersion">需要编辑权限，且当前没有未发布修改，才能复制历史版本。</p>
    </section>
  </NModal>
</template>

<style scoped>
.version-list { list-style: none; margin: 12px 0; padding: 0; }
.version-list > li { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; border-block-end: 1px solid #dce5f2; padding-block: 10px; }
.version-list small { display: block; color: #495d78; }
.version-snapshot { border: 1px solid #dce5f2; border-radius: 8px; padding: 12px; overflow-wrap: anywhere; }
.version-snapshot h3 { margin: 0; }
.version-snapshot small { margin-inline-start: 8px; color: #495d78; }
</style>
