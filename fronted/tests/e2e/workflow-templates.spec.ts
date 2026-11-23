import { expect, test, type Page } from '@playwright/test'

const path = '/api/workflow/businesses/buy/templates'
const steps = [{ name: '主管审批', mode: 'SINGLE', approverIds: [2], minAmount: null, returnTo: 'APPLICANT' }]

async function openTemplates(page: Page, edit: boolean) {
  const draft = { code: 'order', name: '采购审批', revision: 7, publishedVersion: 1, dirty: true, steps }
  await page.route('**/api/auth/me', route => route.fulfill({ json: {
    id: '9', username: 'operator', deptId: '1', admin: false, permissions: ['workflow:template:list'],
  } }))
  await page.route('**/api/auth/menus', route => route.fulfill({ json: [{
    id: '90', parentId: null, name: '流程模板', type: 'PAGE', visible: true, path: '/workflow/templates',
    routeName: 'WorkflowTemplates', componentKey: 'workflow/templates', permissionCode: 'workflow:template:list',
  }] }))
  await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/workflow/businesses', route => route.fulfill({ json: [
    { code: 'buy', name: '采购业务', edit, publish: !edit, start: false },
  ] }))
  await page.route('**' + path, route => route.fulfill({ json: [draft] }))
  await page.route('**' + path + '/order', route => {
    Object.assign(draft, route.request().postDataJSON(), { revision: 8 })
    return route.fulfill({ status: 204 })
  })
  await page.route('**' + path + '/order/publish', route => route.fulfill({ status: 204 }))
  await page.goto('/workflow/templates')
}

test('business editor can save an existing draft with its current revision', async ({ page }) => {
  await openTemplates(page, true)
  const save = page.getByRole('button', { name: '保存草稿' })
  await expect(save).toBeVisible()
  await expect(page.getByRole('button', { name: '发布版本' })).toHaveCount(0)
  const name = page.getByRole('textbox', { name: '模板名称' })
  await expect(name).toHaveValue('采购审批')
  await name.fill('采购审批修订')
  const request = page.waitForRequest(req => req.method() === 'PUT' && req.url().endsWith(path + '/order'))
  await save.click()
  expect((await request).postDataJSON()).toMatchObject({ name: '采购审批修订', revision: 7 })
})

test('business publisher can publish the selected revision without editing', async ({ page }) => {
  await openTemplates(page, false)
  const publish = page.getByRole('button', { name: '发布版本' })
  await expect(publish).toBeEnabled()
  await expect(page.getByRole('button', { name: '保存草稿' })).toHaveCount(0)
  const request = page.waitForRequest(req => req.method() === 'POST' && req.url().endsWith(path + '/order/publish'))
  await publish.click()
  expect((await request).postDataJSON()).toEqual({ revision: 7 })
})
