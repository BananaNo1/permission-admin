<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Modeler from 'bpmn-js/lib/Modeler'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { approvalDiagram } from './approvalDiagram'
import { START, END, branchLabel, isSplit, type RouteLink, type DiagramLayout, type DiagramNode, type DiagramPoint } from './approvalRouting'

type Element = {
  id: string; type: string; x: number; y: number; labelTarget?: Element
  incoming: Element[]; outgoing: Element[]; source?: Element; target?: Element; waypoints?: DiagramPoint[]
  businessObject: { name?: string }
}
type Registry = { get(id: string): Element | undefined; getAll(): Element[] }
type Canvas = { zoom(value?: number | 'fit-viewport'): number }
type Selection = { get(): Element[]; select(element: Element): void }
type Modeling = { updateLabel(element: Element, name: string): void }
type History = { undo(): void; redo(): void }
type PaletteEntry = { title?: string }
type Palette = { registerProvider(provider: { getPaletteEntries(): (entries: Record<string, PaletteEntry>) => Record<string, PaletteEntry> }): void }

class ApprovalPalette {
  static $inject = ['palette']
  constructor(palette: Palette) { palette.registerProvider(this) }
  getPaletteEntries() {
    return (entries: Record<string, PaletteEntry>) => {
      const supported = ['hand-tool', 'lasso-tool', 'space-tool', 'global-connect-tool', 'create.task', 'create.exclusive-gateway']
      for (const key of Object.keys(entries)) if (!supported.includes(key)) delete entries[key]
      if (entries['create.task']) entries['create.task'].title = '拖入审批节点'
      return entries
    }
  }
}

const props = defineProps<{ steps: DiagramNode[]; diagramLayout?: DiagramLayout | null; activeKey: string | null; activeLink: string | null; edit: boolean }>()
const emit = defineEmits<{
  select: [key: string | null]; selectLink: [key: string]; addBranch: []; diagramChange: [nodes: DiagramNode[], layout: DiagramLayout];
  inspect: [nodes: DiagramNode[] | null]; validity: [valid: boolean]; add: []
}>()
const host = ref<HTMLElement>()
const error = ref('')
const ready = ref(false)
const incompleteNodes = ref<DiagramNode[] | null>(null)
let instance: Modeler | NavigatedViewer | undefined
let syncing = false
let pending = false
let generation = 0
let signature = ''
let layoutSignature = ''
const describe = (nodes: DiagramNode[]) => JSON.stringify(nodes.map(node => [node.editorKey, node.name, node.position?.x, node.position?.y]))
const describeLayout = () => JSON.stringify(props.diagramLayout ?? null)
const round = ({ x, y }: DiagramPoint) => ({ x: Math.round(x), y: Math.round(y) })
const isGateway = (element: Element) => element.type === 'bpmn:ExclusiveGateway'
const isTask = (element: Element) => element.type === 'bpmn:UserTask' || element.type === 'bpmn:Task'
const toNode = (element: Element): DiagramNode => ({
  editorKey: element.id, name: element.businessObject.name ?? '', position: round(element),
})

function showError(message: string) {
  error.value = message
  emit('validity', !message)
}

function selectActive() {
  const id = props.activeLink ?? props.activeKey
  const element = id && instance?.get<Registry>('elementRegistry').get(id)
  if (element) instance?.get<Selection>('selection').select(element)
}

function rename(key: string, name: string) {
  if (!instance || !props.edit || !ready.value) return
  const element = instance.get<Registry>('elementRegistry').get(key)
  if (element && isTask(element) && (element.businessObject.name ?? '') !== name) {
    instance.get<Modeling>('modeling').updateLabel(element, name)
  }
}

defineExpose({ rename })

function routedLayout(items: Element[], connections: Element[], start: Element, end: Element) {
  if (start.id !== START || end.id !== END || start.incoming.length || end.outgoing.length) return null
  const gateways = items.filter(isGateway).map(gate => ({ id: gate.id, position: round(gate) }))
  const nodes = items.filter(isTask).map(toNode)
  const ids = new Set(items.map(item => item.id))
  if (nodes.length > 30 || gateways.length > 20 || connections.length > 120 ||
      items.some(item => item !== start && item !== end && !isTask(item) && !isGateway(item))) return null

  const positionOk = (point: DiagramPoint) => Math.abs(point.x) <= 10000 && Math.abs(point.y) <= 6000
  if (![round(start), round(end), ...gateways.map(gate => gate.position)].every(positionOk) ||
      nodes.some(node => Math.abs(node.position!.x) > 8000 || Math.abs(node.position!.y) > 4000)) return null
  const previous = props.diagramLayout?.routing
  const rank = new Map(previous?.links.map((link, index) => [link.id, index]))
  const saved = new Map(previous?.links.map(link => [link.id, link]))
  const connectionsInOrder = [...connections].sort((a, b) => (rank.get(a.id) ?? Infinity) - (rank.get(b.id) ?? Infinity))
  const links: RouteLink[] = []
  const waypoints: DiagramPoint[][] = []
  const destinations = new Map<string, Set<string>>()
  const incoming = new Map<string, number>()
  for (const connection of connectionsInOrder) {
    const source = connection.source?.id
    const target = connection.target?.id
    const points = connection.waypoints?.map(round) ?? []
    if (!source || !target || !ids.has(source) || !ids.has(target) || source === END || target === START ||
        points.length < 2 || points.length > 64 || !points.every(positionOk)) return null
    const next = destinations.get(source) ?? new Set<string>()
    if (next.has(target)) return null
    destinations.set(source, next.add(target))
    incoming.set(target, (incoming.get(target) ?? 0) + 1)
    const old = saved.get(connection.id)
    links.push(old?.source === source && old.target === target ? { ...old } : { id: connection.id, source, target, operator: 'NONE' })
    waypoints.push(points)
  }

  const graph = { taskIds: nodes.map(node => node.editorKey), gateways, links }
  for (const item of items) {
    if (item !== start && !incoming.has(item.id)) return null
    const outgoing = destinations.get(item.id)?.size ?? 0
    if (item !== end && (outgoing === 0 || (!isGateway(item) && outgoing !== 1))) return null
  }
  const queue = [START]
  for (let index = 0; index < queue.length; index++) {
    for (const next of destinations.get(queue[index]) ?? []) {
      const pending = (incoming.get(next) ?? 0) - 1
      incoming.set(next, pending)
      if (!pending) queue.push(next)
    }
  }
  if (queue.length !== items.length) return null
  const taskKeys = new Set(graph.taskIds)
  const seen = new Set<string>()
  const unchecked = [START]
  for (const id of unchecked) {
    if (id === END) return null
    if (seen.has(id) || taskKeys.has(id)) continue
    seen.add(id)
    unchecked.push(...(destinations.get(id) ?? []))
  }

  for (const link of links) {
    if (!isSplit(graph, link.source)) {
      link.operator = 'NONE'
      link.amount = null
    }
  }
  if (previous) {
    const stepOrder = new Map(previous.taskIds.map((id, index) => [id, index]))
    nodes.sort((a, b) => (stepOrder.get(a.editorKey) ?? Infinity) - (stepOrder.get(b.editorKey) ?? Infinity))
    graph.taskIds = nodes.map(node => node.editorKey)
  }
  return { nodes, layout: { start: round(start), end: round(end), waypoints, routing: graph } }
}

const conditionalLinks = computed(() => {
  const graph = props.diagramLayout?.routing
  return graph?.links.filter(link => isSplit(graph, link.source)) ?? []
})

function linkLabel(link: RouteLink) {
  const target = props.steps.find(node => node.editorKey === link.target)?.name
  return (branchLabel(link) || '待配置分支') + ' → ' + (target || (link.target === END ? '审批结束' : '下一网关'))
}

function updateFromCanvas() {
  if (!instance || syncing || !ready.value || !props.edit) return
  const elements = instance.get<Registry>('elementRegistry').getAll().filter(element => !element.labelTarget)
  const items = elements.filter(element => element.type !== 'bpmn:Process' && element.type !== 'bpmn:SequenceFlow')
  const start = items.find(element => element.type === 'bpmn:StartEvent')
  const end = items.find(element => element.type === 'bpmn:EndEvent')
  const invalid = '请连接成一条从开始到结束的审批路径，途中可添加审批节点。'
  function inspectInvalid(message: string) {
    incompleteNodes.value = items.filter(isTask).map(toNode)
    emit('inspect', incompleteNodes.value)
    showError(message)
  }
  if (start && end && items.some(isGateway)) {
    const routed = routedLayout(items, elements.filter(element => element.type === 'bpmn:SequenceFlow'), start, end)
    if (!routed) {
      inspectInvalid('请连接所有节点和排他网关，确保每条路径经过审批节点且没有循环。')
      return
    }
    acceptLayout(routed.nodes, routed.layout)
    return
  }
  if (!start || !end || start.incoming.length || end.outgoing.length ||
      items.some(item => item !== start && item !== end && !isTask(item))) {
    inspectInvalid(invalid)
    return
  }
  const nodes: DiagramNode[] = []
  const waypoints: DiagramPoint[][] = []
  const visited = new Set([start.id])
  let current = start
  while (current !== end) {
    const connection: Element | undefined = current.outgoing.length === 1 ? current.outgoing[0] : undefined
    const next: Element | undefined = connection?.target
    if (!next || next.incoming.length !== 1 || visited.has(next.id) || (next !== end && !isTask(next))) {
      inspectInvalid(invalid)
      return
    }
    visited.add(next.id)
    waypoints.push(connection?.waypoints?.map(round) ?? [])
    if (next !== end) nodes.push(toNode(next))
    current = next
  }
  const layout = { start: round(start), end: round(end), waypoints }
  const invalidLayout = [layout.start, layout.end, ...waypoints.flat()].some(point =>
    Math.abs(point.x) > 10000 || Math.abs(point.y) > 6000)
  if (visited.size !== items.length || nodes.length > 30 || invalidLayout
      || waypoints.some(line => line.length < 2 || line.length > 64) ||
      nodes.some(node => Math.abs(node.position!.x) > 8000 || Math.abs(node.position!.y) > 4000)) {
    inspectInvalid(nodes.length > 30 ? '最多可配置 30 个审批节点。' : '请检查未连接节点及画布位置。')
    return
  }
  acceptLayout(nodes, layout)
}

function acceptLayout(nodes: DiagramNode[], layout: DiagramLayout) {
  const wasIncomplete = incompleteNodes.value !== null
  incompleteNodes.value = null
  showError('')
  const previous = new Map(props.steps.map(step => [step.editorKey, step]))
  if (!layout.routing) nodes.forEach(node => {
    const old = previous.get(node.editorKey)
    if (old && !old.position) {
      const index = props.steps.indexOf(old)
      if (node.position?.x === 160 + index * 235 && node.position.y === 125) node.position = old.position
    }
  })
  const nextSignature = describe(nodes)
  const nextLayout = JSON.stringify(layout)
  if (wasIncomplete || signature !== nextSignature || layoutSignature !== nextLayout) {
    signature = nextSignature
    layoutSignature = nextLayout
    emit('diagramChange', nodes, layout)
  }
}

async function render(force = false) {
  if (!instance) return
  if (syncing) { pending = true; return }
  const nextSignature = describe(props.steps)
  const nextLayout = describeLayout()
  if (!force && signature === nextSignature && layoutSignature === nextLayout) return
  const registry = ready.value ? instance.get<Registry>('elementRegistry') : undefined
  const existing = signature ? JSON.parse(signature) as [string, string, number | null, number | null][] : []
  if (!force && !error.value && registry && layoutSignature === nextLayout && existing.length === props.steps.length &&
      props.steps.every((node, i) => node.editorKey === existing[i][0]
        && (node.position?.x ?? null) === existing[i][2] && (node.position?.y ?? null) === existing[i][3])) {
    const modeling = instance.get<Modeling>('modeling', false)
    syncing = true
    for (const node of props.steps) {
      const element = registry.get(node.editorKey)
      if (modeling && element && (element.businessObject.name ?? '') !== node.name) modeling.updateLabel(element, node.name)
    }
    signature = nextSignature
    syncing = false
    if (!modeling) await render(true)
    return
  }
  syncing = true
  ready.value = false
  const current = instance
  const version = generation
  try {
    const xml = await approvalDiagram(props.steps, props.diagramLayout)
    if (generation !== version) return
    await current.importXML(xml)
    if (generation !== version) return
    signature = nextSignature
    layoutSignature = nextLayout
    ready.value = true
    incompleteNodes.value = null
    emit('inspect', null)
    showError('')
    current.get<Canvas>('canvas').zoom('fit-viewport')
    selectActive()
  } catch {
    if (generation === version) showError('流程图加载失败，请重新打开模板。')
  } finally {
    if (generation === version) {
      syncing = false
      if (pending) { pending = false; void render() }
    }
  }
}

async function init() {
  generation++
  instance?.destroy()
  syncing = pending = ready.value = false
  signature = ''
  layoutSignature = ''
  if (!host.value) return
  instance = props.edit ? new Modeler({ container: host.value, additionalModules: [
    { __init__: ['approvalPalette'], approvalPalette: ['type', ApprovalPalette] },
  ] }) : new NavigatedViewer({ container: host.value })
  instance.on('commandStack.changed', updateFromCanvas)
  instance.on('selection.changed', ({ newSelection }: { newSelection: Element[] }) => {
    if (syncing) return
    const selected = newSelection.length === 1 ? (newSelection[0].labelTarget ?? newSelection[0]) : undefined
    if (selected?.type === 'bpmn:SequenceFlow' && conditionalLinks.value.some(link => link.id === selected.id)) {
      emit('selectLink', selected.id)
      return
    }
    emit('select', selected && isTask(selected) ? selected.id : null)
  })
  // A normal click should keep the task inspector open when bpmn-js toggles an already selected shape off.
  instance.on('element.click', 900, ({ element, originalEvent }: { element: Element; originalEvent?: MouseEvent }) => {
    const task = element.labelTarget ?? element
    if (!isTask(task) || originalEvent?.button !== 0 || originalEvent.shiftKey) return
    const selection = instance?.get<Selection>('selection')
    if (selection?.get()[0] !== task) selection?.select(task)
  })
  await render(true)
}

function zoom(delta: number) {
  const canvas = instance?.get<Canvas>('canvas')
  if (canvas) canvas.zoom(delta === 0 ? 'fit-viewport' : Math.max(.2, Math.min(4, canvas.zoom() * delta)))
}

function history(action: 'undo' | 'redo') {
  if (props.edit) instance?.get<History>('commandStack')[action]()
}

onMounted(init)
onBeforeUnmount(() => { generation++; instance?.destroy(); instance = undefined })
watch(() => props.edit, init)
watch([() => describe(props.steps), describeLayout], () => render())
watch([() => props.activeKey, () => props.activeLink], selectActive)
</script>

<template>
  <section class="flow" aria-label="BPMN 流程图">
    <div class="flow-toolbar">
      <h2>BPMN 流程图</h2>
      <button v-if="edit" type="button" :disabled="!ready" @click="history('undo')">撤销</button>
      <button v-if="edit" type="button" :disabled="!ready" @click="history('redo')">重做</button>
      <button type="button" aria-label="缩小画布" :disabled="!ready" @click="zoom(.8)">−</button>
      <button type="button" :disabled="!ready" @click="zoom(0)">适应画布</button>
      <button type="button" aria-label="放大画布" :disabled="!ready" @click="zoom(1.25)">＋</button>
      <button v-if="edit && !diagramLayout?.routing" type="button" :disabled="!ready || !!error || steps.length >= 30" @click="$emit('add')">添加节点</button>
      <button v-if="edit" type="button" :disabled="!ready || !!error || !activeKey || steps.length >= 30"
        @click="$emit('addBranch')">添加条件分支</button>
      <button v-if="edit" type="button" :disabled="!ready" @click="render(true)">恢复有效草稿</button>
    </div>
    <div ref="host" class="bpmn-host" data-testid="bpmn-modeler"></div>
    <p class="flow-hint" v-if="!error">点击节点配置审批人。点击排他网关的出线配置金额条件和默认分支。</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <ol v-if="(incompleteNodes ?? steps).length" class="node-list" aria-label="审批节点">
      <li v-for="(node, index) in incompleteNodes ?? steps" :key="node.editorKey">
        <button type="button" :aria-pressed="node.editorKey === activeKey" @click="$emit('select', node.editorKey)">
          {{ node.name.trim() || '审批节点 ' + (index + 1) }}
        </button>
      </li>
    </ol>
    <ul v-if="conditionalLinks.length" class="node-list link-list" aria-label="条件连线">
      <li v-for="link in conditionalLinks" :key="link.id">
        <button type="button" :aria-pressed="link.id === activeLink" @click="$emit('selectLink', link.id)">{{ linkLabel(link) }}</button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.flow { min-width: 0; overflow: hidden; border: 1px solid #d4e0ec; border-radius: 8px; background: #fff; }
.flow-toolbar, .node-list { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; padding: 10px 12px; }
.flow-toolbar { border-bottom: 1px solid #e0e9f3; }
h2 { margin: 0 auto 0 0; font-size: 16px; }
button { min-height: 28px; font: inherit; background: #fff; border: 1px solid #c8d6e6; border-radius: 5px; color: #234c77; cursor: pointer; padding: 4px 8px; }
button:disabled { opacity: .45; cursor: default; }
.link-list { list-style: none; padding-inline-start: 12px; }
button[aria-pressed='true'] { background: #e9f2ff; border-color: #245baf; }
.bpmn-host { position: relative; height: clamp(430px, 58vh, 710px); background: #f9fbfe radial-gradient(#cad8e7 1px, transparent 1px) 0 0/18px 18px; }
.node-list { margin: 0; padding-left: 32px; column-gap: 22px; border-top: 1px solid #e0e9f3; }
.flow-hint { margin: 0; padding: 10px 12px; border-top: 1px solid #e0e9f3; color: #536780; font-size: 13px; }
.error { padding: 0 12px; color: #a21d2e; }
</style>
