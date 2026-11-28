<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NCard, NButton, NDataTable, NPagination, type DataTableColumns } from 'naive-ui'
import type { LoginLogVO } from '@/types/log'
import { listLoginLogs } from '@/api/loginLog'

const rows = ref<LoginLogVO[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await listLoginLogs(page.value, pageSize.value);
    rows.value = result.records;
    total.value = result.total
  } catch { /* 全局反馈。 */
  } finally {
    loading.value = false
  }
}

const columns: DataTableColumns<LoginLogVO> = [
  { title: '账号', key: 'username', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '成功', key: 'success', minWidth: 120, ellipsis: { tooltip: true } },
  { title: 'IP', key: 'ip', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '时间', key: 'createdAt', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '追踪ID', key: 'traceId', minWidth: 120, ellipsis: { tooltip: true } },
]
onMounted(load)
</script>
<template>
  <h1 class="management-heading">登录日志</h1>
  <NCard class="management-card">
    <div class="toolbar">
      <NButton @click="page = 1; load()">刷新</NButton>
    </div>
    <NDataTable striped :columns="columns" :data="rows" :loading="loading" :row-key="row => row.id" :scroll-x="1000" />
    <div class="pagination">
      <NPagination v-model:page="page" v-model:page-size="pageSize" :item-count="total" show-size-picker
                   :page-sizes="[10, 20, 50, 100]" @update:page="load" @update:page-size="page = 1; load()" />
    </div>
  </NCard>
</template>