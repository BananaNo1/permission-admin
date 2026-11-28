import { api } from './http'
import type { DeptVO, DeptCreateRequest, DeptUpdateRequest } from '@/types/dept'

const root = '/system/depts'

export function listDepts() {
    return api<DeptVO[]>(root)
}

export function createDept(body: DeptCreateRequest) {
    return api<DeptVO>(root, 'POST', body)
}

export function updateDept(id: string, body: DeptUpdateRequest) {
    return api<DeptVO>(root + '/' + id, 'PUT', body)
}

export function managerCandidates(id: string, keyword: string) {
    return api<{ id: string; username: string; nickname: string }[]>(root + '/' + encodeURIComponent(id)
        + '/manager-candidates?' + new URLSearchParams({ keyword }))
}

export function deleteDept(id: string, version: number) {
    return api<void>(root + '/' + id + '?version=' + version, 'DELETE')
}