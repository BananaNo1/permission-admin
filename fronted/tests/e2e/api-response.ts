import type { Route } from '@playwright/test'

export function fulfillApi(route: Route, { status = 200, json = null }: { status?: number; json?: unknown } = {}) {
  return route.fulfill({
    status,
    json: { code: status, message: status < 400 ? '成功' : 'HTTP ' + status, data: json },
  })
}
