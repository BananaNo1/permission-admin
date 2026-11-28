<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NModal, NPagination, NSpace, NTag, NSelect } from 'naive-ui'
import {
  decideApprovalTask, getApprovalDetail, listCompletedTasks, listTransferCandidates, listWorkflowApplications,
  listWorkflowOperations, listWorkflowTasks, resubmitApprovalTask, terminateApprovalInstance,
  transferApprovalTask, withdrawApprovalInstance,
} from '@/api/workflowApproval'
import { listPublishedTemplates, startTemplateApplication } from '@/api/workflowTemplate'
import ApprovalDetail from './ApprovalDetail.vue'
import { dateTime, labels } from './approvalDetail'
import type { Application, CompletedTask, Decision, InstanceDetail, Task, WorkflowBusiness, WorkflowRelease } from '@/types/workflow'

type View = 'start' | 'applications' | 'tasks' | 'completed' | 'operations'
type Operation = 'transfer' | 'withdraw' | 'terminate'

const props = defineProps<{ businesses: WorkflowBusiness[]; preferredBusiness: string | null; initialView?: Exclude<View, 'start'> }>()
const emit = defineEmits<{ close: [] }>()
const available = computed(() => props.businesses.filter(item => item.start))
const business = ref(available.value.find(item => item.code === props.preferredBusiness)?.code ?? available.value[0]?.code ?? '')
const view = ref<View>(props.initialView ?? (available.value.length ? 'start' : 'tasks'))
const standalone = computed(() => props.initialView !== undefined)
const viewTitle = computed(() => ({ start: '发起审批', applications: '我的申请', tasks: '我的待办', completed: '我的已办', operations: '流程运维' })[view.value])
const emptyText = computed(() => ({ start: '', applications: '暂无申请', tasks: '暂无待办', completed: '暂无已办', operations: '暂无匹配的在途实例' })[view.value])
const releases = ref<WorkflowRelease[]>([])
const template = ref('')
const businessKey = ref('')
const title = ref('')
const description = ref('')
const amount = ref('0')
const task = ref<Task | null>(null)
const operation = ref<Operation | null>(null)
const operationLabels: Record<Operation, string> = { transfer: '转办', withdraw: '撤回申请', terminate: '终止实例' }
const operationReason = ref('')
const operationError = ref('')
const recipient = ref<string | null>(null)
const recipientOptions = ref<{ label: string; value: string }[]>([])
const recipientLoading = ref(false)
let recipientRequest = 0
const decision = ref<Decision>('APPROVE')
const comment = ref('')
const taskTitle = ref('')
const taskDescription = ref('')
const detailId = ref<string | null>(null)
const detail = ref<InstanceDetail | null>(null)
const detailError = ref('')
const actionPage = ref(1)
const applications = ref<Application[]>([])
const tasks = ref<Task[]>([])
const completed = ref<CompletedTask[]>([])
const keywordInput = ref('')
const filterBusiness = ref('')
const filterStatus = ref('')
const filters = ref({ keyword: '', business: '', status: '' })
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

function badgeType(status: string) {
  if (['APPROVED', 'APPROVE'].includes(status)) return 'success'
  if (['REJECTED', 'REJECT'].includes(status)) return 'error'
  if (['RETURNED', 'RETURN'].includes(status)) return 'warning'
  return status === 'PENDING' ? 'info' : 'default'
}

const statuses = ['PENDING', 'RETURNED', 'APPROVED', 'REJECTED', 'CANCELLED']
const message = (cause: unknown) => cause instanceof Error ? cause.message : '请求失败，请重试'

async function load() {
  const request = ++listRequest
  const tab = view.value
  error.value = ''
  loading.value = true
  try {
    if (tab === 'start') {
      const items = business.value ? await listPublishedTemplates(business.value) : []
      if (request !== listRequest) return
      releases.value = items
      if (!items.some(item => item.code === template.value)) template.value = items[0]?.code ?? ''
    } else {
      const query = { page: page.value, pageSize, ...filters.value }
      const list = {
        applications: listWorkflowApplications,
        tasks: listWorkflowTasks,
        completed: listCompletedTasks,
        operations: listWorkflowOperations,
      }[tab]
      const result = await list(query)
      if (request !== listRequest) return
      total.value = result.total
      if (tab === 'applications' || tab === 'operations') applications.value = result.records as Application[]
      else if (tab === 'tasks') tasks.value = result.records as Task[]
      else completed.value = result.records as CompletedTask[]
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
  if (next === 'start' && !available.value.some(item => item.code === business.value)) {
    business.value = available.value[0]?.code ?? ''
  }
  view.value = next
}

function applyFilters() {
  page.value = 1
  filters.value = { keyword: keywordInput.value.trim(), business: filterBusiness.value, status: filterStatus.value }
}

function resetFilters() {
  keywordInput.value = ''
  filterBusiness.value = ''
  filterStatus.value = ''
  applyFilters()
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
    await startTemplateApplication(business.value, template.value, {
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
    if (item.type === 'RESUBMIT') {
      await resubmitApprovalTask(item.id, { title: taskTitle.value.trim(), description: taskDescription.value.trim() })
    } else {
      await decideApprovalTask(item.id, decision.value, comment.value.trim())
    }
    task.value = null
    confirmation.value = item.type === 'RESUBMIT' ? '申请已重新提交' : '待办已办理'
    if (detailId.value) void loadHistory()
    if (view.value === 'tasks' && tasks.value.length === 1 && page.value > 1) page.value--
    else await load()
  } catch (cause) { dialogError.value = message(cause) }
  finally { saving.value = false }
}

function openOperation(action: Operation) {
  if ((action === 'transfer' && !task.value) || (action !== 'transfer' && !detailId.value)) return
  operation.value = action
  operationReason.value = ''
  operationError.value = ''
  recipient.value = null
  recipientOptions.value = []
  if (action === 'transfer') void searchRecipients('')
}

function closeOperation() {
  if (saving.value) return
  recipientRequest++
  operation.value = null
}

async function searchRecipients(keyword: string) {
  const item = task.value
  if (!item || operation.value !== 'transfer') return
  const request = ++recipientRequest
  recipientLoading.value = true
  operationError.value = ''
  try {
    const rows = await listTransferCandidates(item.id, keyword)
    if (request !== recipientRequest) return
    const selected = recipientOptions.value.find(option => option.value === recipient.value)
    const options = rows.map(row => ({ label: row.name + '（' + row.username + '）', value: String(row.id) }))
    recipientOptions.value = selected && !options.some(option => option.value === selected.value)
      ? [selected, ...options] : options
  } catch (cause) {
    if (request === recipientRequest) operationError.value = message(cause)
  } finally {
    if (request === recipientRequest) recipientLoading.value = false
  }
}

async function submitOperation() {
  const action = operation.value
  const reason = operationReason.value.trim()
  if (!action || saving.value) return
  if (action === 'transfer' && !recipient.value) {
    operationError.value = '请选择转办接收人'
    return
  }
  if (!reason) {
    operationError.value = '请填写操作原因'
    return
  }
  const id = action === 'transfer' ? task.value?.id : detailId.value
  if (!id) return
  saving.value = true
  operationError.value = ''
  try {
    if (action === 'transfer') await transferApprovalTask(id, recipient.value!, reason)
    else if (action === 'withdraw') await withdrawApprovalInstance(id, reason)
    else await terminateApprovalInstance(id, reason)
    if (action === 'transfer') task.value = null
    operation.value = null
    recipientRequest++
    confirmation.value = action === 'transfer' ? '任务已转办' : action === 'withdraw' ? '申请已撤回' : '实例已终止'
    if (detailId.value) void loadHistory()
    if (view.value === 'tasks' && tasks.value.length === 1 && page.value > 1) page.value--
    else await load()
  } catch (cause) { operationError.value = message(cause) }
  finally { saving.value = false }
}

async function loadHistory() {
  const request = ++historyRequest
  const id = detailId.value
  if (!id) return
  loadingHistory.value = true
  detailError.value = ''
  try {
    const result = await getApprovalDetail(id, actionPage.value, pageSize)
    if (request === historyRequest) detail.value = result
  } catch (cause) {
    if (request === historyRequest) {
      detail.value = null
      detailError.value = message(cause)
    }
  } finally {
    if (request === historyRequest) loadingHistory.value = false
  }
}

function openHistory(id: string) {
  detail.value = null
  actionPage.value = 1
  detailId.value = id
}

function closeDetail() {
  historyRequest++
  detailId.value = null
  detail.value = null
}

function openOwnTask(id: string) {
  const item = detail.value?.ownTasks.find(candidate => candidate.id === id)
  if (item) openTask(item)
}

watch([view, business, page, filters], load, { immediate: true })
watch([detailId, actionPage], loadHistory)
</script>

<template>
  <section class="workbench">
    <header>
      <h1>{{ standalone ? viewTitle : '审批工作台' }}</h1>
      <NButton v-if="!standalone" :disabled="saving" @click="emit('close')">返回流程模板</NButton>
      <NButton v-else-if="initialView === 'applications' && available.length"
        :disabled="saving" :type="view === 'start' ? 'default' : 'primary'"
        @click="switchView(view === 'start' ? 'applications' : 'start')">
        {{ view === 'start' ? '返回我的申请' : '发起审批' }}
      </NButton>
    </header>
    <NSpace v-if="!standalone">
      <NButton v-if="available.length" :type="view === 'start' ? 'primary' : 'default'" @click="switchView('start')">发起审批</NButton>
      <NButton v-if="!standalone" :type="view === 'applications' ? 'primary' : 'default'" @click="switchView('applications')">我的申请</NButton>
      <NButton v-if="!standalone" :type="view === 'tasks' ? 'primary' : 'default'" @click="switchView('tasks')">我的待办</NButton>
      <NButton v-if="!standalone" :type="view === 'completed' ? 'primary' : 'default'" @click="switchView('completed')">我的已办</NButton>
    </NSpace>
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <NAlert v-if="confirmation" type="success" role="status" closable @close="confirmation = ''">{{ confirmation }}</NAlert>

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

    <section v-else class="inbox-panel" :aria-label="viewTitle + '查询结果'">
      <form class="inbox-filters" aria-label="审批筛选" @submit.prevent="applyFilters">
        <label>关键词<input v-model="keywordInput" type="search" maxlength="120" placeholder="申请标题或单号" /></label>
        <label v-if="businesses.length">业务<select v-model="filterBusiness">
          <option value="">全部业务</option>
          <option v-for="item in businesses" :key="item.code" :value="item.code">{{ item.name }}</option>
        </select></label>
        <label v-if="view === 'applications' || view === 'operations'">状态<select v-model="filterStatus">
          <option value="">{{ view === 'operations' ? '全部在途' : '全部状态' }}</option>
          <option v-for="status in (view === 'operations' ? ['EXCEPTION'] : statuses)" :key="status" :value="status">{{ labels[status] }}</option>
        </select></label>
        <div class="inbox-filter-actions"><NButton class="inbox-button" type="primary" attr-type="submit">查询</NButton><NButton class="inbox-button" @click="resetFilters">重置</NButton></div>
      </form>
      <div class="inbox-toolbar">
        <h2>列表 <span v-if="!loading && !error">（{{ total }}）</span></h2>
        <NButton class="inbox-button" type="primary" ghost :disabled="saving" :loading="loading" @click="load">刷新列表</NButton>
      </div>
      <p v-if="loading" role="status" class="inbox-empty">正在加载</p>
      <NEmpty v-else-if="!error && !total" class="inbox-empty" :description="emptyText" />
      <div v-else-if="!error" class="inbox-table-region" role="region" :aria-label="viewTitle + '列表'" tabindex="0">
        <table class="inbox-table" :aria-label="viewTitle">
          <thead>
            <tr>
              <th scope="col">申请标题</th>
              <th scope="col" class="inbox-reference">{{ view === 'applications' || view === 'operations' ? '业务单号' : '审批节点' }}</th>
              <th v-if="view === 'applications' || view === 'operations'" scope="col" class="inbox-node">当前节点</th>
              <th v-if="view !== 'tasks'" scope="col" class="inbox-state">{{ view === 'completed' ? '办理结果' : '状态' }}</th>
              <th scope="col" class="inbox-time">{{ view === 'completed' ? '办理时间' : view === 'tasks' ? '待办时间' : '提交时间' }}</th>
              <th scope="col" class="inbox-action-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="view === 'applications' || view === 'operations'">
              <tr v-for="item in applications" :key="item.id">
                <th scope="row">{{ item.title }}</th>
                <td class="inbox-reference">
                  {{ item.businessKey || '—' }}
                </td>
                <td class="inbox-node">{{ item.currentNodeNames?.join('、') || '—' }}</td>
                <td class="inbox-state"><NTag size="small" :bordered="false" :type="badgeType(item.status)">{{ labels[item.status] ?? item.status }}</NTag></td>
                <td class="inbox-time">{{ item.startTime ? dateTime(item.startTime) : '—' }}</td>
                <td class="inbox-action-cell">
                  <NButton class="inbox-button" type="primary" ghost @click="openHistory(item.id)">审批记录</NButton>
                </td>
              </tr>
            </template>
            <template v-else-if="view === 'tasks'">
              <tr v-for="item in tasks" :key="item.id">
                <th scope="row">{{ item.title }}</th>
                <td class="inbox-reference">{{ item.nodeName || '—' }}</td>
                <td class="inbox-time">{{ item.nodeEnteredAt || item.createTime ? dateTime(item.nodeEnteredAt || item.createTime) : '—' }}</td>
                <td class="inbox-action-cell">
                  <div class="inbox-actions">
                    <NButton v-if="item.instanceId" class="inbox-button" type="primary" ghost @click="openHistory(item.instanceId)">查看详情</NButton>
                    <NButton class="inbox-button" type="primary" @click="openTask(item)">{{ item.type === 'RESUBMIT' ? '修改并重提' : '办理待办' }}</NButton>
                  </div>
                </td>
              </tr>
            </template>
            <template v-else>
              <tr v-for="item in completed" :key="item.taskId">
                <th scope="row">{{ item.title }}</th>
                <td class="inbox-reference">{{ item.nodeName || '—' }}</td>
                <td class="inbox-state"><NTag size="small" :bordered="false" :type="badgeType(item.action)">{{ labels[item.action] ?? item.action }}</NTag></td>
                <td class="inbox-time">{{ item.completedAt ? dateTime(item.completedAt) : '—' }}</td>
                <td class="inbox-action-cell">
                  <NButton class="inbox-button" type="primary" ghost @click="openHistory(item.instanceId)">查看详情</NButton>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <NPagination class="inbox-pagination" v-if="total > pageSize" v-model:page="page" :page-size="pageSize" :item-count="total" />
    </section>

    <NModal :show="task !== null" preset="card" :title="task?.type === 'RESUBMIT' ? '修改并重提' : '办理待办'"
      :mask-closable="false" :close-on-esc="!saving && operation === null" :closable="!saving && operation === null" style="width: min(560px, calc(100vw - 32px))"
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
        <NSpace>
          <NButton v-if="task.type === 'REVIEW'" :disabled="saving" @click="openOperation('transfer')">转办</NButton>
          <NButton type="primary" attr-type="submit" :loading="saving">确认提交</NButton>
        </NSpace>
      </form>
    </NModal>

    <NModal :show="detailId !== null" preset="card" title="审批详情" aria-label="审批详情"
      style="width: min(960px, calc(100vw - 24px))"
      :close-on-esc="task === null && operation === null" :mask-closable="task === null && operation === null"
      :closable="task === null && operation === null"
      @update:show="show => { if (!show && task === null && operation === null) closeDetail() }">
      <NAlert v-if="detailError" type="error">{{ detailError }}</NAlert>
      <p v-if="loadingHistory && !detail" role="status">正在加载审批详情</p>
      <ApprovalDetail v-else-if="detail" :detail="detail" :page="actionPage" :page-size="pageSize"
        :aria-busy="loadingHistory" @page="actionPage = $event" @task="openOwnTask"
        @withdraw="openOperation('withdraw')" @terminate="openOperation('terminate')" />
    </NModal>

    <NModal :show="operation !== null" preset="card" :title="operation ? operationLabels[operation] : ''"
      :mask-closable="false" :close-on-esc="!saving" :closable="!saving"
      style="width: min(540px, calc(100vw - 32px))"
      @update:show="show => { if (!show) closeOperation() }">
      <form v-if="operation" class="workbench-form" aria-label="确认流程操作" @submit.prevent="submitOperation">
        <NAlert v-if="operationError" type="error">{{ operationError }}</NAlert>
        <p v-if="operation === 'withdraw'">撤回后，本次申请的待办将关闭。</p>
        <p v-if="operation === 'terminate'">终止后，本次实例的所有未办理任务将关闭。</p>
        <div v-if="operation === 'transfer'">
          <span id="transfer-recipient-label">转办接收人</span>
          <NSelect v-model:value="recipient" :options="recipientOptions" :loading="recipientLoading" :disabled="saving"
            :input-props="{ 'aria-labelledby': 'transfer-recipient-label' }" filterable remote clearable
            placeholder="搜索启用账号" @search="searchRecipients" />
        </div>
        <label>操作原因<textarea v-model="operationReason" required maxlength="500" :disabled="saving"></textarea></label>
        <NSpace>
          <NButton autofocus :disabled="saving" @click="closeOperation">取消</NButton>
          <NButton :type="operation === 'transfer' ? 'primary' : 'error'" attr-type="submit" :loading="saving">
            {{ operation ? '确认' + operationLabels[operation] : '' }}
          </NButton>
        </NSpace>
      </form>
    </NModal>
  </section>
</template>

<style scoped>
.workbench, .workbench-form { display: grid; gap: 14px; }
.workbench > header { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; }
.workbench h1 { margin: 0; font-size: 23px; line-height: 1.4; }
.workbench-form { max-width: 560px; }
.workbench-form label, .inbox-filters label { display: grid; gap: 5px; }
.workbench-form input, .workbench-form select, .workbench-form textarea, .inbox-filters input, .inbox-filters select {
  width: 100%; min-height: 36px; padding: 6px 9px; border: 1px solid #aebcd0; border-radius: 6px; background: #fff; color: inherit; font: inherit;
}
.workbench-form textarea { min-height: 76px; resize: vertical; }
.inbox-panel {
  min-width: 0;
  border: 1px solid #d6e0ec;
  border-radius: 5px;
  background: #fff;
  container-type: inline-size;
}
.inbox-filters { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 10px; padding: 14px 16px; }
.inbox-filters label { min-width: min(150px, 100%); }
.inbox-filters label:first-child { flex: 0 1 260px; }
.inbox-toolbar, .inbox-actions, .inbox-filter-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.inbox-toolbar { justify-content: space-between; border-top: 1px solid #d6e0ec; padding: 9px 16px; }
.inbox-toolbar h2 { margin: 0; font-size: 14px; font-weight: 600; }
.inbox-button { min-height: 36px; font-weight: 600; border-radius: 4px; }
.inbox-table-region { overflow: auto; max-block-size: min(56vh, 640px); scroll-padding-block-start: 44px; }
.inbox-table-region:focus-visible { outline: 2px solid #2b59ab; outline-offset: -2px; }
.inbox-table { width: 100%; border-spacing: 0; text-align: start; color: #22334b; }
.inbox-table th, .inbox-table td { padding: 9px 16px; text-align: start; border-block-end: 1px solid #e2e9f1; }
.inbox-table thead { position: sticky; inset-block-start: 0; z-index: 1; background: #f0f4f9; }
.inbox-table thead th { font-weight: 600; }
.inbox-table tbody th { font-weight: 600; overflow-wrap: anywhere; }
.inbox-table tbody tr:hover, .inbox-table tbody tr:focus-within { background: #f5f8fd; }
.inbox-reference, .inbox-node { overflow-wrap: anywhere; }
.inbox-state, .inbox-time, .inbox-action-cell { white-space: nowrap; }
.inbox-time { font-variant-numeric: tabular-nums; }
.inbox-action-cell { width: 1%; }
.inbox-actions { flex-wrap: nowrap; }
.inbox-empty, .inbox-pagination { margin: 0; padding: 16px; }
@container (width <= 680px) {
  .inbox-table .inbox-time { display: none; }
  .inbox-table th, .inbox-table td { padding-inline: 10px; }
}
@container (width <= 500px) {
  .inbox-filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .inbox-filters label { min-width: 0; }
  .inbox-filters label:first-child, .inbox-filter-actions { grid-column: 1 / -1; }
  .inbox-table .inbox-reference { display: none; }
  .inbox-actions { flex-direction: column; align-items: stretch; }
  .inbox-button { min-height: 36px; }
}
</style>
