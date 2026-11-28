import { expect, test, type Page } from '@playwright/test'
import { fulfillApi } from './api-response'

const path = '/api/workflow/businesses/buy/templates'
const steps = [{ name: '主管审批', mode: 'SINGLE', approverIds: [2], minAmount: null, returnTo: 'APPLICANT' }]

async function openTemplates(page: Page, edit: boolean, nodes = steps, publish = !edit, clean = false) {
  const draft = { code: 'order', name: '采购审批', revision: 7, publishedVersion: clean ? 2 : 1, dirty: !clean, enabled: true, steps: nodes }
  await page.route('**/api/auth/me', route => fulfillApi(route, { json: {
    id: '9', username: 'operator', deptId: '1', admin: false, permissions: ['workflow:template:list'],
  } }))
  await page.route('**/api/auth/menus', route => fulfillApi(route, { json: [{
    id: '89', parentId: null, name: '流程管理', type: 'DIRECTORY', visible: true,
  }, {
    id: '90', parentId: '89', name: '流程模板', type: 'PAGE', visible: true, path: '/workflow/templates',
    routeName: 'WorkflowTemplates', componentKey: 'workflow/templates', permissionCode: 'workflow:template:list',
  }] }))
  await page.route('**/api/auth/csrf', route => fulfillApi(route, { json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**' + path + '/reviewers?*', route => fulfillApi(route, {
    json: [{ id: 2, name: '张三', username: 'zhangsan', enabled: true }],
  }))
  await page.route('**/api/workflow/businesses', route => fulfillApi(route, { json: [
    { code: 'buy', name: '采购业务', edit, publish, start: false },
  ] }))
  await page.route('**' + path, route => fulfillApi(route, { json: [draft] }))
  await page.route('**' + path + '/order', route => {
    Object.assign(draft, route.request().postDataJSON(), { revision: 8 })
    return fulfillApi(route)
  })
  await page.route('**' + path + '/order/publish', route => {
    Object.assign(draft, { revision: 8, publishedVersion: 2, dirty: false })
    return fulfillApi(route)
  })
  await page.goto('/workflow/templates')
  await page.getByRole('button', { name: edit ? '设计 采购审批' : '查看 采购审批' }).click()
  return draft
}

test('template catalog and design workspace have separate navigation', async ({ page }) => {
  await page.setViewportSize({ width: 1460, height: 960 })
  await openTemplates(page, true)
  await expect(page.getByRole('heading', { name: '采购审批' })).toBeVisible()
  await expect(page.getByRole('region', { name: '业务模板' })).toHaveCount(0)
  const canvas = page.getByRole('region', { name: 'BPMN 流程图' })
  const inspector = page.getByRole('complementary', { name: '节点属性' })
  await expect(canvas).toBeVisible()
  await expect(inspector).toBeVisible()
  const [drawing, controls] = await Promise.all([canvas.boundingBox(), inspector.boundingBox()])
  expect(drawing!.x + drawing!.width).toBeLessThan(controls!.x)
  await page.setViewportSize({ width: 760, height: 960 })
  const [narrowDrawing, narrowControls] = await Promise.all([canvas.boundingBox(), inspector.boundingBox()])
  expect(narrowDrawing!.y + narrowDrawing!.height).toBeLessThan(narrowControls!.y)
  await page.getByRole('button', { name: /返回模板列表/ }).click()
  await expect(page.getByRole('heading', { name: '流程模板', exact: true })).toBeVisible()
  await expect(canvas).toHaveCount(0)
  const search = page.getByRole('textbox', { name: '搜索模板' })
  await search.fill('找不到的模板')
  await expect(page.getByText('没有匹配的模板')).toBeVisible()
  await search.fill('order')
  await expect(page.getByRole('button', { name: '设计 采购审批' })).toBeVisible()
})

test('BPMN modeler saves and restores the path layout after dragging a task', async ({ page }) => {
  const taskName = 'A&<B>"'
  await openTemplates(page, true, [{ ...steps[0], name: taskName }, { ...steps[0], name: '财务审批' }])
  const canvas = page.getByTestId('bpmn-modeler')
  await expect(canvas.locator('.djs-palette')).toBeVisible()
  await expect(canvas.getByText(taskName, { exact: true })).toBeVisible()
  const task = canvas.locator('.djs-shape[data-element-id="Task_0"] .djs-hit').first()
  await task.scrollIntoViewIfNeeded()
  const box = await task.boundingBox()
  if (!box) throw Error('BPMN task is outside the canvas')
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + 30, box.y + box.height / 2 + 40, { steps: 5 })
  await page.mouse.up()
  const edge = canvas.locator('.djs-connection[data-element-id="Sequence_0"] .djs-visual path').first()
  const pathAfterDrag = await edge.getAttribute('d')
  const request = page.waitForRequest(req => req.method() === 'PUT' && req.url().endsWith(path + '/order'))
  await page.getByRole('button', { name: '保存草稿' }).click()
  const payload = (await request).postDataJSON()
  expect(payload.steps).toMatchObject([
    { name: taskName, position: { x: expect.any(Number), y: expect.any(Number) } },
    { name: '财务审批' },
  ])
  expect(payload.diagramLayout).toMatchObject({
    start: { x: 55, y: 144 },
    end: { x: expect.any(Number), y: 144 },
    waypoints: [expect.any(Array), expect.any(Array), expect.any(Array)],
  })
  await page.reload()
  await expect(edge).toHaveAttribute('d', pathAfterDrag!)
  await expect(canvas.getByText(taskName, { exact: true })).toBeVisible()
})

test('BPMN modeler blocks disconnected drafts and undo restores the path', async ({ page }) => {
  await openTemplates(page, true)
  const canvas = page.getByTestId('bpmn-modeler')
  await canvas.locator('.djs-shape[data-element-id="End_approval"] .djs-hit').first().click()
  await canvas.locator('.djs-context-pad.open [data-action="delete"]').click()
  await expect(page.getByText(/请连接成一条从开始到结束/)).toBeVisible()
  await expect(page.getByRole('button', { name: '保存草稿' })).toBeDisabled()
  await page.getByRole('button', { name: '撤销' }).click()
  await expect(page.getByRole('button', { name: '保存草稿' })).toBeEnabled()
})

test('canvas selection opens a newly placed task before it is connected', async ({ page }) => {
  await openTemplates(page, true)
  const canvas = page.getByTestId('bpmn-modeler')
  await canvas.scrollIntoViewIfNeeded()
  await canvas.locator('.djs-palette [data-action="create.task"]').click()
  const box = await canvas.boundingBox()
  if (!box) throw Error('BPMN canvas is outside the page')
  await page.mouse.move(box.x + box.width * .7, box.y + box.height * .65)
  await page.mouse.click(box.x + box.width * .7, box.y + box.height * .65)
  const newTask = canvas.locator('.djs-shape[data-element-id^="Activity_"] .djs-hit').first()
  await newTask.click()
  const name = page.getByRole('textbox', { name: '节点名称' })
  await expect(name).toHaveValue('')
  await name.fill('财务复核')
  await expect(canvas.getByText('财务复核', { exact: true })).toBeVisible()
  await canvas.locator('.djs-shape[data-element-id="Task_0"] .djs-hit').first().click()
  await expect(name).toHaveValue('主管审批')
  await newTask.click()
  await expect(name).toHaveValue('财务复核')
  await expect(page.getByRole('button', { name: '保存草稿' })).toBeDisabled()
})

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
  await expect(page.getByRole('heading', { name: '采购审批修订' })).toBeVisible()
})

test('business publisher can publish the selected revision without editing', async ({ page }) => {
  await openTemplates(page, false)
  const publish = page.getByRole('button', { name: '发布版本' })
  await expect(publish).toBeEnabled()
  await expect(page.getByRole('button', { name: '保存草稿' })).toHaveCount(0)
  const request = page.waitForRequest(req => req.method() === 'POST' && req.url().endsWith(path + '/order/publish'))
  await publish.click()
  expect((await request).postDataJSON()).toEqual({ revision: 7 })
  await expect(page.getByText(/已发布版本 2/)).toBeVisible()
  await expect(publish).toBeDisabled()
  await expect(page.getByText('当前业务未授予编辑权限')).toBeVisible()
  await expect(page.getByRole('button', { name: '新建模板' })).toHaveCount(0)
})

test('super administrator can create the first business and then its first template', async ({ page }) => {
  let created = false
  await page.route('**/api/auth/me', route => fulfillApi(route, {
    json: { id: '1', username: 'admin', deptId: '1', admin: true, permissions: [] },
  }))
  await page.route('**/api/auth/menus', route => fulfillApi(route, { json: [{
    id: '90', parentId: null, name: '流程模板', type: 'PAGE', visible: true, path: '/workflow/templates',
    routeName: 'WorkflowTemplates', componentKey: 'workflow/templates',
  }] }))
  await page.route('**/api/auth/csrf', route => fulfillApi(route, { json: { token: 'test', headerName: 'X-CSRF-TOKEN' } }))
  await page.route('**/api/workflow/businesses', route => {
    if (route.request().method() === 'POST') {
      created = true
      return fulfillApi(route, { status: 201 })
    }
    return fulfillApi(route, { json: created ? [{ code: 'buy', name: '采购业务', edit: true, publish: true }] : [] })
  })
  await page.route('**' + path, route => fulfillApi(route, { json: [] }))
  await page.route('**' + path + '/reviewers?*', route => fulfillApi(route, {
    json: [{ id: 2, name: '张三', username: 'zhangsan', enabled: true }],
  }))
  await page.route('**' + path + '/order', route => fulfillApi(route))
  await page.goto('/workflow/templates')
  await expect(page.getByText('请先创建业务范围，再新建流程模板。')).toBeVisible()
  const heading = page.locator('header').filter({ has: page.getByRole('heading', { name: '流程模板' }) })
  await heading.getByRole('button', { name: '新建模板' }).click()
  await expect(page.getByText('创建业务范围', { exact: true })).toBeVisible()
  const businessCode = page.getByRole('textbox', { name: '业务编码' })
  await expect(businessCode).toBeFocused()
  await businessCode.fill('buy')
  await page.getByRole('textbox', { name: '业务名称' }).fill('采购业务')
  await page.getByRole('button', { name: '创建业务' }).click()
  await page.getByRole('textbox', { name: '模板编码' }).fill('order')
  await page.getByRole('textbox', { name: '模板名称' }).fill('采购审批')
  await page.getByRole('textbox', { name: '节点名称' }).fill('主管审批')
  const personSearch = page.getByTestId('reviewer-picker')
  await personSearch.locator('.n-base-selection').click()
  const input = personSearch.locator('input').filter({ visible: true })
  const search = page.waitForRequest(req => new URL(req.url()).searchParams.get('keyword') === '张三')
  await input.fill('张三')
  await search
  await page.getByText('张三（zhangsan）', { exact: true }).click()
  await expect(personSearch.getByText('张三（zhangsan）', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /主管审批/ })).toHaveAttribute('aria-pressed', 'true')
  const request = page.waitForRequest(req => req.method() === 'PUT' && req.url().endsWith(path + '/order'), { timeout: 5000 })
  await page.getByRole('button', { name: '保存草稿' }).click()
  expect((await request).postDataJSON()).toMatchObject({ name: '采购审批', revision: 0, steps })
})

test('configures the task form and listeners, then saves and publishes the node', async ({ page }) => {
  await openTemplates(page, true, steps, true)
  const inspector = page.getByRole('complementary', { name: '节点属性' })
  await page.getByTestId('bpmn-modeler').locator('.djs-shape[data-element-id="Task_0"] .djs-hit').first().click()
  await expect(inspector.getByText('任务配置', { exact: true })).toBeVisible()
  await inspector.getByText('表单配置', { exact: true }).click()
  await inspector.getByLabel('办理表单').selectOption('required-opinion')
  await inspector.getByText('执行监听器', { exact: true }).click()
  await inspector.getByRole('checkbox', { name: '节点进入时记录时间' }).check()
  await inspector.getByText('任务监听器', { exact: true }).click()
  await inspector.getByRole('checkbox', { name: '任务办理完成时记录时间' }).check()
  await expect(page.getByRole('button', { name: '发布版本' })).toBeDisabled()
  const save = page.waitForRequest(request => request.method() === 'PUT' && request.url().endsWith(path + '/order'))
  await page.getByRole('button', { name: '保存草稿' }).click()
  expect((await save).postDataJSON().steps[0]).toMatchObject({
    formKey: 'required-opinion', recordEntered: true, recordCompleted: true,
  })
  const publish = page.getByRole('button', { name: '发布版本' })
  await expect(publish).toBeEnabled()
  await page.getByTestId('bpmn-modeler').locator('.djs-shape[data-element-id="Task_0"] .djs-hit').first().click()
  await inspector.getByText('表单配置', { exact: true }).click()
  await expect(inspector.getByLabel('办理表单')).toHaveValue('required-opinion')
  await publish.click()
  await expect(page.getByText(/草稿已发布/)).toBeVisible()
})

test('shows the HTTP status for a failed publication and clears the alert after retry', async ({ page }) => {
  await openTemplates(page, true, steps, true)
  await page.route('**' + path + '/order/publish', route => route.fulfill({ status: 503 }), { times: 1 })

  const publish = page.getByRole('button', { name: '发布版本' })
  const alert = page.locator('.global-alert')
  await publish.click()
  await expect(alert).toHaveText(/HTTP 503/)
  await expect(page.getByText(/草稿待发布/)).toBeVisible()

  await publish.click()
  await expect(page.getByText(/草稿已发布/)).toBeVisible()
  await expect(alert).toHaveCount(0)
})

test('version management reads history, restores a draft and controls new applications', async ({ page }) => {
  const draft = await openTemplates(page, true, steps, true, true)
  const versions = [
    { version: 2, name: '采购审批', publishedAt: '2026-11-25T09:00:00Z', publishedBy: '1' },
    { version: 1, name: '初版采购', publishedAt: '2026-11-24T09:00:00Z', publishedBy: '1' },
  ]
  await page.route('**' + path + '/order/versions', route => fulfillApi(route, { json: versions }))
  await page.route('**' + path + '/order/versions/1', route => fulfillApi(route, {
    json: { name: '初版采购', steps: [{ ...steps[0], name: '初版审批节点' }] },
  }))
  await page.route('**' + path + '/order/versions/1/restore', route => {
    expect(route.request().postDataJSON()).toEqual({ revision: 7 })
    Object.assign(draft, { revision: 8, dirty: true, name: '初版采购' })
    return fulfillApi(route)
  })
  await page.route('**' + path + '/order/availability', route => {
    expect(route.request().postDataJSON()).toEqual({ revision: 8, enabled: false })
    Object.assign(draft, { revision: 9, enabled: false })
    return fulfillApi(route)
  })
  await page.getByRole('button', { name: '版本管理' }).click()
  await page.getByRole('button', { name: '查看 v1' }).click()
  await expect(page.getByText(/初版审批节点/)).toBeVisible()
  await page.getByRole('button', { name: '复制为待发布草稿' }).click()
  await expect(page.getByText(/草稿待发布/)).toBeVisible()
  await expect(page.getByRole('heading', { name: '初版采购', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '版本管理' }).click()
  await page.getByRole('button', { name: '停用新申请' }).click()
  await expect(page.getByText(/已停用新申请/)).toBeVisible()
  await expect(page.getByText(/已发布版本 2/)).toBeVisible()
})

test('configures role-based and department-manager approval nodes', async ({ page }) => {
  await openTemplates(page, true, [steps[0], { ...steps[0], name: '财务会签' }], true)
  await page.route('**' + path + '/reviewer-roles', route => fulfillApi(route, {
    json: [{ id: '9', name: '财务审批', code: 'FINANCE' }],
  }))
  const canvas = page.getByTestId('bpmn-modeler')
  const source = page.getByRole('combobox', { name: '审批人来源' })
  await canvas.locator('.djs-shape[data-element-id="Task_0"] .djs-hit').first().click()
  await source.selectOption('DEPARTMENT_MANAGER')
  await expect(page.getByRole('combobox', { name: '审批模式' })).toBeDisabled()
  await canvas.locator('.djs-shape[data-element-id="Task_1"] .djs-hit').first().click()
  await source.selectOption('ROLE')
  await page.getByRole('combobox', { name: '审批模式' }).selectOption('ALL')
  await page.getByRole('textbox', { name: '审批角色' }).click()
  await page.getByText('财务审批（FINANCE）', { exact: true }).click()
  await page.getByRole('textbox', { name: '最低金额' }).fill('10000')
  const request = page.waitForRequest(req => req.method() === 'PUT' && req.url().endsWith(path + '/order'))
  await page.getByRole('button', { name: '保存草稿' }).click()
  expect((await request).postDataJSON().steps).toMatchObject([
    { reviewerSource: 'DEPARTMENT_MANAGER', approverIds: [], mode: 'SINGLE' },
    { reviewerSource: 'ROLE', roleId: '9', approverIds: [], mode: 'ALL', minAmount: 10000 },
  ])
})

test('saves and reloads an amount gateway with a real default connection', async ({ page }) => {
  await openTemplates(page, true, steps, true)
  await page.getByRole('button', { name: '添加条件分支' }).click()
  const rules = page.getByRole('list', { name: '条件连线' })
  await expect(rules.getByRole('button', { name: /默认分支.*审批结束/ })).toBeVisible()
  await page.getByRole('combobox', { name: '分支条件' }).selectOption('GTE')
  await page.getByRole('button', { name: '应用连线条件' }).click()
  await expect(page.getByText('请输入非负金额，整数最多 16 位，小数最多两位')).toBeVisible()
  await page.getByRole('textbox', { name: '条件金额' }).fill('10000.00')
  await page.getByRole('button', { name: '应用连线条件' }).click()
  await expect(rules.getByRole('button', { name: /金额 ≥ 10000.00/ })).toBeVisible()
  await page.getByRole('list', { name: '审批节点' }).getByRole('button', { name: '条件审批' }).click()
  await page.getByRole('combobox', { name: '审批人来源' }).selectOption('DEPARTMENT_MANAGER')
  const request = page.waitForRequest(req => req.method() === 'PUT' && req.url().endsWith(path + '/order'))
  await page.getByRole('button', { name: '保存草稿' }).click()
  const routing = (await request).postDataJSON().diagramLayout.routing
  expect(routing.gateways).toHaveLength(1)
  expect(routing.links).toEqual(expect.arrayContaining([
    expect.objectContaining({ operator: 'GTE', amount: '10000.00' }),
    expect.objectContaining({ operator: 'DEFAULT', target: 'End_approval' }),
  ]))
  await expect(page.getByRole('button', { name: '发布版本' })).toBeEnabled()
  await page.reload()
  await expect(rules.getByRole('button', { name: /金额 ≥ 10000.00/ })).toBeVisible()
  await expect(page.getByTestId('bpmn-modeler').locator('.djs-shape[data-element-id="' + routing.gateways[0].id + '"]')).toBeVisible()
})
