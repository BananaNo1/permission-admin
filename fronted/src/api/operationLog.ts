import {api} from './http'
import type {PageResult} from '@/types/common'
import type {OperationLogVO} from '@/types/log'

export function listOperationLogs(page: number, pageSize: number) {
    return api<PageResult<OperationLogVO>>('/audit/operation-logs?page=' + page + '&pageSize=' + pageSize)
}