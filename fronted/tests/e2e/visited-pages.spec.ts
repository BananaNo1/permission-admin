import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

const userPage = { id: '10', parentId: '1', name: '用户管理', type: 'PAGE', visible: true, path: '/system/users', routeName: 'SystemUser', componentKey: 'system/user' }
const rolePage = { id: '16', parentId: '1', name: '角色管理', type: 'PAGE', visible: true, path: '/system/roles', routeName: 'SystemRole', componentKey: 'system/role' }

const deptPage = { id: '22', parentId: '1', name: '部门管理', type: 'PAGE', visible: true, path: '/system/depts', routeName: 'SystemDept', componentKey: 'system/dept' }

async function mockPermissions(page: Page) {
  let menus = [userPage, rolePage, deptPage]
  await page.route('**/api/auth/me', route => route.fulfill({
    json: { id: '1', username: 'tab-test', deptId: '1', admin: true, permissions: [] },
  }))
  await page.route('**/api/auth/menus', route => route.fulfill({
    json: [{ id: '1', parentId: null, type: 'DIR', name: '系统管理', visible: true }, ...menus],
  }))
  await page.route(url => url.pathname === '/api/system/users', route => route.fulfill({
    json: { records: [], total: 0, page: 1, pageSize: 20 },
  }))
  await page.route('**/api/system/roles', route => route.fulfill({ json: [] }))
  await page.route('**/api/system/depts', route => route.fulfill({ json: [] }))
  return { revokeRoles: () => { menus = [userPage] } }
}

test('opens, switches and closes pages while keeping home pinned', async ({ page }) => {
  await mockPermissions(page)
  await page.goto('/')
  const tabs = page.getByRole('navigation', { name: '已访问页面' })
  const sidebar = page.getByRole('navigation', { name: '后台导航' })
  await expect(tabs.getByRole('link', { name: '首页' })).toBeVisible()
  await expect(tabs.getByRole('button', { name: '关闭首页' })).toHaveCount(0)
  await sidebar.getByText('系统管理', { exact: true }).click()
  await sidebar.getByText('用户管理', { exact: true }).click()
  await expect(tabs.getByRole('link', { name: '用户管理' })).toHaveAttribute('aria-current', 'page')
  await sidebar.getByText('角色管理', { exact: true }).click()
  await expect(tabs.getByRole('link', { name: '角色管理' })).toHaveAttribute('aria-current', 'page')
  await tabs.getByRole('button', { name: '关闭角色管理' }).click()
  await expect(page).toHaveURL(/\/system\/users$/)
  await sidebar.getByText('角色管理', { exact: true }).click()
  await tabs.getByRole('link', { name: '用户管理' }).click()
  await expect(page).toHaveURL(/\/system\/users$/)
  await expect(tabs.getByRole('link', { name: '用户管理' })).toHaveCount(1)
  await tabs.getByRole('button', { name: '关闭角色管理' }).click()
  await expect(page).toHaveURL(/\/system\/users$/)
  await expect(tabs.getByRole('link', { name: '角色管理' })).toHaveCount(0)
  await tabs.getByRole('button', { name: '关闭用户管理' }).click()
  await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible()
  await expect(tabs.getByRole('link')).toHaveCount(1)
})

test('opens a direct link and drops a page after its permission is revoked', async ({ page }) => {
  const permissions = await mockPermissions(page)
  await page.goto('/system/users?source=bookmark#list')
  const tabs = page.getByRole('navigation', { name: '已访问页面' })
  await expect(tabs.getByRole('link', { name: '用户管理' })).toHaveAttribute('href', '/system/users?source=bookmark#list')
  await tabs.getByRole('link', { name: '首页' }).click()
  await page.getByRole('link', { name: /角色管理/ }).click()
  await expect(tabs.getByRole('link', { name: '角色管理' })).toHaveAttribute('aria-current', 'page')
  permissions.revokeRoles()
  await tabs.getByRole('link', { name: '用户管理' }).click()
  await expect(page).toHaveURL(/\/system\/users\?source=bookmark#list$/)
  await expect(tabs.getByRole('link', { name: '角色管理' })).toHaveCount(0)
})

test('right-click closes tabs relative to the selected tab and keeps home pinned', async ({ page }) => {
  await mockPermissions(page)
  await page.goto('/')
  const tabs = page.getByRole('navigation', { name: '已访问页面' })
  const sidebar = page.getByRole('navigation', { name: '后台导航' })
  await sidebar.getByText('系统管理', { exact: true }).click()
  for (const name of ['用户管理', '角色管理', '部门管理']) {
    await sidebar.getByText(name, { exact: true }).click()
    await expect(tabs.getByRole('link', { name })).toHaveAttribute('aria-current', 'page')
  }

  const menu = page.getByRole('menu', { name: '历史标签操作' })
  await tabs.getByRole('link', { name: '角色管理' }).click({ button: 'right' })
  await expect(menu).toBeVisible()
  await expect(page).toHaveURL(/\/system\/depts$/)
  await menu.getByRole('menuitem', { name: '关闭左侧标签' }).click()
  await expect(tabs.getByRole('link', { name: '用户管理' })).toHaveCount(0)
  await expect(page).toHaveURL(/\/system\/depts$/)

  await tabs.getByRole('link', { name: '角色管理' }).click({ button: 'right' })
  await expect(menu.getByRole('menuitem', { name: '关闭左侧标签' })).toBeDisabled()
  await menu.getByRole('menuitem', { name: '关闭右侧标签' }).click()
  await expect(tabs.getByRole('link', { name: '部门管理' })).toHaveCount(0)
  await expect(page).toHaveURL(/\/system\/roles$/)

  await tabs.getByRole('link', { name: '角色管理' }).press('Shift+F10')
  await expect(menu).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(menu).toBeHidden()
  await tabs.getByRole('link', { name: '角色管理' }).click({ button: 'right' })
  await menu.getByRole('menuitem', { name: '关闭当前标签' }).click()
  await expect(tabs.getByRole('link', { name: '首页' })).toHaveAttribute('aria-current', 'page')
  await expect(tabs.getByRole('link')).toHaveCount(1)

  await sidebar.getByText('用户管理', { exact: true }).click()
  await sidebar.getByText('部门管理', { exact: true }).click()
  await tabs.getByRole('link', { name: '首页' }).click({ button: 'right' })
  await expect(menu.getByRole('menuitem', { name: '关闭当前标签' })).toBeDisabled()
  await page.getByRole('heading', { name: '部门管理' }).click()
  await expect(menu).toBeHidden()
  await tabs.getByRole('link', { name: '首页' }).click({ button: 'right' })
  await menu.getByRole('menuitem', { name: '关闭全部标签' }).click()
  await expect(tabs.getByRole('link', { name: '首页' })).toHaveAttribute('aria-current', 'page')
  await expect(tabs.getByRole('link')).toHaveCount(1)
})
