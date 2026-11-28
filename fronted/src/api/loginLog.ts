import { api } from './http'
import type { PageResult } from '@/types/common'
import type { LoginLogVO } from '@/types/log'

export function listLoginLogs(page: number, pageSize: number) {
    return api<PageResult<LoginLogVO>>('/audit/login-logs?page=' + page + '&pageSize=' + pageSize)
}