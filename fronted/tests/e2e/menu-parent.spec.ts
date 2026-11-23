import { expect, test } from '@playwright/test'

const system = { id: '1', parentId: null, name: '系统管理', type: 'DIRECTORY', visible: true }
const menus = [
  system,
  { id: '27', parentId: '1', name: '菜单管理', type: 'PAGE', visible: true, path: '/system/menus', routeName: 'SystemMenu', componentKey: 'system/menu' },
  { id: '28', parentId: '27', name: 'query', type: 'BUTTON', visible: true },
  { id: '29', parentId: '27', name: 'create', type: 'BUTTON', visible: true },
]

test('filters parent menus by the new menu type', async ({ page }) => {
  await page.route('**/api/auth/me', route => route.fulfill({
    json: { id: '1', username: 'menu-test', deptId: '1', admin: true, permissions: [] },
  }))
  await page.route('**/api/auth/menus', route => route.fulfill({ json: menus }))
  await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'test-token', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/system/menus', route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: menus })
    return route.fulfill({ json: { id: '30', ...route.request().postDataJSON() } })
  })

  await page.goto('/system/menus')
  await page.getByRole('button', { name: '新增菜单' }).click()
  const form = page.locator('.n-modal form')
  const field = (name: string) => form.locator('.n-form-item').filter({
    has: page.getByText(name, { exact: true }),
  })
  const parent = field('父菜单').locator('.n-base-selection')
  const type = field('类型').locator('.n-base-selection')
  const options = page.locator('.n-base-select-menu:visible')

  await parent.click()
  await expect(options.getByText('系统管理', { exact: true })).toBeVisible()
  for (const invalid of ['菜单管理', 'query', 'create']) {
    await expect(options.getByText(invalid, { exact: true })).toHaveCount(0)
  }
  await options.getByText('系统管理', { exact: true }).click()

  await type.click()
  await options.getByText('页面', { exact: true }).click()
  await expect(parent.getByText('系统管理', { exact: true })).toBeVisible()

  await type.click()
  await options.getByText('按钮', { exact: true }).click()
  await expect(parent.getByText('系统管理', { exact: true })).toHaveCount(0)
  await expect(form.getByRole('button', { name: '保存' })).toBeDisabled()
  await parent.click()
  await expect(options.getByText('菜单管理', { exact: true })).toBeVisible()
  for (const invalid of ['系统管理', 'query', 'create']) {
    await expect(options.getByText(invalid, { exact: true })).toHaveCount(0)
  }
  await options.getByText('菜单管理', { exact: true }).click()
  await field('名称').getByRole('textbox').fill('导出菜单')
  await field('权限标识').getByRole('textbox').fill('system:menu:export')
  await field('用途说明').getByRole('textbox').fill('导出菜单清单。')
  const posted = page.waitForRequest(request => request.method() === 'POST' && request.url().endsWith('/api/system/menus'))
  await form.getByRole('button', { name: '保存' }).click()
  expect((await posted).postDataJSON()).toMatchObject({ type: 'BUTTON', parentId: '27', permissionCode: 'system:menu:export', name: '导出菜单', description: '导出菜单清单。' })
  await expect(form).toBeHidden()
})
