<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NCard, NButton, NDataTable, NPagination, type DataTableColumns } from 'naive-ui'
import type { OperationLogVO } from '@/types/log'
import { listOperationLogs } from '@/api/operationLog'

const rows = ref<OperationLogVO[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await listOperationLogs(page.value, pageSize.value);
    rows.value = result.records;
    total.value = result.total
  } catch { /* 全局反馈。 */
  } finally {
    loading.value = false
  }
}

const columns: DataTableColumns<OperationLogVO> = [
  { title: '操作者', key: 'actorUsername', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '动作', key: 'action', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '目标', key: 'targetId', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '成功', key: 'success', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '时间', key: 'createdAt', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '追踪ID', key: 'traceId', minWidth: 120, ellipsis: { tooltip: true } },
]
onMounted(load)
</script>
<template>
  <h1 class="management-heading">操作日志</h1>
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