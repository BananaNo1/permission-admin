import { ref } from 'vue'

export const notice = ref('')
let csrf: { token: string; headerName: string } | null = null

export class ApiError extends Error {
    constructor(public status: number, message: string) { super(message) }
}

export async function refreshCsrf() {
    csrf = await api<{ token: string; headerName: string }>('/auth/csrf')
}

export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
    const headers: Record<string, string> = {}
    if (method !== 'GET') {
        if (!csrf) await refreshCsrf()
        headers[csrf!.headerName] = csrf!.token
    }
    const form = body instanceof URLSearchParams
    if (body !== undefined) headers['Content-Type'] = form
        ? 'application/x-www-form-urlencoded' : 'application/json'
    let response: Response
    try {
        response = await fetch('/api' + path, {
            method, headers, credentials: 'same-origin',
            body: body === undefined ? undefined : form ? body as URLSearchParams : JSON.stringify(body),
        })
    } catch {
        notice.value = '连接失败，请确认后端已启动'
        throw new ApiError(0, notice.value)
    }
    const text = await response.text()
    let data: unknown = null
    try { data = text ? JSON.parse(text) : null }
    catch {
        notice.value = '服务返回了无法识别的响应，请检查后端日志'
        throw new ApiError(response.status, notice.value)
    }
    if (!response.ok) {
        notice.value = data !== null && typeof data === 'object' && 'message' in data && typeof data.message === 'string'
            ? data.message : '请求失败'
        if (response.status === 403) csrf = null
        if (response.status === 401) window.dispatchEvent(new Event('session-expired'))
        throw new ApiError(response.status, notice.value)
    }
    return data as T
}