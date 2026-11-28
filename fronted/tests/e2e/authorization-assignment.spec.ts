import { expect, test, type Page } from '@playwright/test'
import { fulfillApi } from './api-response'

const roles = [
  { id: '1', name: '超级管理员', code: 'SUPER_ADMIN', status: 'ENABLED', dataScope: 'ALL', version: 1 },
  { id: '2', name: '财务查看', code: 'FINANCE_READ', status: 'ENABLED', dataScope: 'DEPT', version: 2 },
  { id: '3', name: '运营专员', code: 'OPS_WRITE', status: 'ENABLED', dataScope: 'SELF', version: 3 },
  { id: '4', name: '旧角色', code: 'LEGACY', status: 'DISABLED', dataScope: 'SELF', version: 4 },
]
const menus = [
  { id: '1', parentId: null, name: '系统管理', type: 'DIRECTORY', visible: true, status: 'ENABLED' },
  { id: '10', parentId: '1', name: '用户管理', type: 'PAGE', path: '/system/users', routeName: 'SystemUser', componentKey: 'system/user', permissionCode: 'system:user:list', visible: true, status: 'ENABLED' },
  { id: '11', parentId: '10', name: 'query', type: 'BUTTON', permissionCode: 'system:user:query', visible: false, status: 'ENABLED' },
  { id: '12', parentId: '10', name: '新增用户', description: '在允许管理的部门下创建账号并设置初始密码。', type: 'BUTTON', permissionCode: 'system:user:create', visible: false, status: 'ENABLED' },
  { id: '40', parentId: '10', name: '导出用户名单', description: '下载数据范围内的员工账号清单。', type: 'BUTTON', permissionCode: 'custom:user:export', visible: false, status: 'ENABLED' },
  { id: '16', parentId: '1', name: '角色管理', type: 'PAGE', path: '/system/roles', routeName: 'SystemRole', componentKey: 'system/role', permissionCode: 'system:role:list', visible: true, status: 'ENABLED' },
]

async function mockAuthorization(page: Page) {
  await page.route('**/api/auth/me', route => fulfillApi(route, {
    json: { id: '1', username: 'grant-test', deptId: '1', admin: true, permissions: [] },
  }))
  await page.route('**/api/auth/menus', route => fulfillApi(route, { json: menus }))
  await page.route('**/api/auth/csrf', route => fulfillApi(route, { json: { token: 'test-token', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/system/roles', route => fulfillApi(route, { json: roles }))
  await page.route('**/api/system/menus', route => fulfillApi(route, { json: menus }))
  await page.route(url => url.pathname === '/api/system/users', route => fulfillApi(route, {
    json: { records: [{ id: '15', username: 'operator', nickname: '测试用户', deptId: '1', status: 'ENABLED', version: 5 }], total: 1, page: 1, pageSize: 20 },
  }))
}

test('filters roles, previews changes and excludes privileged roles from batch grants', async ({ page }) => {
  await mockAuthorization(page)
  await page.route('**/api/system/users/15/roles', route => route.request().method() === 'GET'
    ? fulfillApi(route, { json: ['2'] }) : fulfillApi(route, { status: 200 }))
  await page.goto('/system/users')
  await page.getByRole('button', { name: '分配角色', exact: true }).click()

  const panel = page.getByRole('region', { name: '角色列表' })
  const preview = page.getByRole('region', { name: '变更预览' })
  const save = page.getByRole('button', { name: '保存角色' })
  const search = panel.getByRole('textbox', { name: '搜索角色' })
  await expect(save).toBeDisabled()
  await search.fill('OPS')
  await expect(panel.getByText('超级管理员', { exact: true })).toHaveCount(0)
  await panel.getByRole('button', { name: '勾选筛选结果' }).click()
  await expect(preview.locator('.assignment-row').getByText('运营专员 · OPS_WRITE')).toBeVisible()
  await search.fill('')
  await expect(panel.getByRole('checkbox', { name: /超级管理员/ })).not.toBeChecked()
  await expect(panel.getByRole('checkbox', { name: /旧角色/ })).not.toBeChecked()
  await expect(panel.getByRole('button', { name: '勾选筛选结果' })).toBeDisabled()
  await panel.getByRole('checkbox', { name: '仅看已选' }).click()
  await expect(panel.getByText('超级管理员', { exact: true })).toHaveCount(0)
  await panel.getByRole('checkbox', { name: /财务查看/ }).click()
  await expect(preview.locator('.assignment-row').getByText('财务查看 · FINANCE_READ')).toBeVisible()
  await expect(preview.locator('.assignment-row').getByText('移除', { exact: true })).toBeVisible()

  const request = page.waitForRequest(req => req.method() === 'PUT' && req.url().endsWith('/api/system/users/15/roles'))
  await save.click()
  expect((await request).postDataJSON()).toEqual({ roleIds: ['3'], version: 5 })
  await expect(panel).toBeHidden()
})

test('selects permissions exactly and previews the required ancestors before saving', async ({ page }) => {
  await mockAuthorization(page)
  await page.route('**/api/system/roles/3/menus', route => route.request().method() === 'GET'
    ? fulfillApi(route, { json: ['1', '16'] }) : fulfillApi(route, { status: 200 }))
  await page.goto('/system/roles')
  await page.getByRole('row').filter({ hasText: 'OPS_WRITE' }).getByRole('button', { name: '授权', exact: true }).click()

  const tree = page.getByRole('region', { name: '权限树' })
  const preview = page.getByRole('region', { name: '变更预览' })
  const save = page.getByRole('button', { name: '保存授权' })
  const checkbox = (name: string) => tree.locator('.n-tree-node').filter({ hasText: name }).getByRole('checkbox')
  const search = tree.getByRole('textbox', { name: '搜索权限' })
  await expect(save).toBeDisabled()
  await tree.getByRole('button', { name: '展开全部' }).click()
  await checkbox('system:user:list').click()
  await expect(checkbox('system:user:query')).not.toBeChecked()
  await expect(checkbox('system:user:create')).not.toBeChecked()
  await checkbox('system:user:list').click()
  await expect(tree.getByText('新增用户', { exact: true })).toBeVisible()
  await expect(tree.getByText('在允许管理的部门下创建账号并设置初始密码。', { exact: true })).toBeVisible()
  await search.fill('员工账号清单')
  await expect(tree.getByText('导出用户名单', { exact: true })).toBeVisible()
  await expect(tree.getByText('custom:user:export', { exact: true })).toBeVisible()
  await search.fill('初始密码')
  await expect(tree.getByText('新增用户', { exact: true })).toBeVisible()
  await expect(tree.getByText('system:user:query', { exact: true })).toHaveCount(0)
  await search.fill('system:user:create')
  await expect(tree.getByRole('button', { name: '取消匹配项', exact: true })).toHaveCount(1)
  await expect(tree.getByText(/system:user:query/)).toHaveCount(0)
  await tree.getByRole('button', { name: '勾选匹配项' }).click()
  await expect(preview.locator('.assignment-row').getByText('页面 · 用户管理 · system:user:list')).toBeVisible()
  await expect(preview.locator('.assignment-row').getByText('新增用户 · system:user:create')).toBeVisible()
  await expect(preview.locator('.assignment-row').getByText('在允许管理的部门下创建账号并设置初始密码。')).toBeVisible()
  await search.fill('system:role:list')
  await tree.getByRole('button', { name: '取消匹配项' }).click()
  await expect(preview.locator('.assignment-row').getByText('页面 · 角色管理 · system:role:list')).toBeVisible()
  await expect(preview.locator('.assignment-row').getByText('移除', { exact: true })).toBeVisible()

  const request = page.waitForRequest(req => req.method() === 'PUT' && req.url().endsWith('/api/system/roles/3/menus'))
  await save.click()
  const body = (await request).postDataJSON()
  expect(body.version).toBe(3)
  expect(body.menuIds.sort()).toEqual(['1', '10', '12'])
  await expect(tree).toBeHidden()
})
