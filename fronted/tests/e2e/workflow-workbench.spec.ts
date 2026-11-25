import { expect, test } from '@playwright/test'

test('starts a published approval, updates pending work and reads its history', async ({ page }) => {
  const application = { id: 'p1', title: '办公用品采购', status: 'PENDING', businessKey: 'BUY-2026-001', templateVersion: 2 }
  const reviewerTask = { id: 't1', title: application.title, type: 'REVIEW', nodeName: '采购审批', returnAllowed: true }
  const returnedTask = { id: 't2', title: '退回申请', type: 'RESUBMIT', nodeName: '申请人修改', returnAllowed: false }
  let applications: typeof application[] = []
  let tasks = [reviewerTask, returnedTask]
  await page.route('**/api/auth/me', route => route.fulfill({ json: { id: '9', username: 'operator', admin: false, permissions: [] } }))
  await page.route('**/api/auth/menus', route => route.fulfill({ json: [
    { id: '90', parentId: null, name: '流程模板', type: 'PAGE', visible: true, path: '/workflow/templates', routeName: 'WorkflowTemplates', componentKey: 'workflow/templates' },
  ] }))
  await page.route('**/api/auth/csrf', route => route.fulfill({ json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/workflow/businesses', route => route.fulfill({ json: [
    { code: 'buy', name: '采购', edit: false, publish: false, start: true },
  ] }))
  await page.route('**/api/workflow/businesses/buy/templates/published', route => route.fulfill({ json: [
    { code: 'purchase', name: '采购审批', version: 2 },
  ] }))
  await page.route('**/api/workflow/businesses/buy/templates/purchase/applications', route => {
    expect(route.request().postDataJSON()).toMatchObject({ title: application.title, businessKey: application.businessKey, amount: '12.50' })
    applications = [application]
    return route.fulfill({ status: 201, json: { instanceId: 'p1' } })
  })
  await page.route('**/api/workflow/applications?page=*', route => route.fulfill({
    json: { records: applications, total: applications.length, page: 1, pageSize: 10 },
  }))
  await page.route('**/api/workflow/applications/p1/actions?page=*', route => route.fulfill({
    json: { records: [{ taskId: 'old', actorId: '2', action: 'APPROVE', comment: '已核对用途', time: '2026-11-25T08:00:00Z' }], total: 1 },
  }))
  await page.route('**/api/workflow/tasks?page=*', route => route.fulfill({ json: { records: tasks, total: tasks.length } }))
  await page.route('**/api/workflow/tasks/t1/decision', route => {
    expect(route.request().postDataJSON()).toEqual({ action: 'RETURN', comment: '补充用途' })
    tasks = tasks.filter(task => task.id !== 't1')
    return route.fulfill({ status: 204 })
  })
  await page.route('**/api/workflow/tasks/t2/resubmit', route => {
    expect(route.request().postDataJSON()).toMatchObject({ title: '补充后的申请' })
    tasks = []
    return route.fulfill({ status: 204 })
  })

  await page.goto('/workflow/templates')
  await page.getByRole('button', { name: '审批工作台' }).click()
  await expect(page.getByLabel('审批模板')).toHaveValue('purchase')
  await page.getByLabel('业务单号').fill(application.businessKey)
  await page.getByLabel('申请标题').fill(application.title)
  await page.getByLabel('金额', { exact: true }).fill('12.50')
  await page.getByRole('button', { name: '提交申请' }).click()
  await expect(page.getByText('申请已提交', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '审批记录' }).click()
  await expect(page.getByText('已核对用途')).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '我的待办' }).click()
  await page.getByRole('button', { name: '办理待办' }).click()
  await page.getByLabel('办理结果').selectOption('RETURN')
  await page.getByLabel('审批意见').fill('补充用途')
  await page.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByRole('button', { name: '办理待办' })).toHaveCount(0)
  await page.getByRole('button', { name: '修改并重提' }).click()
  await page.getByLabel('申请标题').fill('补充后的申请')
  await page.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByText('暂无待办')).toBeVisible()
})
