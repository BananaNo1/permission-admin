<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NModal, NPagination, NSpace, NTag } from 'naive-ui'
import { api } from '@/api/http'
import type { PageResult } from '@/types/common'

type Business = { code: string; name: string; start: boolean }
type Release = { code: string; name: string; version: number }
type Application = { id: string; title: string; description?: string; status: string; businessKey?: string; templateVersion?: number; startTime: string }
type Task = { id: string; title: string; description?: string; nodeName: string; type: 'REVIEW' | 'RESUBMIT'; returnAllowed: boolean; createTime: string; formKey?: string | null; nodeEnteredAt?: string | null }
type RecordedAction = { taskId: string; actorId: string; action: string; comment?: string; time: string; listenerCompletedAt?: string | null }
type Decision = 'APPROVE' | 'REJECT' | 'RETURN'
type View = 'start' | 'applications' | 'tasks'

const props = defineProps<{ businesses: Business[]; preferredBusiness: string | null }>()
const emit = defineEmits<{ close: [] }>()
const available = computed(() => props.businesses.filter(item => item.start))
const business = ref(available.value.find(item => item.code === props.preferredBusiness)?.code ?? available.value[0]?.code ?? '')
const view = ref<View>(available.value.length ? 'start' : 'tasks')
const releases = ref<Release[]>([])
const template = ref('')
const businessKey = ref('')
const title = ref('')
const description = ref('')
const amount = ref('0')
const task = ref<Task | null>(null)
const decision = ref<Decision>('APPROVE')
const comment = ref('')
const taskTitle = ref('')
const taskDescription = ref('')
const detail = ref<Application | null>(null)
const actions = ref<RecordedAction[]>([])
const actionTotal = ref(0)
const actionPage = ref(1)
const applications = ref<Application[]>([])
const tasks = ref<Task[]>([])
const page = ref(1)
const total = ref(0)
const pageSize = 10
const loading = ref(false)
const loadingHistory = ref(false)
const saving = ref(false)
const error = ref('')
const dialogError = ref('')
const confirmation = ref('')
let listRequest = 0
let historyRequest = 0

const labels: Record<string, string> = {
  PENDING: '审批中', RETURNED: '已退回', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已取消',
  APPROVE: '通过', REJECT: '驳回', RETURN: '退回', RESUBMIT: '重新提交',
}
const segment = encodeURIComponent
const templatePath = (code: string) => '/workflow/businesses/' + segment(code) + '/templates'
const message = (cause: unknown) => cause instanceof Error ? cause.message : '请求失败，请重试'

async function load() {
  const request = ++listRequest
  const tab = view.value
  error.value = ''
  loading.value = true
  try {
    if (tab === 'start') {
      const items = business.value ? await api<Release[]>(templatePath(business.value) + '/published') : []
      if (request !== listRequest) return
      releases.value = items
      if (!items.some(item => item.code === template.value)) template.value = items[0]?.code ?? ''
    } else {
      const result = await api<PageResult<Application | Task>>('/workflow/' + tab + '?page=' + page.value + '&pageSize=' + pageSize)
      if (request !== listRequest) return
      total.value = result.total
      if (tab === 'applications') applications.value = result.records as Application[]
      else tasks.value = result.records as Task[]
    }
  } catch (cause) {
    if (request === listRequest) error.value = message(cause)
  } finally {
    if (request === listRequest) loading.value = false
  }
}

function switchView(next: View) {
  if (saving.value || next === view.value) return
  page.value = 1
  total.value = 0
  view.value = next
}

function changeBusiness() {
  releases.value = []
  template.value = ''
}

async function start() {
  if (saving.value || loading.value || !available.value.some(item => item.code === business.value)
      || !releases.value.some(item => item.code === template.value)
      || !title.value.trim() || !businessKey.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    await api(templatePath(business.value) + '/' + segment(template.value) + '/applications', 'POST', {
      title: title.value.trim(), businessKey: businessKey.value.trim(), description: description.value.trim(), amount: amount.value,
    })
    title.value = ''
    businessKey.value = ''
    description.value = ''
    amount.value = '0'
    confirmation.value = '申请已提交'
    page.value = 1
    view.value = 'applications'
  } catch (cause) { error.value = message(cause) }
  finally { saving.value = false }
}

function openTask(item: Task) {
  dialogError.value = ''
  task.value = item
  decision.value = 'APPROVE'
  comment.value = ''
  taskTitle.value = item.title
  taskDescription.value = item.description ?? ''
}

async function handleTask() {
  const item = task.value
  if (!item || saving.value || (item.type === 'RESUBMIT' && !taskTitle.value.trim())) return
  saving.value = true
  dialogError.value = ''
  try {
    const path = '/workflow/tasks/' + segment(item.id)
    if (item.type === 'RESUBMIT') {
      await api(path + '/resubmit', 'POST', { title: taskTitle.value.trim(), description: taskDescription.value.trim() })
    } else {
      await api(path + '/decision', 'POST', { action: decision.value, comment: comment.value.trim() })
    }
    task.value = null
    confirmation.value = item.type === 'RESUBMIT' ? '申请已重新提交' : '待办已办理'
    if (tasks.value.length === 1 && page.value > 1) page.value--
    else await load()
  } catch (cause) { dialogError.value = message(cause) }
  finally { saving.value = false }
}

async function loadHistory() {
  const request = ++historyRequest
  const item = detail.value
  if (!item) return
  loadingHistory.value = true
  dialogError.value = ''
  try {
    const result = await api<PageResult<RecordedAction>>('/workflow/applications/' + segment(item.id)
      + '/actions?page=' + actionPage.value + '&pageSize=' + pageSize)
    if (request !== historyRequest) return
    actions.value = result.records
    actionTotal.value = result.total
  } catch (cause) {
    if (request === historyRequest) dialogError.value = message(cause)
  } finally {
    if (request === historyRequest) loadingHistory.value = false
  }
}

function openHistory(item: Application) {
  actions.value = []
  actionTotal.value = 0
  actionPage.value = 1
  detail.value = item
}

watch([view, business, page], load, { immediate: true })
watch([detail, actionPage], loadHistory)
</script>

<template>
  <section class="workbench">
    <header>
      <h1>审批工作台</h1>
      <NButton :disabled="saving" @click="emit('close')">返回流程模板</NButton>
    </header>
    <NSpace>
      <NButton v-if="available.length" :type="view === 'start' ? 'primary' : 'default'" @click="switchView('start')">发起审批</NButton>
      <NButton :type="view === 'applications' ? 'primary' : 'default'" @click="switchView('applications')">我的申请</NButton>
      <NButton :type="view === 'tasks' ? 'primary' : 'default'" @click="switchView('tasks')">我的待办</NButton>
      <NButton :disabled="saving" :loading="loading" @click="load">刷新</NButton>
    </NSpace>
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <NAlert v-if="confirmation" type="success" closable @close="confirmation = ''">{{ confirmation }}</NAlert>

    <NCard v-if="view === 'start'" title="按已发布模板发起">
      <form class="workbench-form" @submit.prevent="start">
        <label>业务范围<select v-model="business" required :disabled="saving" @change="changeBusiness">
          <option v-for="item in available" :key="item.code" :value="item.code">{{ item.name }}</option>
        </select></label>
        <label>审批模板<select v-model="template" required :disabled="loading || saving">
          <option v-if="!releases.length" value="">暂无已发布模板</option>
          <option v-for="item in releases" :key="item.code" :value="item.code">{{ item.name }} · v{{ item.version }}</option>
        </select></label>
        <label>业务单号<input v-model="businessKey" required maxlength="120" :disabled="saving" /></label>
        <label>申请标题<input v-model="title" required maxlength="120" :disabled="saving" /></label>
        <label>金额<input v-model="amount" required inputmode="decimal" pattern="[0-9]{1,16}([.][0-9]{1,2})?" :disabled="saving" /></label>
        <label>申请说明<textarea v-model="description" maxlength="1000" :disabled="saving"></textarea></label>
        <div><NButton type="primary" attr-type="submit" :loading="saving" :disabled="loading || !template">提交申请</NButton></div>
      </form>
    </NCard>

    <NCard v-else :title="view === 'applications' ? '我的申请' : '我的待办'">
      <p v-if="loading" role="status">正在加载</p>
      <NEmpty v-else-if="!error && !total" :description="view === 'applications' ? '暂无申请' : '暂无待办'" />
      <ul v-else-if="!error" class="workbench-list">
        <template v-if="view === 'applications'">
          <li v-for="item in applications" :key="item.id">
            <div><strong>{{ item.title }}</strong><small v-if="item.businessKey">单号 {{ item.businessKey }} · 模板 v{{ item.templateVersion }}</small></div>
            <NSpace align="center">
              <NTag size="small">{{ labels[item.status] ?? item.status }}</NTag>
              <NButton size="small" @click="openHistory(item)">审批记录</NButton>
            </NSpace>
          </li>
        </template>
        <template v-else>
          <li v-for="item in tasks" :key="item.id">
            <div><strong>{{ item.title }}</strong><small>{{ item.nodeName }}</small><small v-if="item.nodeEnteredAt">节点进入时间 {{ new Date(item.nodeEnteredAt).toLocaleString('zh-CN') }}</small></div>
            <NButton size="small" type="primary" secondary @click="openTask(item)">{{ item.type === 'RESUBMIT' ? '修改并重提' : '办理待办' }}</NButton>
          </li>
        </template>
      </ul>
      <NPagination v-if="total > pageSize" v-model:page="page" :page-size="pageSize" :item-count="total" />
    </NCard>

    <NModal :show="task !== null" preset="card" :title="task?.type === 'RESUBMIT' ? '修改并重提' : '办理待办'"
      :mask-closable="false" :close-on-esc="!saving" :closable="!saving" style="width: min(560px, calc(100vw - 32px))"
      @update:show="show => { if (!show && !saving) task = null }">
      <form v-if="task" class="workbench-form" @submit.prevent="handleTask">
        <NAlert v-if="dialogError" type="error">{{ dialogError }}</NAlert>
        <template v-if="task.type === 'RESUBMIT'">
          <label>申请标题<input v-model="taskTitle" required maxlength="120" :disabled="saving" /></label>
          <label>申请说明<textarea v-model="taskDescription" maxlength="1000" :disabled="saving"></textarea></label>
        </template>
        <template v-else>
          <div><strong>{{ task.title }}</strong><p v-if="task.description">{{ task.description }}</p></div>
          <label>办理结果<select v-model="decision" :disabled="saving">
            <option value="APPROVE">通过</option><option value="REJECT">驳回</option>
            <option v-if="task.returnAllowed" value="RETURN">退回</option>
          </select></label>
          <label>审批意见 <span v-if="task.formKey === 'required-opinion'">（必填）</span><textarea v-model="comment" maxlength="1000" :required="task.formKey === 'required-opinion'" :disabled="saving"></textarea></label>
        </template>
        <div><NButton type="primary" attr-type="submit" :loading="saving">确认提交</NButton></div>
      </form>
    </NModal>

    <NModal :show="detail !== null" preset="card" title="审批记录" style="width: min(620px, calc(100vw - 32px))"
      @update:show="show => { if (!show) detail = null }">
      <p v-if="detail"><strong>{{ detail.title }}</strong> · {{ labels[detail.status] ?? detail.status }}</p>
      <p v-if="detail?.description">{{ detail.description }}</p>
      <NAlert v-if="dialogError" type="error">{{ dialogError }}</NAlert>
      <p v-else-if="loadingHistory">正在加载审批记录</p>
      <NEmpty v-else-if="!actions.length" description="暂无审批记录" />
      <ul v-else class="workbench-list">
        <li v-for="item in actions" :key="item.taskId">
          <div><strong>{{ labels[item.action] ?? item.action }}</strong><small>办理人 ID {{ item.actorId }} · {{ new Date(item.time).toLocaleString('zh-CN') }}</small></div>
          <div><span v-if="item.comment">{{ item.comment }}</span><small v-if="item.listenerCompletedAt">任务监听器记录 {{ new Date(item.listenerCompletedAt).toLocaleString('zh-CN') }}</small></div>
        </li>
      </ul>
      <NPagination v-if="actionTotal > pageSize" v-model:page="actionPage" :page-size="pageSize" :item-count="actionTotal" />
    </NModal>
  </section>
</template>

<style scoped>
.workbench, .workbench-form { display: grid; gap: 14px; }
.workbench > header, .workbench-list > li { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; }
.workbench h1 { margin: 0; }
.workbench-form { max-width: 560px; }
.workbench-form label { display: grid; gap: 5px; }
.workbench-form input, .workbench-form select, .workbench-form textarea {
  width: 100%; min-height: 36px; padding: 6px 9px; border: 1px solid #aebcd0; border-radius: 6px; background: #fff; color: inherit; font: inherit;
}
.workbench-form textarea { min-height: 76px; resize: vertical; }
.workbench-list { list-style: none; margin: 0 0 12px; padding: 0; }
.workbench-list > li { border-bottom: 1px solid #e2eaf5; padding: 10px 0; overflow-wrap: anywhere; }
.workbench-list small { display: block; color: #495d78; }
</style>
