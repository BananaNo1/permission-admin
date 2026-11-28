import { expect, test } from '@playwright/test'
import { fulfillApi } from './api-response'

test('saves a department manager selected from current department members', async ({ page }) => {
  const dept = { id: '1', name: '采购部', parentId: null, sortOrder: 0, status: 'ENABLED', version: 4, selectable: true }
  await page.route('**/api/auth/me', route => fulfillApi(route, { json: { id: '1', username: 'admin', admin: true, permissions: [] } }))
  await page.route('**/api/auth/menus', route => fulfillApi(route, { json: [
    { id: '80', parentId: null, name: '部门管理', type: 'PAGE', visible: true, path: '/system/dept', routeName: 'Dept', componentKey: 'system/dept' },
  ] }))
  await page.route('**/api/auth/csrf', route => fulfillApi(route, { json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/system/depts', route => fulfillApi(route, { json: [dept] }))
  await page.route('**/api/system/depts/1/manager-candidates?*', route => fulfillApi(route, {
    json: [{ id: '42', nickname: '王经理', username: 'manager' }],
  }))
  await page.route('**/api/system/depts/1', route => {
    expect(route.request().postDataJSON()).toMatchObject({ version: 4, managerAssignment: { userId: '42' } })
    return fulfillApi(route, { json: { ...dept, managerUserId: '42', version: 5 } })
  })
  await page.goto('/system/dept')
  await page.getByRole('button', { name: '编辑' }).click()
  await page.getByRole('textbox', { name: '部门负责人' }).click()
  await page.getByText('王经理（manager）', { exact: true }).click()
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('部门维护', { exact: true })).toHaveCount(0)
})
