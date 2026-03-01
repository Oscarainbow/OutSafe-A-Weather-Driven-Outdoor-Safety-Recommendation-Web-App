<script setup>
import { levelLabel, topReasons, comparisonText } from '../utils/risk'

const props = defineProps({
  level: { type: String, default: 'unknown' },
  score: { type: Number, default: 0 },
  percentiles: { type: Object, default: () => ({}) },
  yearsBack: { type: Number, default: 5 },
  hasHistory: { type: Boolean, default: true },
  archiveRequested: { type: Number, default: 0 },
  archiveSuccess: { type: Number, default: 0 },
})

const badgeClass = () => {
  if (props.level === 'not_recommended') return 'badge--danger'
  if (props.level === 'caution') return 'badge--warning'
  return 'badge--success'
}

const reasons = () => topReasons(props.percentiles, 3)
const comparison = () => comparisonText(props.percentiles, props.yearsBack)
</script>

<template>
  <div class="card">
    <h2 style="margin-top: 0;">总评</h2>
    <p>
      <span :class="['badge', badgeClass()]">{{ levelLabel(level) }}</span>
      <span class="muted" style="margin-left: 0.5rem;">综合风险 {{ score }}%</span>
    </p>
    <div v-if="!hasHistory" class="no-history-tip">
      <p>暂无历史同日数据，百分位为参考值（50% 表示无法与历史对比）。</p>
      <p class="no-history-reasons">可能原因：请求了 {{ archiveRequested }} 年同日数据，成功 {{ archiveSuccess }} 条。若为 0，多为网络问题、Open-Meteo 限流，或该区域/日期暂无历史存档。</p>
    </div>

    <div v-if="reasons().length" class="reason-block">
      <h3 class="sub">关键原因</h3>
      <ul class="reason-list">
        <li v-for="r in reasons()" :key="r.key">
          {{ r.label }}：历史百分位 {{ r.pct }}%
        </li>
      </ul>
    </div>

    <div class="comparison">
      {{ comparison() }}
    </div>
  </div>
</template>

<style scoped>
.muted { color: var(--muted); font-size: 0.9rem; }
.sub { font-size: 1rem; margin: 0.75rem 0 0.25rem; color: var(--muted); }
.reason-block { margin-top: 0.5rem; }
.no-history-tip { font-size: 0.85rem; color: var(--warning); margin-top: 0.5rem; }
.no-history-tip p { margin: 0.25rem 0; }
.no-history-reasons { color: var(--muted); font-size: 0.8rem; }
</style>
