<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NAlert } from 'naive-ui'
import { listWorkflowBusinesses } from '@/api/workflowBusiness'
import type { WorkflowBusiness } from '@/types/workflow'
import ApprovalWorkbench from './ApprovalWorkbench.vue'

defineProps<{ initialView: 'applications' | 'tasks' | 'completed' | 'operations' }>()

const businesses = ref<WorkflowBusiness[]>([])
const error = ref('')

onMounted(async () => {
  try {
    businesses.value = await listWorkflowBusinesses()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取业务范围'
  }
})
</script>

<template>
  <NAlert v-if="error" type="error">{{ error }}</NAlert>
  <ApprovalWorkbench :businesses="businesses" :preferred-business="null" :key="initialView" :initial-view="initialView" />
</template>
