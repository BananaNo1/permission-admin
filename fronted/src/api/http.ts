import { ref } from 'vue'

export const notice = ref('')
let csrf: { token: string; headerName: string } | null = null

interface ApiResponse<T> {
    code: number
    message: string
    data: T
}

function isApiResponse(value: unknown, status: number): value is ApiResponse<unknown> {
    return value !== null && typeof value === 'object'
        && 'code' in value && value.code === status
        && 'message' in value && typeof value.message === 'string'
        && 'data' in value
}

export class ApiError extends Error {
    constructor(public status: number, message: string, options?: ErrorOptions) {
        super(message, options)
    }
}

export const AUTHORIZATION_CHANGED = 'authorization-changed'

export async function changesAuthorization<T>(request: Promise<T>) {
    const result = await request
    window.dispatchEvent(new Event(AUTHORIZATION_CHANGED))
    return result
}

export async function refreshCsrf() {
    csrf = await api<{ token: string; headerName: string }>('/auth/csrf')
}

export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (method !== 'GET') {
        if (!csrf) await refreshCsrf()
        headers[csrf!.headerName] = csrf!.token
    }
    const form = body instanceof URLSearchParams
    if (body !== undefined) headers['Content-Type'] = form
        ? 'application/x-www-form-urlencoded' : 'application/json'
    const previousNotice = notice.value
    let response: Response
    try {
        response = await fetch('/api' + path, {
            method, headers, credentials: 'same-origin',
            body: body === undefined ? undefined : form ? body : JSON.stringify(body),
        })
    } catch (cause) {
        notice.value = '连接失败，请确认后端已启动'
        throw new ApiError(0, notice.value, { cause })
    }
    if (response.status === 403) csrf = null
    if (response.status === 401) window.dispatchEvent(new Event('session-expired'))

    const fallback = '请求失败（HTTP ' + response.status + '）'
    let payload: unknown
    try {
        payload = await response.json()
    } catch (cause) {
        notice.value = response.ok ? '服务返回了无法识别的响应' : fallback
        throw new ApiError(response.status, notice.value, { cause })
    }
    if (!response.ok) {
        notice.value = isApiResponse(payload, response.status) ? payload.message : fallback
        throw new ApiError(response.status, notice.value)
    }
    if (!isApiResponse(payload, response.status)) {
        notice.value = '服务返回了无法识别的响应'
        throw new ApiError(response.status, notice.value)
    }
    if (method !== 'GET' && notice.value === previousNotice) notice.value = ''
    return payload.data as T
}
