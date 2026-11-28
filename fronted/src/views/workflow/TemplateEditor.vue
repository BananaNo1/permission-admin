<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { NSelect } from 'naive-ui'
import { listTemplateReviewerRoles, listTemplateReviewers, publishTemplate, saveTemplateDraft } from '@/api/workflowTemplate'
import type { DiagramPoint as Position, Reviewer, ReviewerSource, TemplateDraft, TemplateStep } from '@/types/workflow'
import { START, END, isSplit, operators, routedWaypoints, taskPosition,
    type BranchOperator, type RoutingGraph, type RouteLink, type DiagramLayout, type DiagramNode } from './approvalRouting'
import TemplateVersions from './TemplateVersions.vue'

const TemplateFlow = defineAsyncComponent(() => import('./TemplateFlow.vue'))

const props = defineProps<{ business: string; template: TemplateDraft | null; edit: boolean; publish: boolean }>()
const emit = defineEmits<{ saved: [code: string] }>()
const code = ref(''), name = ref(''), message = ref(''), busy = ref(false)
const steps = ref<Array<Omit<TemplateStep, 'minAmount'> & { minAmount: string; editorKey: string }>>([])
const incompleteSteps = ref<typeof steps.value | null>(null)
const inspectorSteps = computed(() => incompleteSteps.value ?? steps.value)
const previousNodeSettings = new Map<string, typeof steps.value[number]>()
const diagramValid = ref(true)
const diagramLayout = ref<DiagramLayout | null>(null)
let nextKey = 0
const activeKey = ref<string | null>(null)
const active = computed({
    get: () => inspectorSteps.value.findIndex(node => node.editorKey === activeKey.value),
    set: (index: number) => { activeKey.value = inspectorSteps.value[index]?.editorKey ?? null },
})
const step = computed(() => inspectorSteps.value[active.value])
const activeLink = ref<string | null>(null)
const currentLink = computed(() => diagramLayout.value?.routing?.links.find(link => link.id === activeLink.value))
const branchOperator = ref<BranchOperator>('NONE')
const branchAmount = ref('')
const branchError = ref('')
const branchSaved = ref('')
watch(currentLink, link => {
    branchOperator.value = link?.operator ?? 'NONE'
    branchAmount.value = String(link?.amount ?? '')
    branchError.value = ''
}, { immediate: true })

function selectNode(id: string | null) {
    activeLink.value = null
    activeKey.value = id
}

function selectLink(id: string) {
    activeKey.value = null
    activeLink.value = id
    branchSaved.value = ''
}

function linkEndpoint(id: string) {
    if (id === END) return '审批结束'
    if (id === START) return '发起申请'
    return steps.value.find(node => node.editorKey === id)?.name || '排他网关'
}

function validateConditions(graph: RoutingGraph) {
    for (const gate of graph.gateways) {
        const outgoing = graph.links.filter(link => link.source === gate.id)
        if (outgoing.length < 2) continue
        if (outgoing.filter(link => link.operator === 'DEFAULT').length !== 1) {
            throw Error('每个分支网关必须有一条默认连线')
        }
        if (outgoing.some(link => link.operator === 'NONE' || (link.operator !== 'DEFAULT' && link.amount == null))) {
            throw Error('请为所有非默认连线配置金额条件')
        }
    }
}

function applyCondition() {
    const layout = diagramLayout.value
    const graph = layout?.routing
    const selected = currentLink.value
    if (!layout || !graph || !selected || !isSplit(graph, selected.source)) return
    const operator = branchOperator.value
    const amount = branchAmount.value.trim()
    if (operator === 'NONE') {
        branchError.value = '请选择默认分支或金额运算符'
        return
    }
    if (operator !== 'DEFAULT' && !/^\d{1,16}(\.\d{1,2})?$/.test(amount)) {
        branchError.value = '请输入非负金额，整数最多 16 位，小数最多两位'
        return
    }
    const links = graph.links.map(link => {
        if (link.id === selected.id) return { ...link, operator, amount: operator === 'DEFAULT' ? null : amount }
        if (operator === 'DEFAULT' && link.source === selected.source && link.operator === 'DEFAULT') {
            return { ...link, operator: 'NONE' as const, amount: null }
        }
        return link
    })
    diagramLayout.value = { ...layout, routing: { ...graph, links } }
    modified.value = true
    branchError.value = ''
    branchSaved.value = '连线条件已应用，请保存草稿'
}

function addBranch() {
    const source = step.value
    if (!source || !diagramValid.value || steps.value.length >= 30) return
    if (steps.value.some(item => item.minAmount)) {
        message.value = '请先清除节点最低金额，再将金额条件配置到网关连线上'
        return
    }
    const existing = diagramLayout.value?.routing
    const idList = [START, ...steps.value.map(item => item.editorKey), END]
    const graph: RoutingGraph = existing ?? {
        taskIds: idList.slice(1, -1), gateways: [], links: idList.slice(1).map((target, index) => ({
            id: 'Sequence_' + index, source: idList[index], target, operator: 'NONE',
        })),
    }
    if (graph.gateways.length >= 20 || graph.links.length + 3 > 120) {
        message.value = '最多可配置 20 个网关和 120 条连线'
        return
    }
    const outgoing = graph.links.find(link => link.source === source.editorKey)
    if (!outgoing) return
    let gatewayId: string
    let editorKey: string
    do {
        const sequence = nextKey++
        gatewayId = 'ApprovalGateway_' + sequence
        editorKey = 'Task_' + sequence
    } while (graph.gateways.some(gate => gate.id === gatewayId) || idList.includes(editorKey))
    const position = taskPosition(source, active.value)
    const gateway = { id: gatewayId, position: { x: position.x + 200, y: position.y + 12 } }
    const task = {
        name: '条件审批', mode: 'SINGLE', approverIds: [], minAmount: '', returnTo: 'APPLICANT', editorKey,
        position: { x: gateway.position.x + 134, y: gateway.position.y + 106 },
    }
    const newSteps = [...steps.value, task]
    const branch: RouteLink = { id: gatewayId + '_condition', source: gatewayId, target: editorKey, operator: 'NONE' }
    const links: RouteLink[] = [
        ...graph.links.map(link => link.id === outgoing.id ? { ...link, target: gatewayId } : link),
        branch,
        { id: gatewayId + '_default', source: gatewayId, target: outgoing.target, operator: 'DEFAULT' },
        { id: gatewayId + '_join', source: editorKey, target: outgoing.target, operator: 'NONE' },
    ]
    const routing = { taskIds: newSteps.map(item => item.editorKey), gateways: [...graph.gateways, gateway], links }
    const start = diagramLayout.value?.start ?? { x: 55, y: 144 }
    const end = outgoing.target === END
        ? { x: task.position.x + 250, y: position.y + 19 }
        : diagramLayout.value?.end ?? { x: Math.max(...steps.value.map((item, index) => taskPosition(item, index).x + 211)), y: 144 }
    if (task.position.x > 8000 || task.position.y > 4000 || end.x > 10000 || end.y > 6000) {
        message.value = '画布边缘空间不足，请先向左或向上移动节点'
        return
    }
    const layout = { start, end, waypoints: [], routing }
    steps.value = newSteps
    diagramLayout.value = { ...layout, waypoints: routedWaypoints(newSteps, layout, routing) }
    modified.value = true
    message.value = ''
    selectLink(branch.id)
}


const flow = ref<{ rename: (key: string, name: string) => void } | null>(null)
watch([activeKey, () => step.value?.name], ([key, name]) => {
    if (incompleteSteps.value && key !== null && name !== undefined) flow.value?.rename(key, name)
})
const multipleReviewers = computed(() => step.value?.mode !== 'SINGLE' || (step.value?.approverIds.length ?? 0) > 1)
const people = ref<Record<number, Reviewer>>({})
const results = ref<number[]>([])
const searching = ref(false)
const roleOptions = ref<{ value: string; label: string }[]>([])
const rolesLoading = ref(false)
const rolesError = ref('')
let rolesBusiness = ''
let roleRequest = 0
const selectableRoles = computed(() => {
  const id = step.value?.roleId
  return id && !roleOptions.value.some(role => role.value === id)
    ? [{ value: id, label: '原审批角色 #' + id }, ...roleOptions.value] : roleOptions.value
})

async function loadReviewerRoles() {
  if (rolesBusiness === props.business) return
  const request = ++roleRequest
  const business = props.business
  roleOptions.value = []
  rolesLoading.value = true
  rolesError.value = ''
  try {
    const roles = await listTemplateReviewerRoles(business)
    if (request !== roleRequest || business !== props.business) return
    roleOptions.value = roles.map(role => ({ value: role.id, label: role.name + '（' + role.code + '）' }))
    rolesBusiness = business
  } catch {
    if (request === roleRequest) rolesError.value = '审批角色读取失败，请重新加载'
  } finally {
    if (request === roleRequest) rolesLoading.value = false
  }
}

watch([() => props.business, () => step.value?.reviewerSource], ([, source]) => {
  if (source === 'ROLE') void loadReviewerRoles()
})

function changeReviewerSource(event: Event) {
  if (!step.value) return
  const source = (event.target as HTMLSelectElement).value as ReviewerSource
  step.value.reviewerSource = source
  step.value.roleId = null
  step.value.approverIds = []
  if (source === 'DEPARTMENT_MANAGER') step.value.mode = 'SINGLE'
  modified.value = true
}

const reviewerError = ref('')
let searchRequest = 0
const options = computed(() => Array.from(new Set([...(step.value?.approverIds ?? []), ...results.value])).map(id => {
    const person = people.value[id]
    return {
        value: id,
        label: person ? person.name + '（' + person.username + '）' + (person.enabled ? '' : ' · 已停用') : '原审批人（无法读取）',
    }
}))

async function search(keyword: string) {
    const request = ++searchRequest
    searching.value = true
    reviewerError.value = ''
    try {
        const matches = await listTemplateReviewers(props.business, { keyword })
        if (request !== searchRequest) return
        for (const person of matches) people.value[person.id] = person
        results.value = matches.map(person => person.id)
    } catch {
        if (request === searchRequest) {
            results.value = []
            reviewerError.value = '审批人查询失败，请重新搜索'
        }
    } finally {
        if (request === searchRequest) searching.value = false
    }
}

watch(step, async (selected, _, onCleanup) => {
    const ids = selected?.approverIds.filter(id => !people.value[id]) ?? []
    if (!ids.length) return
    let stale = false
    onCleanup(() => { stale = true })
    try {
        const matches = await listTemplateReviewers(props.business, { ids })
        if (!stale) for (const person of matches) people.value[person.id] = person
    } catch {
        if (!stale) reviewerError.value = '原审批人读取失败'
    }
})

const modified = ref(false)
const versionsOpen = ref(false)
function selectReviewers(value: number | number[] | null) {
    if (!step.value) return
    step.value.approverIds = Array.isArray(value) ? value : value === null ? [] : [value]
    modified.value = true
}

function add(position?: Position) {
    diagramLayout.value = null
    steps.value.push({ name: '', mode: 'SINGLE', approverIds: [], minAmount: '', returnTo: 'APPLICANT', position, editorKey: 'Task_' + nextKey++ })
    activeLink.value = null
    active.value = steps.value.length - 1
    modified.value = true
}
watch(() => [props.business, props.template], () => {
    const item = props.template
    previousNodeSettings.clear()
    incompleteSteps.value = null
    code.value = item?.code ?? ''
    name.value = item?.name ?? ''
    steps.value = item?.steps.map((step, index) => ({ ...step, roleId: step.roleId == null ? null : String(step.roleId),
        approverIds: [...step.approverIds], minAmount: String(step.minAmount ?? ''), editorKey: item.diagramLayout?.routing?.taskIds[index] ?? ('Task_' + nextKey++) })) ?? []
    diagramLayout.value = item?.diagramLayout ?? null
    activeLink.value = null
    active.value = 0
    if (!item) add()
    modified.value = false
    message.value = ''
}, { immediate: true })
function allowedFirst(step: typeof steps.value[number] | undefined) {
    if (!step || (!step.minAmount && step.returnTo === 'APPLICANT')) return true
    message.value = '首节点必须无金额条件且退回申请人，请先修改即将排在首位的节点'
    return false
}
function remove(index: number) {
    if (index === 0 && !allowedFirst(steps.value[1])) return
    steps.value.splice(index, 1)
    diagramLayout.value = null
    active.value = Math.max(0, Math.min(index, steps.value.length - 1))
    modified.value = true
    message.value = ''
}
function move(index: number, delta: number) {
    if (!delta) return
    if (index + delta === 0 && !allowedFirst(steps.value[index])) return
    if (index === 0 && !allowedFirst(steps.value[1])) return
    message.value = ''
    const [step] = steps.value.splice(index, 1)
    steps.value.splice(index + delta, 0, step)
    diagramLayout.value = null
    active.value = index + delta
    modified.value = true
}

function mergeCanvasNodes(nodes: DiagramNode[]) {
    for (const node of inspectorSteps.value) previousNodeSettings.set(node.editorKey, node)
    return nodes.map(node => ({
        mode: 'SINGLE', approverIds: [], minAmount: '', returnTo: 'APPLICANT',
        ...previousNodeSettings.get(node.editorKey), ...node,
    }))
}

function inspect(nodes: DiagramNode[] | null) {
    incompleteSteps.value = nodes ? mergeCanvasNodes(nodes) : null
}

function updateDiagram(nodes: DiagramNode[], layout: DiagramLayout) {
    steps.value = mergeCanvasNodes(nodes)
    incompleteSteps.value = null
    diagramLayout.value = layout
    modified.value = true
}
async function submit(publishing: boolean) {
    if (busy.value || !diagramValid.value || (!publishing && !props.edit)) return
    const key = code.value.trim()
    const revision = props.template?.revision ?? 0
    message.value = ''
    busy.value = true
    try {
        if (publishing) {
            await publishTemplate(props.business, key, revision)
        } else {
            if (!key || !name.value.trim() || !steps.value.length) throw Error('请填写模板编码、名称，并添加审批节点')
            if (steps.value.length > 30) throw Error('最多可配置 30 个审批节点')
            if (diagramLayout.value?.routing) validateConditions(diagramLayout.value.routing)
            else if (!allowedFirst(steps.value[0])) return
            const payload = steps.value.map(({ editorKey: _editorKey, ...step }, i) => {
                const ids = step.approverIds
                const source = step.reviewerSource ?? 'FIXED'
                if (!step.name.trim() || (source === 'FIXED' && (step.mode === 'SINGLE' ? ids.length !== 1 : ids.length < 2 || ids.length > 50))
                    || (source === 'ROLE' && !step.roleId)) {
                    active.value = i
                    throw Error('请检查第 ' + (i + 1) + ' 个节点的名称和审批人配置')
                }
                const raw = step.minAmount.trim()
                const amount = raw ? Number(raw) : null
                if (amount !== null) {
                    const decimal = /^(\d{1,16})(?:\.(\d{1,2}))?$/.exec(raw)
                    const canonical = decimal && (decimal[1].replace(/^0+(?=\d)/, '') + '.' + (decimal[2] ?? '').padEnd(2, '0'))
                    if (!canonical || amount.toFixed(2) !== canonical || String(amount) !== canonical.replace(/0+$/, '').replace(/\.$/, '')) {
                        throw Error('第 ' + (i + 1) + ' 个节点金额须为可精确表示的非负金额，最多两位小数')
                    }
                }
                return { ...step, name: step.name.trim(), minAmount: amount }
            })
            await saveTemplateDraft(props.business, key, { name: name.value.trim(), revision, steps: payload, diagramLayout: diagramLayout.value })
        }
        emit('saved', key)
    } catch (error) {
        message.value = error instanceof Error ? error.message : '操作失败'
    } finally { busy.value = false }
}
</script>

<template>
<form class="editor" @submit.prevent="submit(false)" @input="modified = true">
  <div class="design-bar">
    <div class="design-status">
      <strong>流程设计</strong>
      <span v-if="template?.enabled === false" class="state"> · 已停用新申请</span>
      <p class="state" v-if="template">已发布版本 {{ template.publishedVersion || '暂无' }} · {{ modified ? '有未保存修改' : template.dirty ? '草稿待发布' : '草稿已发布' }}</p>
      <p v-else class="state">新建草稿</p>
      <p v-if="!edit" class="state">当前业务未授予编辑权限</p>
    </div>
    <div class="actions">
      <button v-if="template?.publishedVersion" type="button" :disabled="busy" @click="versionsOpen = true">版本管理</button>
      <button v-if="edit" class="primary" :disabled="busy || !diagramValid">保存草稿</button>
      <button v-if="publish && template" :class="{ primary: !edit }" type="button"
        :disabled="busy || !diagramValid || modified || !template.dirty" @click="submit(true)">发布版本</button>
    </div>
  </div>
  <p v-if="message" class="error" role="alert">{{ message }}</p>
  <fieldset class="template-fields" :disabled="!edit || busy">
    <label>模板编码<input v-model="code" required :readonly="!!template"></label>
    <label>模板名称<input v-model="name" required></label>
  </fieldset>
  <div class="designer">
  <TemplateFlow ref="flow" :steps="steps" :diagram-layout="diagramLayout" :active-key="activeKey" :active-link="activeLink" :edit="edit"
    :inert="busy" @select="selectNode" @select-link="selectLink" @inspect="inspect"
    @add="add()" @add-branch="addBranch" @diagram-change="updateDiagram" @validity="diagramValid = $event" />
  <aside class="inspector" aria-label="节点属性">
    <header class="inspector-header">
      <h2>{{ currentLink ? '连线属性' : '节点属性' }}</h2>
      <small v-if="step">第 {{ active + 1 }} 步</small>
    </header>
  <fieldset v-if="currentLink" :disabled="!edit || busy || !diagramValid">
    <article>
      <p>{{ linkEndpoint(currentLink.source) }} → {{ linkEndpoint(currentLink.target) }}</p>
      <label>分支条件
        <select v-model="branchOperator" @change="branchError = ''">
          <option value="NONE">请选择</option>
          <option value="DEFAULT">默认分支</option>
          <option v-for="rule in (['GTE', 'GT', 'LTE', 'LT', 'EQ'] as const)" :key="rule" :value="rule">金额 {{ operators[rule] }}</option>
        </select>
      </label>
      <label v-if="branchOperator !== 'NONE' && branchOperator !== 'DEFAULT'">条件金额
        <input v-model="branchAmount" inputmode="decimal" maxlength="19" :aria-invalid="!!branchError"
          :aria-describedby="branchError ? 'branch-condition-help branch-condition-error' : 'branch-condition-help'"
          @input="branchError = ''" />
      </label>
      <p id="branch-condition-help">同一网关按条件连线列表从上到下匹配。全部未命中时走默认分支。</p>
      <p v-if="branchError" id="branch-condition-error" class="error" role="alert">{{ branchError }}</p>
      <p v-if="branchSaved" role="status">{{ branchSaved }}</p>
      <button v-if="edit" type="button" @click="applyCondition">应用连线条件</button>
    </article>
  </fieldset>
  <fieldset v-else-if="step" :disabled="!edit || busy">
    <article>
      <label>节点名称<input v-model="step.name" required></label>
    </article>
    <details open>
      <summary>任务配置</summary>
      <article>
      <label>审批人来源<select :value="step.reviewerSource ?? 'FIXED'" @change="changeReviewerSource">
        <option value="FIXED">指定成员</option><option value="ROLE">角色成员</option><option value="DEPARTMENT_MANAGER">申请人部门负责人</option>
      </select></label>
      <label>审批模式<select v-model="step.mode" :disabled="step.reviewerSource === 'DEPARTMENT_MANAGER'"><option value="SINGLE">单人审批</option><option value="ANY">或签</option><option value="ALL">会签</option><option value="SEQUENTIAL">依次审批</option></select></label>
      <div v-if="!step.reviewerSource || step.reviewerSource === 'FIXED'" class="reviewer-field" @input.stop>
        <span>审批人</span>
        <NSelect :value="multipleReviewers ? step.approverIds : (step.approverIds[0] ?? null)"
          :multiple="multipleReviewers" clearable filterable remote :options="options"
          data-testid="reviewer-picker" :disabled="!edit || busy" :loading="searching"
          placeholder="搜索姓名或账号" @update:show="shown => { if (shown) search('') }"
          @search="search" @update:value="selectReviewers" />
        <small v-if="step.mode === 'SEQUENTIAL'">按选择顺序依次审批</small>
        <small v-if="reviewerError" role="alert">{{ reviewerError }}</small>
      </div>
      <div v-if="step.reviewerSource === 'ROLE'" class="reviewer-field">
        <label>审批角色
          <NSelect v-model:value="step.roleId" :options="selectableRoles" :loading="rolesLoading" :disabled="!edit || busy"
            filterable clearable :input-props="{ 'aria-label': '审批角色' }" @update:show="show => { if (show && !rolesLoading) loadReviewerRoles() }"
            @update:value="modified = true" />
        </label>
        <small v-if="rolesError" role="alert">{{ rolesError }}</small>
        <small v-else>按发起时的启用成员分配，申请人自动排除。依次审批按账号标识排序。</small>
      </div>
      <small v-if="step.reviewerSource === 'DEPARTMENT_MANAGER'">按发起人所属部门查找负责人。未配置或负责人不可用时禁止发起。</small>
      <label v-if="!diagramLayout?.routing || step.minAmount">最低金额<input v-model="step.minAmount" inputmode="decimal"
        :disabled="!diagramLayout?.routing && active === 0" placeholder="不限"></label>
      <label>退回位置<select v-model="step.returnTo"><option value="APPLICANT">申请人</option><option value="PREVIOUS" :disabled="!diagramLayout?.routing && active === 0">上一节点</option></select></label>
      <div v-if="edit && !diagramLayout?.routing" class="node-actions"><button type="button" :disabled="!diagramValid || active === 0" @click="move(active, -1)">上移</button> <button type="button" :disabled="!diagramValid || active === steps.length - 1" @click="move(active, 1)">下移</button> <button type="button" :disabled="!diagramValid" @click="remove(active)">删除节点</button></div>
      </article>
    </details>
    <details>
      <summary>表单配置</summary>
      <article>
        <label>办理表单
          <select :value="step.formKey ?? ''" @change="step.formKey = ($event.target as HTMLSelectElement).value || null; modified = true">
            <option value="">默认审批表单</option>
            <option value="opinion">审批意见选填</option>
            <option value="required-opinion">审批意见必填</option>
          </select>
        </label>
      </article>
    </details>
    <details>
      <summary>执行监听器</summary>
      <article>
        <label class="check-setting"><input v-model="step.recordEntered" type="checkbox" /> 节点进入时记录时间</label>
      </article>
    </details>
    <details>
      <summary>任务监听器</summary>
      <article>
        <label class="check-setting"><input v-model="step.recordCompleted" type="checkbox" /> 任务办理完成时记录时间</label>
      </article>
    </details>
  </fieldset>
  <p v-else class="inspector-empty">点击画布中的审批节点，查看或修改该节点的配置。</p>
  </aside>
  </div>
</form>
<TemplateVersions v-if="template?.publishedVersion" v-model:show="versionsOpen"
  :business="business" :code="template.code" :revision="template.revision" :published-version="template.publishedVersion"
  :enabled="template.enabled !== false" :can-restore="edit && !modified && !template.dirty"
  :can-change-availability="publish && !modified"
  @changed="emit('saved', template.code)" />
</template>

<style scoped>
.editor { display: grid; gap: 14px; min-width: 0; container-type: inline-size; }
.designer { display: grid; grid-template-columns: minmax(0, 1fr); gap: 14px; align-items: start; min-width: 0; }
.design-bar, .template-fields, .inspector { background: #fff; border: 1px solid #dce5ef; border-radius: 10px; }
.design-bar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; padding: 14px 16px; }
.design-status { display: grid; gap: 4px; }
.state { margin: 0; color: #536780; font-size: 13px; }
.template-fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(200px, 100%), 1fr)); gap: 12px; padding: 12px 16px; }
fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
.template-fields { border: 1px solid #dce5ef; }
.inspector { min-width: 0; }
.inspector-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 12px 16px; border-bottom: 1px solid #e3eaf4; }
h2 { margin: 0; font-size: 16px; }
.inspector-header small, .inspector-empty { color: #536780; }
.inspector-empty { margin: 0; padding: 16px; }
article { display: grid; gap: 12px; padding: 14px 16px; }
label, .reviewer-field { display: grid; gap: 5px; min-width: 0; }
input, select, button { min-height: 34px; padding: 6px 10px; border: 1px solid #bdcbdc; background: #fff; border-radius: 6px; font: inherit; }
input, select { width: 100%; min-width: 0; }
button { width: fit-content; cursor: pointer; color: #245baf; }
.actions, .node-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.node-actions { border-top: 1px solid #e3eaf4; padding-top: 12px; }
.primary { background: #245baf; color: #fff; border-color: #245baf; }
button:disabled { opacity: .5; cursor: default; }
[role=alert] { color: #ab2537; }
.error { margin: 0; padding: 10px 14px; background: #fff0f1; border: 1px solid #f0c9cf; border-radius: 6px; }
@container (min-width: 820px) { .designer { grid-template-columns: minmax(0, 1fr) 292px; } }
.inspector details { border-top: 1px solid #e3eaf4; }
.inspector summary { cursor: pointer; font-weight: 600; padding: 12px 16px; }
.inspector details article { padding-top: 0; }
.check-setting { display: flex; gap: 8px; align-items: center; }
.check-setting input { width: auto; min-height: auto; margin: 0; }
</style>
