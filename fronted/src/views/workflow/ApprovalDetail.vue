<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NPagination } from 'naive-ui'
import type { InstanceDetail } from '@/types/workflow'
import { dateTime, labels } from './approvalDetail'

const props = defineProps<{ detail: InstanceDetail; page: number; pageSize: number }>()
const emit = defineEmits<{ page: [number]; task: [string]; withdraw: []; terminate: [] }>()

const application = computed(() => props.detail.application)
const operations = computed(() => props.detail.operations ?? [])
const endingOperation = computed(() => operations.value.find(item => item.operation === 'WITHDRAW' || item.operation === 'TERMINATE'))
const finished = computed(() => ['APPROVED', 'REJECTED', 'CANCELLED'].includes(application.value.status))
const status = computed(() => {
  if (application.value.status === 'CANCELLED') {
    if (endingOperation.value?.operation === 'WITHDRAW') return '已撤回'
    if (endingOperation.value?.operation === 'TERMINATE') return '已终止'
  }
  return labels[application.value.status] ?? application.value.status
})
const showSubmission = computed(() => Boolean(application.value.startTime)
  && props.page * props.pageSize >= props.detail.actions.total)
const tone = (value: string) => {
  if (value === 'APPROVE' || value === 'APPROVED') return 'success'
  if (value === 'REJECT' || value === 'REJECTED') return 'danger'
  if (value === 'RETURN' || value === 'RETURNED') return 'warning'
  return value === 'CANCELLED' ? 'neutral' : 'active'
}
const actor = (id?: string | null) => id ? props.detail.actorNames[id] ?? '账号 ' + id : '办理人未记录'
</script>

<template>
  <div class="approval-detail">
    <header class="summary">
      <div>
        <div v-if="application.businessKey" class="business-key">{{ application.businessKey }}</div>
        <h2>{{ application.title }}</h2>
      </div>
      <span class="state" :data-tone="tone(application.status)">{{ status }}</span>
    </header>

    <section class="progress" aria-label="流程进度">
      <div class="section-header">
        <h3>{{ finished ? '流程已结束' : '当前办理' }}</h3>
        <div v-if="detail.canWithdraw || detail.canTerminate" class="actions">
          <NButton v-if="detail.canWithdraw" size="small" type="warning" secondary @click="emit('withdraw')">撤回申请</NButton>
          <NButton v-if="detail.canTerminate" size="small" type="error" secondary @click="emit('terminate')">终止实例</NButton>
        </div>
      </div>
      <p v-if="finished" class="progress-note">
        {{ status }}<template v-if="application.endTime"> · <time :datetime="application.endTime">{{ dateTime(application.endTime) }}</time></template>
      </p>
      <ul v-else-if="detail.active.length" class="current-list" role="list" aria-label="当前办理人员">
        <li v-for="item in detail.active" :key="item.taskId" class="current-task">
          <div>
            <strong>{{ item.nodeName }}</strong>
            <div class="metadata">
              <span>{{ item.actorName || (item.actorId ? actor(item.actorId) : '待分配') }}</span>
              <time v-if="item.createTime" :datetime="item.createTime">{{ dateTime(item.createTime) }}</time>
            </div>
          </div>
          <NButton v-if="item.mine" size="small" type="primary" secondary @click="emit('task', item.taskId)">处理任务</NButton>
          <span v-else class="task-status">待处理</span>
        </li>
      </ul>
      <p v-else class="progress-note">当前暂无人工待办</p>
    </section>

    <div class="detail-grid">
      <section class="timeline-panel" aria-label="审批轨迹">
        <div class="section-header">
          <h3>审批轨迹</h3>
          <span v-if="detail.actions.total" class="muted">最新办理在前</span>
        </div>
        <ol v-if="detail.actions.records.length || showSubmission" class="timeline" role="list" aria-label="审批轨迹记录">
          <li v-for="item in detail.actions.records" :key="item.taskId" class="timeline-item" :data-tone="tone(item.action)">
            <div class="entry-title">
              <strong>{{ item.nodeName || (item.action === 'RESUBMIT' ? '修改申请' : '审批节点') }}</strong>
              <span class="state">{{ labels[item.action] ?? item.action }}</span>
            </div>
            <div class="metadata">
              <span>{{ actor(item.actorId) }}</span>
              <time :datetime="item.time">{{ dateTime(item.time) }}</time>
            </div>
            <p v-if="item.comment" class="opinion">{{ item.comment }}</p>
            <details v-if="item.listenerCompletedAt" class="listener">
              <summary>节点执行信息</summary>
              <span>任务监听器记录 <time :datetime="item.listenerCompletedAt">{{ dateTime(item.listenerCompletedAt) }}</time></span>
            </details>
          </li>
          <li v-if="showSubmission" class="timeline-item submitted" data-tone="neutral">
            <div class="entry-title"><strong>发起申请</strong></div>
            <div class="metadata">
              <span v-if="application.applicantId">{{ actor(application.applicantId) }}</span>
              <time :datetime="application.startTime">{{ dateTime(application.startTime) }}</time>
            </div>
          </li>
        </ol>
        <p v-else class="progress-note">暂无审批记录</p>
        <NPagination v-if="detail.actions.total > pageSize" class="pagination"
          :page="page" :page-size="pageSize" :item-count="detail.actions.total" :page-slot="5"
          @update:page="emit('page', $event)" />
      </section>

      <aside class="context" aria-label="申请与操作信息">
        <section class="facts">
          <h3>申请信息</h3>
          <dl>
            <template v-if="application.applicantId"><dt>申请人</dt><dd>{{ actor(application.applicantId) }}</dd></template>
            <template v-if="application.amount != null"><dt>申请金额</dt><dd>{{ application.amount }}</dd></template>
            <template v-if="application.templateVersion"><dt>模板版本</dt><dd><code v-if="application.templateCode">{{ application.templateCode }}</code> v{{ application.templateVersion }}</dd></template>
            <template v-if="application.startTime"><dt>提交时间</dt><dd><time :datetime="application.startTime">{{ dateTime(application.startTime) }}</time></dd></template>
            <template v-if="application.description"><dt class="description-label">申请说明</dt><dd class="description">{{ application.description }}</dd></template>
          </dl>
        </section>
        <section v-if="operations.length" class="operation-history">
          <h3>流程操作</h3>
          <ul role="list">
            <li v-for="item in operations" :key="item.id">
              <strong>{{ labels[item.operation] ?? item.operation }}</strong>
              <div class="metadata"><span>{{ actor(item.actorId) }}<template v-if="item.targetId"> 转给 {{ actor(item.targetId) }}</template></span>
                <time :datetime="item.createdAt">{{ dateTime(item.createdAt) }}</time></div>
              <p v-if="item.reason">{{ item.reason }}</p>
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.approval-detail {
  --detail-ink: #172b45;
  --detail-muted: #4e627b;
  --detail-line: #dbe4ef;
  --detail-surface: #f5f8fc;
  display: grid;
  gap: 18px;
  max-block-size: calc(100vh - 9rem);
  max-block-size: calc(100dvh - 9rem);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 2px 4px 4px;
  color: var(--detail-ink);
  overflow-wrap: anywhere;
}
.summary, .section-header, .entry-title, .current-task, .actions, .metadata {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.summary, .section-header, .current-task { justify-content: space-between; }
.summary { padding: 3px 0 17px; border-bottom: 1px solid var(--detail-line); }
.summary > div, .current-task > div { min-width: 0; }
.business-key { color: var(--detail-muted); font-size: 12px; font-weight: 650; font-variant-numeric: tabular-nums; }
.summary h2 { margin: 4px 0 0; font-size: clamp(21px, 4vw, 27px); line-height: 1.25; }
h3 { margin: 0; font-size: 15px; line-height: 1.35; }
.state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 5px;
  padding: 2px 8px;
  background: var(--state-bg, #e6effc);
  color: var(--state-color, #22548d);
  font-size: 12px;
  font-weight: 650;
}
.summary .state { padding: 4px 10px; font-size: 13px; }
.summary .state::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: var(--state-dot, #3266a3); }
[data-tone="success"] { --state-color: #186044; --state-bg: #e4f3eb; --state-dot: #27815b; }
[data-tone="danger"] { --state-color: #a23136; --state-bg: #fbecef; --state-dot: #ae444b; }
[data-tone="warning"] { --state-color: #805218; --state-bg: #f9efdd; --state-dot: #9b6022; }
[data-tone="neutral"] { --state-color: #485c74; --state-bg: #eaf0f7; --state-dot: #647d98; }
.progress { padding: 14px 16px; border: 1px solid #d5e3f3; border-radius: 8px; background: var(--detail-surface); }
.progress-note { margin: 6px 0 0; color: var(--detail-muted); }
.current-list, .timeline, .operation-history ul { margin: 0; padding: 0; list-style: none; }
.current-list { margin-top: 10px; }
.current-task { padding: 9px 0; border-top: 1px solid var(--detail-line); }
.current-task:last-child { padding-bottom: 0; }
.task-status, .muted, .metadata { color: var(--detail-muted); font-size: 12px; }
.metadata { margin: 3px 0 0; gap: 2px 10px; }
time { font-variant-numeric: tabular-nums; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(190px, 31%); gap: 22px; align-items: start; }
.timeline { margin-top: 13px; }
.timeline-item { position: relative; padding: 0 0 17px 22px; }
.timeline-item:last-child { padding-bottom: 0; }
.timeline-item:not(:last-child)::before {
  content: ''; position: absolute; inset: 11px auto -6px 4px; width: 1px; background: var(--detail-line);
}
.timeline-item::after {
  content: ''; position: absolute; top: 6px; left: 0; width: 9px; height: 9px;
  border: 2px solid var(--state-dot, #3266a3); border-radius: 50%; background: #fff;
}
.opinion { margin: 7px 0 0; padding: 7px 10px; background: var(--detail-surface); border-radius: 4px; white-space: pre-wrap; }
.listener { margin-top: 6px; color: var(--detail-muted); font-size: 12px; }
.listener summary { cursor: pointer; width: fit-content; }
.listener > span { display: block; margin: 4px 0 0 13px; }
.pagination { margin-top: 14px; }
.context { display: grid; gap: 13px; }
.facts, .operation-history { padding: 13px 14px; border: 1px solid var(--detail-line); border-radius: 8px; }
.facts dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 8px 11px; margin: 12px 0 0; font-size: 13px; }
.facts dt { color: var(--detail-muted); }
.facts dd { margin: 0; }
.facts code { font-size: 12px; }
.description-label, .description { grid-column: 1 / -1; }
.facts .description { margin-top: -4px; white-space: pre-wrap; }
.operation-history li { margin-top: 10px; padding-top: 9px; border-top: 1px solid var(--detail-line); }
.operation-history p { margin: 4px 0 0; white-space: pre-wrap; }
@media (max-width: 680px) {
  .approval-detail { gap: 14px; }
  .detail-grid { grid-template-columns: minmax(0, 1fr); gap: 16px; }
}
</style>
