import { expect, test } from '@playwright/test'
import { fulfillApi } from './api-response'

test('starts a published approval, updates pending work and reads its history', async ({ page }, testInfo) => {
  const application = { id: 'p1', title: '办公用品采购', status: 'PENDING', businessKey: 'BUY-2026-001', templateVersion: 2, currentNodeNames: ['采购审批'] }
  const reviewerTask = { id: 't1', title: application.title, type: 'REVIEW', nodeName: '采购审批', returnAllowed: true }
  const returnedTask = { id: 't2', title: '退回申请', type: 'RESUBMIT', nodeName: '申请人修改', returnAllowed: false }
  let applications: typeof application[] = []
  let tasks = [reviewerTask, returnedTask]
  await page.route('**/api/auth/me', route => fulfillApi(route, { json: { id: '9', username: 'operator', admin: false, permissions: [] } }))
  await page.route('**/api/auth/menus', route => fulfillApi(route, { json: [
    { id: '90', parentId: null, name: '流程模板', type: 'PAGE', visible: true, path: '/workflow/templates', routeName: 'WorkflowTemplates', componentKey: 'workflow/templates' },
  ] }))
  await page.route('**/api/auth/csrf', route => fulfillApi(route, { json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/workflow/businesses', route => fulfillApi(route, { json: [
    { code: 'buy', name: '采购', edit: false, publish: false, start: true },
  ] }))
  await page.route('**/api/workflow/businesses/buy/templates/published', route => fulfillApi(route, { json: [
    { code: 'purchase', name: '采购审批', version: 2 },
  ] }))
  await page.route('**/api/workflow/businesses/buy/templates/purchase/applications', route => {
    expect(route.request().postDataJSON()).toMatchObject({ title: application.title, businessKey: application.businessKey, amount: '12.50' })
    applications = [application]
    return fulfillApi(route, { status: 201, json: { instanceId: 'p1' } })
  })
  await page.route('**/api/workflow/applications?page=*', route => fulfillApi(route, {
    json: { records: applications, total: applications.length, page: 1, pageSize: 10 },
  }))
  await page.route('**/api/workflow/instances/p1?page=*', route => fulfillApi(route, { json: {
    application, active: [], ownTasks: [], actorNames: { '2': '李四' },
    actions: { records: [{ taskId: 'old', nodeName: '采购审批', actorId: '2', action: 'APPROVE', comment: '已核对用途', time: '2026-11-25T08:00:00Z' }], total: 1 },
  } }))
  await page.route('**/api/workflow/tasks?page=*', route => fulfillApi(route, { json: { records: tasks, total: tasks.length } }))
  await page.route('**/api/workflow/tasks/t1/decision', route => {
    expect(route.request().postDataJSON()).toEqual({ action: 'RETURN', comment: '补充用途' })
    tasks = tasks.filter(task => task.id !== 't1')
    return fulfillApi(route)
  })
  await page.route('**/api/workflow/tasks/t2/resubmit', route => {
    expect(route.request().postDataJSON()).toMatchObject({ title: '补充后的申请' })
    tasks = []
    return fulfillApi(route)
  })

  await page.goto('/workflow/templates')
  await page.getByRole('button', { name: '审批工作台' }).click()
  await expect(page.getByLabel('审批模板')).toHaveValue('purchase')
  await page.getByLabel('业务单号').fill(application.businessKey)
  await page.getByLabel('申请标题').fill(application.title)
  await page.getByLabel('金额', { exact: true }).fill('12.50')
  await page.getByRole('button', { name: '提交申请' }).click()
  await expect(page.getByText('申请已提交', { exact: true })).toBeVisible()
  const inbox = page.getByRole('region', { name: '我的申请查询结果' })
  const refresh = inbox.getByRole('button', { name: '刷新列表' })
  const list = inbox.getByRole('table', { name: '我的申请' })
  await expect(list.getByRole('columnheader', { name: '操作' })).toBeVisible()
  await expect(list).not.toContainText('模板 v2')
  const row = list.getByRole('row').filter({ hasText: application.title })
  await expect(row.getByRole('cell', { name: '采购审批', exact: true })).toBeVisible()
  await expect(row.getByRole('button', { name: '审批记录' })).toBeVisible()
  await page.screenshot({ animations: 'disabled', path: testInfo.outputPath('approval-inbox-desktop.png') })
  const reload = page.waitForResponse(response => response.url().includes('/api/workflow/applications?') && response.ok())
  await refresh.click()
  await reload
  const desktop = page.viewportSize()
  await page.locator('.n-layout-toggle-button').click()
  await page.setViewportSize({ width: 390, height: 760 })
  await expect(row.getByRole('button', { name: '审批记录' })).toBeInViewport()
  await expect(refresh).toBeInViewport()
  await page.screenshot({ animations: 'disabled', path: testInfo.outputPath('approval-inbox-mobile.png') })
  if (desktop) await page.setViewportSize(desktop)
  await page.locator('.n-layout-toggle-button').click()
  await page.getByRole('button', { name: '审批记录' }).click()
  await expect(page.getByText('已核对用途')).toBeVisible()
  await expect(page.getByRole('dialog', { name: '审批详情' }).getByText('v2', { exact: true })).toBeVisible()
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

test('opens independent inbox routes and moves processed work to completed', async ({ page }, testInfo) => {
  const application = { id: 'p7', title: '打印机采购', status: 'PENDING', businessKey: 'BUY-007', applicantId: '1',
    amount: '1200.00', templateCode: 'purchase', templateVersion: 3, startTime: '2026-11-25T08:00:00Z',
    description: '用于办公室日常打印，请核对采购数量。' }
  const task = { id: 't7', instanceId: 'p7', title: application.title, nodeName: '部门审批', type: 'REVIEW', returnAllowed: true }
  let completed = false
  await page.route('**/api/auth/me', route => fulfillApi(route, { json: { id: '2', username: 'reviewer', admin: false, permissions: [] } }))
  await page.route('**/api/auth/menus', route => fulfillApi(route, { json: [
    { id: '91', parentId: null, name: '我的待办', type: 'PAGE', visible: true, path: '/workflow/tasks', routeName: 'Tasks', componentKey: 'workflow/tasks' },
    { id: '92', parentId: null, name: '我的已办', type: 'PAGE', visible: true, path: '/workflow/completed', routeName: 'Completed', componentKey: 'workflow/completed' },
  ] }))
  await page.route('**/api/auth/csrf', route => fulfillApi(route, { json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/workflow/businesses', route => fulfillApi(route, { json: [{ code: 'buy', name: '采购', start: false }] }))
  await page.route('**/api/workflow/tasks?page=*', route => fulfillApi(route, {
    json: { records: completed ? [] : [task], total: completed ? 0 : 1 },
  }))
  await page.route('**/api/workflow/tasks/completed?page=*', route => fulfillApi(route, {
    json: { records: completed ? [{ taskId: 't7', instanceId: 'p7', title: application.title, nodeName: task.nodeName,
      action: 'APPROVE', completedAt: '2026-11-25T09:00:00Z' }] : [], total: completed ? 1 : 0 },
  }))
  await page.route('**/api/workflow/instances/p7?page=*', route => fulfillApi(route, { json: {
    application: { ...application, status: completed ? 'APPROVED' : 'PENDING', endTime: completed ? '2026-11-25T09:00:00Z' : null },
    active: completed ? [] : [{ taskId: 't7', nodeName: task.nodeName, actorId: '2', actorName: '张三', createTime: '2026-11-25T08:00:00Z', mine: true }],
    ownTasks: completed ? [] : [task], actorNames: { '1': '初始管理员', '2': '张三' },
    actions: { records: completed ? [{ taskId: 't7', nodeName: '部门审批', actorId: '2', action: 'APPROVE', comment: '确认采购', time: '2026-11-25T09:00:00Z', listenerCompletedAt: '2026-11-25T09:00:00Z' }] : [],
      total: completed ? 1 : 0 },
  } }))
  await page.route('**/api/workflow/tasks/t7/decision', route => {
    expect(route.request().postDataJSON()).toEqual({ action: 'APPROVE', comment: '确认采购' })
    completed = true
    return fulfillApi(route)
  })
  await page.goto('/workflow/tasks')
  await expect(page.getByRole('heading', { name: '我的待办' })).toBeVisible()
  await page.getByRole('searchbox', { name: '关键词' }).fill('打印机')
  await page.getByRole('combobox', { name: '业务', exact: true }).selectOption('buy')
  const filter = page.waitForRequest(request => {
    const params = new URL(request.url()).searchParams
    return params.get('keyword') === '打印机' && params.get('business') === 'buy'
  })
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await filter
  await page.getByRole('button', { name: '查看详情' }).click()
  const current = page.getByRole('list', { name: '当前办理人员' })
  await expect(current).toContainText('部门审批')
  await expect(current).toContainText('张三')
  const timeline = page.getByRole('list', { name: '审批轨迹记录' })
  await expect(timeline.getByRole('listitem')).toHaveText([/发起申请.*初始管理员/])
  await page.screenshot({ animations: 'disabled', path: testInfo.outputPath('approval-current.png') })
  await page.getByRole('button', { name: '处理任务' }).click()
  await page.getByLabel('审批意见').fill('确认采购')
  await page.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByRole('heading', { name: '流程已结束' })).toBeVisible()
  await expect(current).toHaveCount(0)
  await expect(timeline.getByRole('listitem')).toHaveText([/部门审批.*通过.*张三.*确认采购/, /发起申请.*初始管理员/])
  await expect(page.getByText('任务监听器记录')).toBeHidden()
  await page.getByText('节点执行信息').click()
  await expect(page.getByText('任务监听器记录')).toBeVisible()
  await page.getByText('节点执行信息').click()
  await page.screenshot({ animations: 'disabled', path: testInfo.outputPath('approval-completed.png') })
  await page.setViewportSize({ width: 390, height: 760 })
  const dialog = page.getByRole('dialog', { name: '审批详情' })
  const bounds = await dialog.boundingBox()
  expect(bounds?.width).toBeLessThanOrEqual(390)
  await expect(page.getByText('1200.00')).toBeVisible()
  await page.screenshot({ animations: 'disabled', path: testInfo.outputPath('approval-mobile.png') })
  await page.keyboard.press('Escape')
  await expect(page.getByText('暂无待办')).toBeVisible()
  await page.goto('/workflow/completed')
  await expect(page.getByRole('heading', { name: '我的已办' })).toBeVisible()
  await page.getByRole('button', { name: '查看详情' }).click()
  await expect(page.getByText('确认采购')).toBeVisible()
})

test('transfers a pending approval to the applicant with an explicit reason', async ({ page }) => {
  const item = { id: 'transfer-task', instanceId: 'transfer-instance', title: '转办测试', nodeName: '审批', type: 'REVIEW', returnAllowed: true }
  let pending = true
  await page.route('**/api/auth/me', route => fulfillApi(route, { json: { id: '2', username: 'reviewer', admin: false, permissions: [] } }))
  await page.route('**/api/auth/menus', route => fulfillApi(route, { json: [
    { id: '91', parentId: null, name: '我的待办', type: 'PAGE', visible: true, path: '/workflow/tasks', routeName: 'Tasks', componentKey: 'workflow/tasks' },
  ] }))
  await page.route('**/api/auth/csrf', route => fulfillApi(route, { json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/workflow/businesses', route => fulfillApi(route, { json: [] }))
  await page.route('**/api/workflow/tasks?page=*', route => fulfillApi(route, { json: { records: pending ? [item] : [], total: pending ? 1 : 0 } }))
  await page.route('**/api/workflow/tasks/transfer-task/transfer-candidates?*', route => fulfillApi(route, {
    json: [{ id: '11', name: '申请人', username: 'applicant' }],
  }))
  await page.route('**/api/workflow/tasks/transfer-task/transfer', route => {
    expect(route.request().postDataJSON()).toEqual({ userId: '11', reason: '转交给申请人处理' })
    pending = false
    return fulfillApi(route)
  })
  await page.goto('/workflow/tasks')
  await page.getByRole('button', { name: '办理待办' }).click()
  await page.getByRole('button', { name: '转办', exact: true }).click()
  await page.getByRole('textbox', { name: '转办接收人' }).click()
  await page.getByText('申请人（applicant）', { exact: true }).click()
  await page.getByRole('textbox', { name: '操作原因' }).fill('转交给申请人处理')
  await page.getByRole('button', { name: '确认转办' }).click()
  await expect(page.getByText('任务已转办', { exact: true })).toBeVisible()
  await expect(page.getByText('暂无待办', { exact: true })).toBeVisible()
})

test('administrator filters instances by engine errors and terminates one', async ({ page }) => {
  const application = { id: 'ops-instance', title: '异常测试实例', status: 'PENDING' }
  let ended = false
  await page.route('**/api/auth/me', route => fulfillApi(route, { json: { id: '1', username: 'admin', admin: true, permissions: [] } }))
  await page.route('**/api/auth/menus', route => fulfillApi(route, { json: [
    { id: '95', parentId: null, name: '流程运维', type: 'PAGE', visible: true, path: '/workflow/operations', routeName: 'Operations', componentKey: 'workflow/operations' },
  ] }))
  await page.route('**/api/auth/csrf', route => fulfillApi(route, { json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/workflow/businesses', route => fulfillApi(route, { json: [] }))
  await page.route('**/api/workflow/operations/instances?page=*', route => fulfillApi(route, {
    json: { records: ended ? [] : [application], total: ended ? 0 : 1 },
  }))
  await page.route('**/api/workflow/instances/ops-instance?page=*', route => fulfillApi(route, { json: {
    application: { ...application, status: ended ? 'CANCELLED' : application.status },
    active: [], ownTasks: [], actions: { records: [], total: 0 }, actorNames: { '1': '管理员' },
    canTerminate: !ended, operations: ended ? [{ id: 1, actorId: '1', operation: 'TERMINATE',
      reason: '外部依赖失败', createdAt: '2026-11-26T01:00:00Z' }] : [],
  } }))
  await page.route('**/api/workflow/instances/ops-instance/terminate', route => {
    expect(route.request().postDataJSON()).toEqual({ reason: '外部依赖失败' })
    ended = true
    return fulfillApi(route)
  })
  await page.goto('/workflow/operations')
  await expect(page.getByRole('heading', { name: '流程运维' })).toBeVisible()
  await page.getByRole('combobox', { name: '状态' }).selectOption('EXCEPTION')
  const list = page.waitForRequest(request => new URL(request.url()).searchParams.get('status') === 'EXCEPTION')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await list
  await page.getByRole('button', { name: '审批记录' }).click()
  await page.getByRole('button', { name: '终止实例', exact: true }).click()
  await page.getByRole('textbox', { name: '操作原因' }).fill('外部依赖失败')
  await page.getByRole('button', { name: '确认终止实例' }).click()
  await expect(page.getByText('外部依赖失败', { exact: true })).toBeVisible()
})
