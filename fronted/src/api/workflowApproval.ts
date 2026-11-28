import { api } from './http'
import type { PageResult } from '@/types/common'
import type { Application, CompletedTask, Decision, InstanceDetail, Task } from '@/types/workflow'

type InboxQuery = { page: number; pageSize: number; keyword?: string; business?: string; status?: string }
const root = '/workflow'
const taskPath = (id: string) => root + '/tasks/' + encodeURIComponent(id)
const instancePath = (id: string) => root + '/instances/' + encodeURIComponent(id)

function inboxQuery({ page, pageSize, keyword, business, status }: InboxQuery) {
    const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
    if (keyword) query.set('keyword', keyword)
    if (business) query.set('business', business)
    if (status) query.set('status', status)
    return query.toString()
}

export function listWorkflowApplications(query: InboxQuery) {
    return api<PageResult<Application>>(root + '/applications?' + inboxQuery(query))
}

export function listWorkflowTasks(query: Omit<InboxQuery, 'status'>) {
    return api<PageResult<Task>>(root + '/tasks?' + inboxQuery({ ...query, status: undefined }))
}

export function listCompletedTasks(query: Omit<InboxQuery, 'status'>) {
    return api<PageResult<CompletedTask>>(root + '/tasks/completed?' + inboxQuery({ ...query, status: undefined }))
}

export function listWorkflowOperations(query: InboxQuery) {
    return api<PageResult<Application>>(root + '/operations/instances?' + inboxQuery(query))
}

export function getApprovalDetail(id: string, page: number, pageSize: number) {
    return api<InstanceDetail>(instancePath(id) + '?' + inboxQuery({ page, pageSize }))
}

export function decideApprovalTask(id: string, action: Decision, comment: string) {
    return api<void>(taskPath(id) + '/decision', 'POST', { action, comment })
}

export function resubmitApprovalTask(id: string, body: { title: string; description: string }) {
    return api<void>(taskPath(id) + '/resubmit', 'POST', body)
}

export function listTransferCandidates(id: string, keyword: string) {
    return api<{ id: string | number; name: string; username: string }[]>(
        taskPath(id) + '/transfer-candidates?' + new URLSearchParams({ keyword }))
}

export function transferApprovalTask(id: string, userId: string, reason: string) {
    return api<void>(taskPath(id) + '/transfer', 'POST', { userId, reason })
}

export function withdrawApprovalInstance(id: string, reason: string) {
    return api<void>(instancePath(id) + '/withdraw', 'POST', { reason })
}

export function terminateApprovalInstance(id: string, reason: string) {
    return api<void>(instancePath(id) + '/terminate', 'POST', { reason })
}
