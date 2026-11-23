<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NCheckbox, NInput, NSpace, NTable } from 'naive-ui'
import { api } from '@/api/http'
import { useAuth } from '@/stores/auth'

type Rights = { edit: boolean; publish: boolean; start: boolean }
type Grant = Rights & { userId: string | number }
const props = defineProps<{ business: string }>()
const emit = defineEmits<{ changed: [] }>()
const auth = useAuth()
const admin = computed(() => !!auth.me?.admin)
const code = ref('')
const name = ref('')
const userId = ref('')
const rights = reactive<Rights>({ edit: false, publish: false, start: false })
const grants = ref<Grant[]>([])
const busy = ref(false)
const loading = ref(false)
const message = ref('')
const failed = ref(false)
const root = '/workflow/businesses'
const grantsPath = (business: string) => root + '/' + encodeURIComponent(business) + '/grants'
let generation = 0

function report(error: unknown) {
    failed.value = true
    message.value = error instanceof Error ? error.message : '操作失败'
}

async function reload() {
    const version = ++generation
    loading.value = false
    if (!admin.value || !props.business) return
    loading.value = true
    try {
        const rows = await api<Grant[]>(grantsPath(props.business))
        if (version === generation) grants.value = rows
    } catch (error) {
        if (version === generation) report(error)
    } finally {
        if (version === generation) loading.value = false
    }
}

watch([() => props.business, admin], () => {
    grants.value = []
    userId.value = ''
    message.value = ''
    Object.assign(rights, { edit: false, publish: false, start: false })
    void reload()
}, { immediate: true })

async function create() {
    if (!admin.value || busy.value) return
    busy.value = true
    try {
        await api(root, 'POST', { code: code.value.trim(), name: name.value.trim() })
        code.value = ''
        name.value = ''
        failed.value = false
        message.value = '业务已创建'
        emit('changed')
    } catch (error) { report(error) }
    finally { busy.value = false }
}

function select(row: Grant) {
    userId.value = String(row.userId)
    Object.assign(rights, { edit: row.edit, publish: row.publish, start: row.start })
}

async function save(id: string, flags: Rights) {
    if (!admin.value || !props.business || busy.value) return
    const business = props.business
    busy.value = true
    try {
        await api(grantsPath(business) + '/' + id, 'PUT', { ...flags })
        if (business !== props.business) return
        failed.value = false
        message.value = '业务权限已更新'
        userId.value = ''
        Object.assign(rights, { edit: false, publish: false, start: false })
        await reload()
    } catch (error) { if (business === props.business) report(error) }
    finally { busy.value = false }
}
</script>

<template>
  <NCard v-if="admin" title="业务授权管理" size="small">
    <NSpace vertical>
      <NSpace align="center">
        <NInput v-model:value="code" placeholder="业务编码" aria-label="业务编码" :maxlength="64" />
        <NInput v-model:value="name" placeholder="业务名称" aria-label="业务名称" :maxlength="128" />
        <NButton :disabled="busy || !/^[a-zA-Z0-9_-]{1,64}$/.test(code.trim()) || !name.trim()" @click="create">创建业务</NButton>
      </NSpace>
      <NAlert v-if="message" :type="failed ? 'error' : 'success'">{{ message }}</NAlert>
      <template v-if="business">
        <strong>{{ business }} 的用户权限</strong>
        <NSpace align="center">
          <NInput v-model:value="userId" placeholder="用户 ID" aria-label="用户 ID" />
          <NCheckbox v-model:checked="rights.edit">编辑</NCheckbox>
          <NCheckbox v-model:checked="rights.publish">发布</NCheckbox>
          <NCheckbox v-model:checked="rights.start">发起</NCheckbox>
          <NButton :disabled="busy || !/^[1-9]\d*$/.test(userId) || !(rights.edit || rights.publish || rights.start)" @click="save(userId, rights)">保存权限</NButton>
        </NSpace>
        <NTable v-if="grants.length" size="small" :single-line="false">
          <thead><tr><th>用户 ID</th><th>编辑</th><th>发布</th><th>发起</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in grants" :key="row.userId">
              <td>{{ row.userId }}</td><td>{{ row.edit ? '有' : '无' }}</td>
              <td>{{ row.publish ? '有' : '无' }}</td><td>{{ row.start ? '有' : '无' }}</td>
              <td><NSpace>
                <NButton size="small" :disabled="busy" @click="select(row)">修改</NButton>
                <NButton size="small" type="error" ghost :disabled="busy" @click="save(String(row.userId), {edit:false,publish:false,start:false})">撤销全部</NButton>
              </NSpace></td>
            </tr>
          </tbody>
        </NTable>
        <span v-else>{{ loading ? '正在加载授权…' : failed ? '' : '暂无授权用户' }}</span>
      </template>
    </NSpace>
  </NCard>
</template>
