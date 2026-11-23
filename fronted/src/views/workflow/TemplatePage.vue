<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NSelect, NSpin, NTag } from 'naive-ui'
import { api } from '@/api/http'
import { useAuth } from '@/stores/auth'
import TemplateEditor from './TemplateEditor.vue'
import BusinessGrants from './BusinessGrants.vue'

type Business = { code: string; name: string; edit: boolean; publish: boolean; start: boolean }
type Step = {
  name: string
  mode: 'SINGLE' | 'ANY' | 'ALL' | 'SEQUENTIAL'
  approverIds: number[]
  minAmount: number | null
  returnTo: 'APPLICANT' | 'PREVIOUS'
}
type Draft = { code: string; name: string; revision: number; publishedVersion: number; dirty: boolean; steps: Step[] }

const auth = useAuth()
const businesses = ref<Business[]>([])
const selected = ref<string | null>(null)
const business = computed(() => businesses.value.find(item => item.code === selected.value))
const options = computed(() => businesses.value.map(item => ({ label: item.name, value: item.code })))
const drafts = ref<Draft[]>([])
const current = ref<string | null>(null)
const creating = ref(false)
const draft = computed(() => drafts.value.find(item => item.code === current.value) ?? null)
const loading = ref(false)
const error = ref('')
const managingGrants = ref(false)
let requestId = 0

async function loadBusinesses() {
  try {
    error.value = ''
    businesses.value = await api<Business[]>('/workflow/businesses')
    if (!businesses.value.some(item => item.code === selected.value)) {
      selected.value = businesses.value[0]?.code ?? null
    }
  } catch (e) { error.value = e instanceof Error ? e.message : '业务列表加载失败' }
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
    const items = await api<Draft[]>('/workflow/businesses/' + encodeURIComponent(scope.code) + '/templates')
    if (request !== requestId) return
    drafts.value = items
    current.value = items.find(item => item.code === preferred)?.code ?? items[0]?.code ?? null
  } catch (e) {
    if (request === requestId) error.value = e instanceof Error ? e.message : '模板加载失败'
  } finally { if (request === requestId) loading.value = false }
}

function selectDraft(code: string | null) {
  current.value = code
  creating.value = code === null
}

watch(business, () => loadDrafts())
onMounted(loadBusinesses)
</script>

<template>
  <section class="workflow-page">
    <header class="workflow-heading"><h1>流程模板</h1>
      <NButton v-if="auth.me?.admin" @click="managingGrants = !managingGrants">
        {{ managingGrants ? '返回模板配置' : '业务与授权' }}
      </NButton>
    </header>
    <label class="workflow-filter">业务范围
      <NSelect v-model:value="selected" :options="options" placeholder="选择业务" />
    </label>
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <BusinessGrants v-if="auth.me?.admin && (managingGrants || !businesses.length)"
      :business="selected ?? ''" @changed="loadBusinesses" />
    <NEmpty v-else-if="!business" description="尚未分配业务范围" />
    <NEmpty v-else-if="!business.edit && !business.publish" description="当前业务仅授予了发起权限" />
    <NSpin v-else :show="loading">
      <div class="workflow-columns">
        <NCard title="业务模板" size="small">
          <NButton v-if="business.edit" block dashed @click="selectDraft(null)">新建模板</NButton>
          <ul class="workflow-list">
            <li v-for="item in drafts" :key="item.code">
              <button :aria-current="current === item.code ? 'true' : undefined" @click="selectDraft(item.code)">
                <strong>{{ item.name }}</strong><small>{{ item.code }}</small>
              </button>
              <NTag size="small" :bordered="false">{{ item.dirty ? '草稿' : '已发布' }}</NTag>
            </li>
          </ul>
        </NCard>
        <TemplateEditor v-if="draft || creating" :key="[business.code,current,draft?.revision].join('/')"
          :business="business.code" :template="draft" :edit="business.edit" :publish="business.publish"
          @saved="loadDrafts" />
        <NEmpty v-else-if="!loading" description="该业务暂无流程模板" />
      </div>
    </NSpin>
  </section>
</template>

<style scoped>
.workflow-page { display: grid; gap: 18px; }
.workflow-heading { display: flex; align-items: center; justify-content: space-between; }
.workflow-heading h1 { margin: 0; }
.workflow-filter { max-width: 340px; display: grid; gap: 6px; font-weight: 600; }
.workflow-columns { display: grid; grid-template-columns: minmax(190px, 260px) minmax(0, 1fr); align-items: start; gap: 18px; }
.workflow-list { list-style: none; margin: 12px 0 0; padding: 0; }
.workflow-list li { display: flex; align-items: center; gap: 6px; border-top: 1px solid #e4e9ef; padding: 6px 0; }
.workflow-list button { flex: 1; min-width: 0; display: grid; gap: 3px; text-align: left; padding: 8px; border: 0; background: transparent; color: inherit; cursor: pointer; overflow-wrap: anywhere; }
.workflow-list button[aria-current] { background: #edf3fa; border-radius: 5px; }
.workflow-list small { color: #56677a; }
@media (max-width: 800px) { .workflow-columns { grid-template-columns: 1fr; } }
</style>
