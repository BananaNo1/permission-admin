<script setup lang="ts">
import { NTag } from 'naive-ui'

defineProps<{
  selected: { id: string; label: string; description?: string }[]
  added: { id: string; label: string; description?: string }[]
  removed: { id: string; label: string; description?: string }[]
}>()
</script>

<template>
  <section class="assignment-panel" aria-label="变更预览">
    <h3>变更预览</h3>
    <div class="assignment-review">
      <p v-if="!added.length && !removed.length" class="assignment-hint">暂无变更</p>
      <div v-for="entry in added" :key="entry.id" class="assignment-row">
        <div>
          <span>{{ entry.label }}</span>
          <small v-if="entry.description" class="assignment-code">{{ entry.description }}</small>
        </div>
        <NTag size="small" type="success">新增</NTag>
      </div>
      <div v-for="entry in removed" :key="entry.id" class="assignment-row">
        <div>
          <span>{{ entry.label }}</span>
          <small v-if="entry.description" class="assignment-code">{{ entry.description }}</small>
        </div>
        <NTag size="small" type="warning">移除</NTag>
      </div>
      <details class="assignment-selected">
        <summary>保存后共 {{ selected.length }} 项</summary>
        <ul v-if="selected.length">
          <li v-for="entry in selected" :key="entry.id">
            {{ entry.label }}
            <small v-if="entry.description" class="assignment-code">{{ entry.description }}</small>
          </li>
        </ul>
        <p v-else class="assignment-hint">无已选项</p>
      </details>
    </div>
  </section>
</template>

<style scoped src="./assignment-dialog.css"></style>
