import { BpmnModdle } from 'bpmn-moddle'
import { START, END, branchLabel, defaultWaypoints, taskPosition, type DiagramLayout, type DiagramNode, type DiagramPoint } from './approvalRouting'

export type { DiagramLayout, DiagramNode, DiagramPoint } from './approvalRouting'

type ShapeType = 'bpmn:StartEvent' | 'bpmn:UserTask' | 'bpmn:EndEvent' | 'bpmn:ExclusiveGateway'

const moddle = new BpmnModdle()

function shape(type: ShapeType, id: string, name: string, position: DiagramPoint, width: number, height: number) {
  return {
    id, element: moddle.create(type, { id, name }),
    bounds: { ...position, width, height },
  }
}

export async function approvalDiagram(nodes: DiagramNode[], layout?: DiagramLayout | null): Promise<string> {
  const tasks = nodes.map((node, index) => shape('bpmn:UserTask', node.editorKey, node.name, taskPosition(node, index), 130, 74))
  const gates = (layout?.routing?.gateways ?? []).map(gate => ({
    id: gate.id, element: moddle.create('bpmn:ExclusiveGateway', { id: gate.id }),
    bounds: { ...gate.position, width: 50, height: 50 },
  }))
  const shapes = [
    shape('bpmn:StartEvent', START, '发起申请', layout?.start ?? { x: 55, y: 144 }, 36, 36),
    ...tasks, ...gates,
    shape('bpmn:EndEvent', END, '审批结束',
      layout?.end ?? { x: Math.max(180, ...tasks.map(task => task.bounds.x + 211)), y: 144 }, 36, 36),
  ]
  const byId = new Map(shapes.map(item => [item.id, item]))
  const gateById = new Map(gates.map(item => [item.id, item.element]))
  const links = layout?.routing?.links ?? shapes.slice(1).map((target, index) => ({
    id: 'Sequence_' + index, source: shapes[index].id, target: target.id, operator: 'NONE' as const,
  }))
  const flows = links.map(link => {
    const flow = moddle.create('bpmn:SequenceFlow', {
      id: link.id, name: branchLabel(link), sourceRef: byId.get(link.source)!.element, targetRef: byId.get(link.target)!.element,
    })
    const gateway = gateById.get(link.source)
    if (gateway && link.operator === 'DEFAULT') gateway.default = flow
    return flow
  })
  const process = moddle.create('bpmn:Process', {
    id: 'Process_approval', flowElements: [...shapes.map(item => item.element), ...flows],
  })

  const diagramShapes = shapes.map(({ id, element, bounds }) => moddle.create('bpmndi:BPMNShape', {
    id: element.id + '_di', bpmnElement: element, bounds: moddle.create('dc:Bounds', bounds),
    ...(gateById.has(id) ? { isMarkerVisible: true } : {}),
  }))
  const diagramEdges = flows.map((flow, index) => moddle.create('bpmndi:BPMNEdge', {
    id: flow.id + '_di',
    bpmnElement: flow,
    waypoint: (layout?.waypoints[index] ?? defaultWaypoints(byId.get(links[index].source)!.bounds, byId.get(links[index].target)!.bounds))
      .map(({ x, y }) => moddle.create('dc:Point', { x, y })),
  }))
  const plane = moddle.create('bpmndi:BPMNPlane', {
    id: 'Plane_approval', bpmnElement: process, planeElement: [...diagramShapes, ...diagramEdges],
  })
  const diagram = moddle.create('bpmndi:BPMNDiagram', { id: 'Diagram_approval', plane })
  const definitions = moddle.create('bpmn:Definitions', {
    id: 'Definitions_approval',
    targetNamespace: 'urn:permission-admin:workflow',
    rootElements: [process],
    diagrams: [diagram],
  })
  const { xml } = await moddle.toXML(definitions)
  return xml
}
