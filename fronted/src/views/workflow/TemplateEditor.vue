<script setup lang="ts">
import {computed, defineAsyncComponent, ref, watch} from 'vue'
import { NSelect } from 'naive-ui'
import {api} from '@/api/http'
import type { DiagramLayout, DiagramNode } from './approvalDiagram'

const TemplateFlow = defineAsyncComponent(() => import('./TemplateFlow.vue'))

type Position = {x:number; y:number}
type Step = {name:string; mode:string; approverIds:number[]; minAmount:number|null; returnTo:string; position?:Position|null}
type Template = {code:string; name:string; revision:number; publishedVersion:number|null; dirty:boolean; steps:Step[]; diagramLayout?:DiagramLayout|null}
const props = defineProps<{business:string; template:Template|null; edit:boolean; publish:boolean}>()
const emit = defineEmits<{saved:[code:string]}>()
const code = ref(''), name = ref(''), message = ref(''), busy = ref(false)
const steps = ref<Array<Omit<Step,'minAmount'> & {minAmount:string; editorKey:string}>>([])
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
const flow = ref<{ rename: (key: string, name: string) => void } | null>(null)
watch([activeKey, () => step.value?.name], ([key, name]) => {
    if (incompleteSteps.value && key !== null && name !== undefined) flow.value?.rename(key, name)
})
const multipleReviewers = computed(() => step.value?.mode !== 'SINGLE' || (step.value?.approverIds.length ?? 0) > 1)
type Reviewer = { id: number; name: string; username: string; enabled: boolean }
const people = ref<Record<number, Reviewer>>({})
const results = ref<number[]>([])
const searching = ref(false)
const reviewerError = ref('')
let searchRequest = 0
const options = computed(() => Array.from(new Set([...(step.value?.approverIds ?? []), ...results.value])).map(id => {
    const person = people.value[id]
    return {
        value: id,
        label: person ? person.name + '（' + person.username + '）' + (person.enabled ? '' : ' · 已停用') : '原审批人（无法读取）',
    }
}))

function reviewers(query: URLSearchParams) {
    return api<Reviewer[]>('/workflow/businesses/' + encodeURIComponent(props.business) + '/templates/reviewers?' + query)
}

async function search(keyword: string) {
    const request = ++searchRequest
    searching.value = true
    reviewerError.value = ''
    try {
        const matches = await reviewers(new URLSearchParams({ keyword }))
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
        const matches = await reviewers(new URLSearchParams({ ids: ids.join(',') }))
        if (!stale) for (const person of matches) people.value[person.id] = person
    } catch {
        if (!stale) reviewerError.value = '原审批人读取失败'
    }
})

const modified = ref(false)
function selectReviewers(value: number | number[] | null) {
    if (!step.value) return
    step.value.approverIds = Array.isArray(value) ? value : value === null ? [] : [value]
    modified.value = true
}

function add(position?: Position) {
    diagramLayout.value = null
    steps.value.push({name:'',mode:'SINGLE',approverIds:[],minAmount:'',returnTo:'APPLICANT',position,editorKey:'Task_' + nextKey++})
    active.value = steps.value.length - 1
    modified.value = true
}
watch(() => [props.business, props.template], () => {
    const item = props.template
    previousNodeSettings.clear()
    incompleteSteps.value = null
    code.value = item?.code ?? ''
    name.value = item?.name ?? ''
    steps.value = item?.steps.map(step => ({...step,approverIds:[...step.approverIds],minAmount:String(step.minAmount ?? ''),editorKey:'Task_' + nextKey++})) ?? []
    diagramLayout.value = item?.diagramLayout ?? null
    active.value = 0
    if (!item) add()
    modified.value = false
    message.value = ''
}, {immediate:true})
function allowedFirst(step:typeof steps.value[number] | undefined) {
    if (!step || (!step.minAmount && step.returnTo==='APPLICANT')) return true
    message.value = '首节点必须无金额条件且退回申请人，请先修改即将排在首位的节点'
    return false
}
function remove(index:number) {
    if (index===0 && !allowedFirst(steps.value[1])) return
    steps.value.splice(index,1)
    diagramLayout.value = null
    active.value = Math.max(0, Math.min(index, steps.value.length - 1))
    modified.value = true
    message.value = ''
}
function move(index:number, delta:number) {
    if (!delta) return
    if (index+delta===0 && !allowedFirst(steps.value[index])) return
    if (index===0 && !allowedFirst(steps.value[1])) return
    message.value = ''
    const [step] = steps.value.splice(index,1)
    steps.value.splice(index+delta,0,step)
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
async function submit(publishing:boolean) {
    if (busy.value || !diagramValid.value || (!publishing && !props.edit)) return
    const key = code.value.trim()
    const path = '/workflow/businesses/'+encodeURIComponent(props.business)+'/templates/'+encodeURIComponent(key)
    const revision = props.template?.revision ?? 0
    message.value = ''
    busy.value = true
    try {
        if (publishing) {
            await api(path+'/publish','POST',{revision})
        } else {
            if (!key || !name.value.trim() || !steps.value.length) throw Error('请填写模板编码、名称，并添加审批节点')
            if (steps.value.length>30) throw Error('最多可配置 30 个审批节点')
            if (!allowedFirst(steps.value[0])) return
            const payload = steps.value.map(({editorKey: _editorKey, ...step},i) => {
                const ids = step.approverIds
                if (!step.name.trim() || (step.mode==='SINGLE' ? ids.length!==1 : ids.length<2 || ids.length>50)) {
                    active.value = i
                    throw Error('请检查第 '+(i+1)+' 个节点的名称和审批人数量')
                }
                const raw = step.minAmount.trim()
                const amount = raw ? Number(raw) : null
                if (amount !== null) {
                    const decimal = /^(\d{1,16})(?:\.(\d{1,2}))?$/.exec(raw)
                    const canonical = decimal && (decimal[1].replace(/^0+(?=\d)/,'') + '.' + (decimal[2] ?? '').padEnd(2,'0'))
                    if (!canonical || amount.toFixed(2)!==canonical || String(amount)!==canonical.replace(/0+$/,'').replace(/\.$/,'')) {
                        throw Error('第 '+(i+1)+' 个节点金额须为可精确表示的非负金额，最多两位小数')
                    }
                }
                return {...step,name:step.name.trim(),minAmount:amount}
            })
            await api(path,'PUT',{name:name.value.trim(),revision,steps:payload,diagramLayout:diagramLayout.value})
        }
        emit('saved',key)
    } catch (error) {
        message.value = error instanceof Error ? error.message : '操作失败'
    } finally { busy.value = false }
}
</script>

<template>
<form class="editor" @submit.prevent="submit(false)" @input="modified=true">
  <div class="design-bar">
    <div class="design-status">
      <strong>流程设计</strong>
      <p class="state" v-if="template">已发布版本 {{ template.publishedVersion || '暂无' }} · {{ modified ? '有未保存修改' : template.dirty ? '草稿待发布' : '草稿已发布' }}</p>
      <p v-else class="state">新建草稿</p>
      <p v-if="!edit" class="state">当前业务未授予编辑权限</p>
    </div>
    <div class="actions">
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
  <TemplateFlow ref="flow" :steps="steps" :diagram-layout="diagramLayout" :active-key="activeKey" :edit="edit"
    :inert="busy" @select="activeKey = $event" @inspect="inspect"
    @add="add()" @diagram-change="updateDiagram" @validity="diagramValid = $event" />
  <aside class="inspector" aria-label="节点属性">
    <header class="inspector-header">
      <h2>节点属性</h2>
      <small v-if="step">第 {{ active + 1 }} 步</small>
    </header>
  <fieldset v-if="step" :disabled="!edit || busy">
    <article>
      <label>节点名称<input v-model="step.name" required></label>
      <label>审批模式<select v-model="step.mode"><option value="SINGLE">单人审批</option><option value="ANY">或签</option><option value="ALL">会签</option><option value="SEQUENTIAL">依次审批</option></select></label>
      <div class="reviewer-field" @input.stop>
        <span>审批人</span>
        <NSelect :value="multipleReviewers ? step.approverIds : (step.approverIds[0] ?? null)"
          :multiple="multipleReviewers" clearable filterable remote :options="options"
          data-testid="reviewer-picker" :disabled="!edit || busy" :loading="searching"
          placeholder="搜索姓名或账号" @update:show="shown => { if (shown) search('') }"
          @search="search" @update:value="selectReviewers" />
        <small v-if="step.mode==='SEQUENTIAL'">按选择顺序依次审批</small>
        <small v-if="reviewerError" role="alert">{{ reviewerError }}</small>
      </div>
      <label>最低金额<input v-model="step.minAmount" inputmode="decimal" :disabled="active===0" placeholder="不限"></label>
      <label>退回位置<select v-model="step.returnTo"><option value="APPLICANT">申请人</option><option value="PREVIOUS" :disabled="active===0">上一节点</option></select></label>
      <div v-if="edit" class="node-actions"><button type="button" :disabled="!diagramValid || active===0" @click="move(active,-1)">上移</button> <button type="button" :disabled="!diagramValid || active===steps.length-1" @click="move(active,1)">下移</button> <button type="button" :disabled="!diagramValid" @click="remove(active)">删除节点</button></div>
    </article>
  </fieldset>
  <p v-else class="inspector-empty">点击画布中的审批节点，查看或修改该节点的配置。</p>
  </aside>
  </div>
</form>
</template>

<style scoped>
.editor { display:grid; gap:14px; min-width:0; container-type:inline-size; }
.designer { display:grid; grid-template-columns:minmax(0,1fr); gap:14px; align-items:start; min-width:0; }
.design-bar,.template-fields,.inspector { background:#fff; border:1px solid #dce5ef; border-radius:10px; }
.design-bar { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; padding:14px 16px; }
.design-status { display:grid; gap:4px; }
.state { margin:0; color:#536780; font-size:13px; }
.template-fields { display:grid; grid-template-columns:repeat(auto-fit,minmax(min(200px,100%),1fr)); gap:12px; padding:12px 16px; }
fieldset { border:0; padding:0; margin:0; min-width:0; }
.template-fields { border:1px solid #dce5ef; }
.inspector { min-width:0; }
.inspector-header { display:flex; align-items:center; justify-content:space-between; gap:8px; padding:12px 16px; border-bottom:1px solid #e3eaf4; }
h2 { margin:0; font-size:16px; }
.inspector-header small, .inspector-empty { color:#536780; }
.inspector-empty { margin:0; padding:16px; }
article { display:grid; gap:12px; padding:14px 16px; }
label, .reviewer-field { display:grid; gap:5px; min-width:0; }
input,select,button { min-height:34px; padding:6px 10px; border:1px solid #bdcbdc; background:#fff; border-radius:6px; font:inherit; }
input,select { width:100%; min-width:0; }
button { width:fit-content; cursor:pointer; color:#245baf; }
.actions,.node-actions { display:flex; flex-wrap:wrap; gap:8px; }
.node-actions { border-top:1px solid #e3eaf4; padding-top:12px; }
.primary { background:#245baf; color:#fff; border-color:#245baf; }
button:disabled { opacity:.5; cursor:default; }
[role=alert] { color:#ab2537; }
.error { margin:0; padding:10px 14px; background:#fff0f1; border:1px solid #f0c9cf; border-radius:6px; }
@container (min-width: 820px) { .designer { grid-template-columns:minmax(0,1fr) 292px; } }
</style>
