<script setup lang="ts">
import {NResult, NButton} from 'naive-ui'

defineProps<{ code: string; title: string }>()
</script>
<template>
  <main class="status-page">
    <NResult :title="code" :description="title">
      <template #footer>
        <NButton @click="$router.push('/')">返回首页</NButton>
      </template>
    </NResult>
  </main>
</template>