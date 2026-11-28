<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NInput, NModal, NSelect, NSpin, NTag } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { listWorkflowBusinesses } from '@/api/workflowBusiness'
import { listWorkflowTemplates } from '@/api/workflowTemplate'
import type { WorkflowBusiness, TemplateDraft } from '@/types/workflow'
import { useAuth } from '@/stores/auth'
import TemplateEditor from './TemplateEditor.vue'
import BusinessGrants from './BusinessGrants.vue'
import ApprovalWorkbench from './ApprovalWorkbench.vue'
const auth = useAuth()
const route = useRoute()
const router = useRouter()
const businesses = ref<WorkflowBusiness[]>([])
const selected = ref<string | null>(null)
const business = computed(() => businesses.value.find(item => item.code === selected.value))
const options = computed(() => businesses.value.map(item => ({ label: item.name, value: item.code })))
const drafts = ref<TemplateDraft[]>([])
const current = ref<string | null>(null)
const creating = ref(false)
const draft = computed(() => drafts.value.find(item => item.code === current.value) ?? null)
const designing = computed(() => creating.value || draft.value !== null)
const search = ref('')
const visibleDrafts = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return drafts.value.filter(item => !keyword || [item.name, item.code].some(value => value.toLowerCase().includes(keyword)))
})
const loadingBusinesses = ref(true)
const loading = ref(false)
const error = ref('')
const managingGrants = ref(false)
const workbench = ref(false)
const firstBusinessDialog = ref(false)
const grants = ref<{ focusBusinessCode: () => void } | null>(null)
let requestId = 0

async function loadBusinesses(preferred = selected.value) {
  loadingBusinesses.value = true
  try {
    error.value = ''
    businesses.value = await listWorkflowBusinesses()
    selected.value = businesses.value.find(item => item.code === preferred)?.code ?? businesses.value[0]?.code ?? null
  } catch (e) { error.value = e instanceof Error ? e.message : '业务列表加载失败' }
  finally { loadingBusinesses.value = false }
}

async function businessCreated(code: string) {
  await loadBusinesses(code)
  if (selected.value !== code) return
  managingGrants.value = false
  firstBusinessDialog.value = false
  await nextTick()
  if (selected.value === code && business.value?.edit) selectDraft(null)
}

async function loadDrafts(preferred?: string) {
  const request = ++requestId
  const scope = business.value
  drafts.value = []
  current.value = null
  creating.value = false
  loading.value = false
  error.value = ''
  if (!scope || !(scope.edit || scope.publish)) return
  loading.value = true
  try {
    const items = await listWorkflowTemplates(scope.code)
    if (request !== requestId) return
    drafts.value = items
    if (!creating.value) current.value = items.find(item => item.code === preferred)?.code ?? null
  } catch (e) {
    if (request === requestId) error.value = e instanceof Error ? e.message : '模板加载失败'
  } finally { if (request === requestId) loading.value = false }
}

function selectDraft(code: string | null) {
  current.value = code
  creating.value = code === null
  void router.replace({ query: { ...route.query, business: code ? selected.value : undefined, template: code ?? undefined } })
}

function backToList() {
  current.value = null
  creating.value = false
  managingGrants.value = false
  void router.replace({ query: { ...route.query, business: undefined, template: undefined } })
}

async function draftSaved(code: string) {
  await loadDrafts(code)
  if (current.value === code) selectDraft(code)
}

function createTemplate() {
  if (business.value?.edit) selectDraft(null)
  else firstBusinessDialog.value = true
}

watch(business, scope => {
  search.value = ''
  const preferred = scope?.code === route.query.business && typeof route.query.template === 'string'
    ? route.query.template : undefined
  void loadDrafts(preferred)
})
onMounted(() => loadBusinesses(typeof route.query.business === 'string' ? route.query.business : null))
</script>

<template>
  <ApprovalWorkbench v-if="workbench" :businesses="businesses" :preferred-business="selected" @close="workbench = false" />
  <section v-else class="workflow-page">
    <header class="workflow-heading">
      <div class="heading-copy">
        <div v-if="designing || managingGrants" class="breadcrumbs">
          <NButton text @click="backToList">← 返回模板列表</NButton>
          <span v-if="business">/ {{ business.name }}</span>
        </div>
        <h1>{{ designing ? (draft?.name ?? '新建流程模板') : managingGrants ? '业务与授权' : '流程模板' }}</h1>
        <p v-if="designing && draft" class="subtle">{{ draft.code }} · {{ draft.publishedVersion ? '已发布 v' + draft.publishedVersion : '未发布' }}</p>
      </div>
      <div class="workflow-actions">
        <NButton v-if="!designing && !managingGrants" :disabled="loadingBusinesses" @click="workbench = true">审批工作台</NButton>
        <NButton v-if="auth.me?.admin && businesses.length && !designing && !managingGrants" @click="managingGrants = true">业务与授权</NButton>
        <NButton v-if="!designing && !managingGrants && (business?.edit || (auth.me?.admin && !businesses.length && !error))"
          type="primary" :disabled="loading || loadingBusinesses" @click="createTemplate">新建模板</NButton>
      </div>
    </header>
    <div v-if="businesses.length && !designing" class="workflow-filters">
      <label class="workflow-filter">业务范围
        <NSelect v-model:value="selected" :options="options" placeholder="选择业务" />
      </label>
      <label v-if="!managingGrants && drafts.length" class="workflow-filter">搜索模板
        <NInput v-model:value="search" clearable placeholder="输入模板名称或编码" aria-label="搜索模板" />
      </label>
    </div>
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <NSpin v-if="loadingBusinesses && !businesses.length" />
    <template v-else-if="auth.me?.admin && !businesses.length">
      <NAlert v-if="!error" type="info">请先创建业务范围，再新建流程模板。</NAlert>
      <NModal v-model:show="firstBusinessDialog" preset="card" title="创建业务范围"
        style="width: min(580px, calc(100vw - 32px))" @after-enter="grants?.focusBusinessCode()">
        <BusinessGrants ref="grants" business="" @changed="businessCreated" />
      </NModal>
    </template>
    <BusinessGrants v-else-if="auth.me?.admin && managingGrants" :business="selected ?? ''" @changed="businessCreated" />
    <NEmpty v-else-if="!business" description="尚未分配业务范围，请联系超级管理员授权" />
    <NEmpty v-else-if="!business.edit && !business.publish" description="当前业务仅授予了发起权限，请联系超级管理员授予编辑权限" />
    <NSpin v-else :show="loading">
      <TemplateEditor v-if="designing" :key="[business.code, current, draft?.revision].join('/')"
        :business="business.code" :template="draft" :edit="business.edit" :publish="business.publish"
        @saved="draftSaved" />
      <section v-else class="template-list" aria-label="业务模板">
        <header class="list-header">
          <h2>业务模板 <span>{{ drafts.length }}</span></h2>
          <small v-if="!business.edit" class="subtle">当前业务未授予编辑权限</small>
        </header>
        <ul v-if="visibleDrafts.length" class="workflow-list">
          <li v-for="item in visibleDrafts" :key="item.code">
            <NCard size="small" class="template-card">
              <div class="card-heading">
                <h3>{{ item.name }}</h3>
                <NTag size="small" :type="item.dirty ? 'warning' : 'success'" :bordered="false">
                  {{ item.dirty ? '草稿待发布' : '已发布' }}
                </NTag>
              </div>
              <p class="subtle template-code">{{ item.code }}</p>
              <div class="card-footer">
                <small class="subtle">{{ item.steps.length }} 个节点 · {{ item.publishedVersion ? '已发布 v' + item.publishedVersion : '未发布' }}</small>
                <NButton size="small" type="primary" secondary :aria-label="(business.edit ? '设计 ' : '查看 ') + item.name"
                  @click="selectDraft(item.code)">{{ business.edit ? '设计流程' : '查看流程' }}</NButton>
              </div>
            </NCard>
            </li>
        </ul>
        <NCard v-else-if="!loading" class="empty-list">
          <NEmpty :description="drafts.length ? '没有匹配的模板' : '当前业务还没有流程模板'" />
        </NCard>
      </section>
    </NSpin>
  </section>
</template>

<style scoped>
.workflow-page { display: grid; gap: 20px; min-width: 0; }
.workflow-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }
.workflow-heading h1 { margin: 0; }
.workflow-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.breadcrumbs { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 8px; color: #526780; }
.heading-copy { min-width: 0; overflow-wrap: anywhere; }
.heading-copy p { margin: 5px 0 0; }
.subtle { color: #556983; }
.workflow-filters { display: flex; flex-wrap: wrap; gap: 12px 16px; background: #fff; padding: 14px 16px; border: 1px solid #dce5ef; border-radius: 10px; }
.workflow-filter { flex: 1 1 220px; max-width: 340px; display: grid; gap: 6px; font-weight: 600; }
.list-header { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 8px; margin: 0 0 12px; }
.list-header h2 { font-size: 17px; margin: 0; }
.list-header h2 span { margin-left: 5px; color: #5a6f87; font-size: 13px; font-weight: 500; }
.workflow-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(280px, 100%), 1fr)); gap: 14px; list-style: none; margin: 0; padding: 0; }
.template-card { height: 100%; }
.card-heading, .card-footer { display: flex; flex-wrap: wrap; gap: 8px; justify-content: space-between; align-items: center; }
.card-heading h3 { font-size: 16px; margin: 0; overflow-wrap: anywhere; }
.template-code { font-size: 13px; margin: 4px 0 14px; overflow-wrap: anywhere; }
.card-footer { padding-top: 12px; border-top: 1px solid #e6edf4; }
.empty-list { padding: 18px; }
</style>
