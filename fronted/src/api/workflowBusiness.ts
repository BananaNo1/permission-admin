import { api } from './http'
import type { WorkflowBusiness, WorkflowGrant, WorkflowRights } from '@/types/workflow'

const root = '/workflow/businesses'
const grantsPath = (business: string) => root + '/' + encodeURIComponent(business) + '/grants'

export function listWorkflowBusinesses() {
    return api<WorkflowBusiness[]>(root)
}

export function createWorkflowBusiness(body: { code: string; name: string }) {
    return api<void>(root, 'POST', body)
}

export function listWorkflowGrants(business: string) {
    return api<WorkflowGrant[]>(grantsPath(business))
}

export function setWorkflowGrant(business: string, userId: string, body: WorkflowRights) {
    return api<void>(grantsPath(business) + '/' + encodeURIComponent(userId), 'PUT', body)
}
