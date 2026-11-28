import type { PageResult } from './common'

export type WorkflowRights = { edit: boolean; publish: boolean; start: boolean }
export type WorkflowBusiness = WorkflowRights & { code: string; name: string }
export type WorkflowGrant = WorkflowRights & { userId: string | number }
export type WorkflowRelease = { code: string; name: string; version: number }
export type Reviewer = { id: number; name: string; username: string; enabled: boolean }
export type ReviewerRole = { id: string; name: string; code: string }
export type ReviewerSource = 'FIXED' | 'ROLE' | 'DEPARTMENT_MANAGER'
export type TemplateStep = {
    reviewerSource?: ReviewerSource
    roleId?: string | null
    name: string
    mode: string
    approverIds: number[]
    minAmount: number | null
    returnTo: string
    position?: DiagramPoint | null
    formKey?: string | null
    recordEntered?: boolean
    recordCompleted?: boolean
}
export type TemplateDraft = {
    code: string
    name: string
    revision: number
    publishedVersion: number | null
    dirty: boolean
    enabled?: boolean
    steps: TemplateStep[]
    diagramLayout?: DiagramLayout | null
}
export type TemplateVersion = { version: number; name: string; publishedAt: string; publishedBy: string }
export type TemplateVersionDetail = { name: string; steps: TemplateStep[] }
export type Decision = 'APPROVE' | 'REJECT' | 'RETURN'
export type CompletedTask = { taskId: string; instanceId: string; title: string; nodeName: string; action: string; completedAt: string }

export type Application = { id: string; title: string; description?: string; status: string; businessKey?: string; templateVersion?: number; templateCode?: string; applicantId?: string; amount?: string; startTime: string; endTime?: string; currentNodeNames?: string[] }
export type Task = { instanceId: string; id: string; title: string; description?: string; nodeName: string; type: 'REVIEW' | 'RESUBMIT'; returnAllowed: boolean; createTime: string; formKey?: string | null; nodeEnteredAt?: string | null }
type RecordedAction = { taskId: string; nodeName?: string; actorId?: string; action: string; comment?: string; time: string; listenerCompletedAt?: string | null }
type OperationRecord = { id: number; actorId: string; targetId?: string; operation: string; reason: string; createdAt: string }
type ActiveWork = { taskId: string; nodeName: string; actorId?: string; actorName?: string; createTime?: string; mine: boolean }
export type InstanceDetail = {
  application: Application; active: ActiveWork[]; ownTasks: Task[];
  actions: PageResult<RecordedAction>; actorNames: Record<string, string>
  operations?: OperationRecord[]; canWithdraw?: boolean; canTerminate?: boolean
}

export type DiagramPoint = { x: number; y: number }
export type BranchOperator = 'NONE' | 'DEFAULT' | 'GTE' | 'GT' | 'LTE' | 'LT' | 'EQ'
export type RouteLink = { id: string; source: string; target: string; operator: BranchOperator; amount?: string | number | null }
export type RoutingGraph = {
  taskIds: string[]
  gateways: { id: string; position: DiagramPoint }[]
  links: RouteLink[]
}
export type DiagramLayout = { start: DiagramPoint; end: DiagramPoint; waypoints: DiagramPoint[][]; routing?: RoutingGraph | null }
