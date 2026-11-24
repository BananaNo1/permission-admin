import { BpmnModdle } from 'bpmn-moddle'

export type DiagramPoint = { x: number; y: number }
export type DiagramLayout = { start: DiagramPoint; end: DiagramPoint; waypoints: DiagramPoint[][] }
export type DiagramNode = { editorKey: string; name: string; position?: DiagramPoint | null }

type ShapeType = 'bpmn:StartEvent' | 'bpmn:UserTask' | 'bpmn:EndEvent'
type Bounds = DiagramPoint & { width: number; height: number }

const moddle = new BpmnModdle()

function shape(type: ShapeType, id: string, name: string, position: DiagramPoint, width: number, height: number) {
  return {
    element: moddle.create(type, { id, name }),
    bounds: { ...position, width, height },
  }
}

function defaultWaypoints(from: Bounds, to: Bounds): DiagramPoint[] {
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

export async function approvalDiagram(nodes: DiagramNode[], layout?: DiagramLayout | null): Promise<string> {
  const tasks = nodes.map((node, index) => shape('bpmn:UserTask', node.editorKey, node.name,
    node.position ?? { x: 160 + index * 235, y: 125 }, 130, 74))
  const shapes = [
    shape('bpmn:StartEvent', 'Start_approval', '发起申请', layout?.start ?? { x: 55, y: 144 }, 36, 36),
    ...tasks,
    shape('bpmn:EndEvent', 'End_approval', '审批结束',
      layout?.end ?? { x: Math.max(180, ...tasks.map(task => task.bounds.x + 211)), y: 144 }, 36, 36),
  ]
  const flows = shapes.slice(1).map((target, index) => moddle.create('bpmn:SequenceFlow', {
    id: 'Sequence_' + index, sourceRef: shapes[index].element, targetRef: target.element,
  }))
  const process = moddle.create('bpmn:Process', {
    id: 'Process_approval', flowElements: [...shapes.map(item => item.element), ...flows],
  })

  const diagramShapes = shapes.map(({ element, bounds }) => moddle.create('bpmndi:BPMNShape', {
    id: element.id + '_di', bpmnElement: element, bounds: moddle.create('dc:Bounds', bounds),
  }))
  const diagramEdges = flows.map((flow, index) => moddle.create('bpmndi:BPMNEdge', {
    id: flow.id + '_di',
    bpmnElement: flow,
    waypoint: (layout?.waypoints[index] ?? defaultWaypoints(shapes[index].bounds, shapes[index + 1].bounds))
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
