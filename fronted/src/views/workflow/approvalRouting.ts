import type { BranchOperator, DiagramLayout, DiagramPoint, RouteLink, RoutingGraph } from '@/types/workflow'
export type { BranchOperator, DiagramLayout, DiagramPoint, RouteLink, RoutingGraph } from '@/types/workflow'

export type DiagramNode = { editorKey: string; name: string; position?: DiagramPoint | null }
export type Bounds = DiagramPoint & { width: number; height: number }

export const START = 'Start_approval'
export const END = 'End_approval'
export const operators: Record<Exclude<BranchOperator, 'NONE'>, string> = {
  DEFAULT: '默认分支', GTE: '≥', GT: '>', LTE: '≤', LT: '<', EQ: '=',
}

export function branchLabel(link: RouteLink) {
  if (link.operator === 'DEFAULT') return operators.DEFAULT
  if (link.operator === 'NONE') return ''
  return link.amount == null || link.amount === '' ? '条件未配置' : '金额 ' + operators[link.operator] + ' ' + link.amount
}

export function defaultWaypoints(from: Bounds, to: Bounds): DiagramPoint[] {
  const start = { x: from.x + from.width, y: from.y + from.height / 2 }
  const end = { x: to.x, y: to.y + to.height / 2 }
  if (end.x >= start.x) {
    if (start.y === end.y) return [start, end]
    const middle = (start.x + end.x) / 2
    return [start, { x: middle, y: start.y }, { x: middle, y: end.y }, end]
  }
  const turn = start.x + 30
  const height = Math.max(start.y, end.y) + 75
  return [start, { x: turn, y: start.y }, { x: turn, y: height },
    { x: end.x - 30, y: height }, { x: end.x - 30, y: end.y }, end]
}

export function taskPosition(node: DiagramNode, index: number) {
  return node.position ?? { x: 160 + index * 235, y: 125 }
}

export function routedWaypoints(nodes: DiagramNode[], layout: DiagramLayout, graph: RoutingGraph) {
  const shapes = new Map<string, Bounds>([
    [START, { ...layout.start, width: 36, height: 36 }],
    [END, { ...layout.end, width: 36, height: 36 }],
  ])
  for (const [index, node] of nodes.entries()) {
    shapes.set(node.editorKey, { ...taskPosition(node, index), width: 130, height: 74 })
  }
  for (const gate of graph.gateways) {
    shapes.set(gate.id, { ...gate.position, width: 50, height: 50 })
  }
  return graph.links.map(link => defaultWaypoints(shapes.get(link.source)!, shapes.get(link.target)!)
    .map(({ x, y }) => ({ x: Math.round(x), y: Math.round(y) })))
}

export function isSplit(graph: RoutingGraph, source: string) {
  return graph.gateways.some(gate => gate.id === source) && graph.links.filter(link => link.source === source).length > 1
}
