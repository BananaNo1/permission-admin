declare module 'bpmn-moddle' {
  import type { BpmnModdleTypeMap, BpmndiModdleTypeMap, DcModdleTypeMap } from 'bpmn-moddle/types'

  type ElementTypes = BpmnModdleTypeMap & BpmndiModdleTypeMap & DcModdleTypeMap

  export class BpmnModdle {
    create<K extends keyof ElementTypes>(type: K, attributes: Partial<ElementTypes[K]>): ElementTypes[K]
    toXML(definitions: ElementTypes['bpmn:Definitions']): Promise<{ xml: string }>
  }
}
