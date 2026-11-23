<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {NModal, NForm, NFormItem, NInput, NInputNumber, NSelect, NSwitch, NButton} from 'naive-ui'
import type {MenuVO, MenuCreateRequest} from '@/types/menu'
import {createMenu, updateMenu} from '@/api/menu'

const props = defineProps<{ menu: MenuVO | null; menus: MenuVO[] }>()
const emit = defineEmits<{ saved: []; close: [] }>()
const parentId = ref<string | null>(props.menu?.parentId || null)
const name = ref(props.menu?.name || '')
const description = ref(props.menu?.description ?? '')
const type = ref(props.menu?.type || 'DIRECTORY')
const routeName = ref(props.menu?.routeName || '')
const path = ref(props.menu?.path || '')
const componentKey = ref<string | null>(props.menu?.componentKey || null)
const permissionCode = ref(props.menu?.permissionCode || '')
const sortOrder = ref(props.menu?.sortOrder || 0)
const visible = ref(props.menu?.visible ?? true)
const status = ref(props.menu?.status || 'ENABLED')
const busy = ref(false)
const types = [{label: '目录', value: 'DIRECTORY'}, {label: '页面', value: 'PAGE'}, {label: '按钮', value: 'BUTTON'}]
const components = ['system/user', 'system/role', 'system/dept', 'system/menu', 'audit/login', 'audit/operation'].map(value => ({
  label: value,
  value
}))

const parentRequired = computed(() => type.value !== 'DIRECTORY')
const parentOptions = computed(() => {
  const parentType = type.value === 'BUTTON' ? 'PAGE' : 'DIRECTORY'
  return props.menus.filter(row => row.type === parentType)
    .map(row => ({ label: row.name, value: row.id, disabled: row.id === props.menu?.id }))
})

watch(parentOptions, options => {
  if (parentId.value !== null && !options.some(option => option.value === parentId.value && !option.disabled)) {
    parentId.value = null
  }
})

async function save() {
  if (busy.value || (parentRequired.value && parentId.value === null)) return
  const body: MenuCreateRequest = {
    parentId: parentId.value,
    name: name.value,
    description: description.value.trim(),
    type: type.value,
    routeName: routeName.value || null,
    path: path.value || null,
    componentKey: componentKey.value,
    permissionCode: permissionCode.value || null,
    sortOrder: sortOrder.value,
    visible: visible.value,
    status: status.value
  }
  busy.value = true
  try {
    if (props.menu) await updateMenu(props.menu.id, {...body, version: props.menu.version})
    else await createMenu(body)
    emit('saved')
  } catch { /* 后端校验父级、后代和页面白名单。 */
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <NModal :show="true" preset="card" title="菜单维护" style="width: 540px"
          @update:show="value => { if (!value) emit('close') }">
    <NForm @submit.prevent="save">
      <NFormItem label="名称" required>
        <NInput v-model:value="name" placeholder="填写业务名称，例如导出用户名单"/>
      </NFormItem>
      <NFormItem label="用途说明">
        <NInput v-model:value="description" type="textarea" :maxlength="300"
                :autosize="{ minRows: 2, maxRows: 3 }" placeholder="说明授予后可以执行的业务操作"/>
      </NFormItem>
      <NFormItem label="类型">
        <NSelect v-model:value="type" :options="types"/>
      </NFormItem>
      <NFormItem label="父菜单" :required="parentRequired">
        <NSelect v-model:value="parentId" clearable :options="parentOptions"
                 :placeholder="type === 'BUTTON' ? '请选择页面' : parentRequired ? '请选择目录' : '不选则为根目录'"/>
      </NFormItem>
      <NFormItem v-if="type !== 'DIRECTORY'" label="权限标识" required>
        <NInput v-model:value="permissionCode"/>
      </NFormItem>
      <template v-if="type === 'PAGE'">
        <NFormItem label="路由名称" required>
          <NInput v-model:value="routeName"/>
        </NFormItem>
        <NFormItem label="路由路径" required>
          <NInput v-model:value="path"/>
        </NFormItem>
        <NFormItem label="组件键" required>
          <NSelect v-model:value="componentKey" :options="components"/>
        </NFormItem>
      </template>
      <NFormItem label="排序">
        <NInputNumber v-model:value="sortOrder" :min="0"/>
      </NFormItem>
      <NFormItem label="导航可见">
        <NSwitch v-model:value="visible"/>
      </NFormItem>
      <NFormItem label="状态">
        <NSelect v-model:value="status" :options="[{label:'启用',value:'ENABLED'},{label:'禁用',value:'DISABLED'}]"/>
      </NFormItem>
      <NButton attr-type="submit" type="primary" :loading="busy" :disabled="parentRequired && parentId === null">保存</NButton>
    </NForm>
  </NModal>
</template>