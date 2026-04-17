<script setup>
const props = defineProps({
  overlay: { type: Boolean, default: false },
  level: { type: String, default: 'unknown' },
  score: { type: Number, default: 0 },
  percentiles: { type: Object, default: () => ({}) },
  yearsBack: { type: Number, default: 5 },
  reasons: { type: Array, default: () => [] },
  comparisonText: { type: String, default: '' },
  meta: { type: Object, default: () => ({}) },
  adviceApiConfigured: { type: Boolean, default: false },
  aiAdvice: { type: String, default: '' },
  aiAdviceLoading: { type: Boolean, default: false },
  aiAdviceError: { type: String, default: '' },
})

const levelDisplay = {
  recommended: 'Recommended',
  caution: 'Caution',
  not_recommended: 'Not Recommended',
  unknown: 'Unknown',
}

function badgeClass(level) {
  if (level === 'not_recommended') return 'badge--danger'
  if (level === 'caution') return 'badge--warning'
  return 'badge--success'
}
</script>

<template>
  <div class="safety" :class="{ 'safety--overlay': overlay }">
    <div class="safety__scroll">
      <h2 class="safety__h">AI Conclusion</h2>

      <div v-if="!adviceApiConfigured" class="hint hint--muted">
        AI is not configured yet. The result below is from the local OutSafe algorithm.
      </div>

      <div v-else-if="aiAdviceLoading" class="ai-loading">
        <span class="ai-loading__dot" aria-hidden="true" />
        Generating advice...
      </div>

      <p v-else-if="aiAdviceError" class="hint hint--err">{{ aiAdviceError }}</p>

      <div v-else-if="aiAdvice" class="ai-body">{{ aiAdvice }}</div>

      <p v-else class="hint hint--muted">No AI response yet.</p>

      <h3 class="safety__sub">OutSafe Local Risk</h3>

      <p class="risk-line">
        <span :class="['badge', badgeClass(level)]">{{ levelDisplay[level] ?? 'Unknown' }}</span>
        <span class="risk-score">Safety Score {{ score }}</span>
      </p>

      <div class="metric-grid" v-if="percentiles">
        <div class="metric-card">
          <div class="metric-label">Wind Percentile</div>
          <div class="metric-value">{{ percentiles.wind ?? '—' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Rain Percentile</div>
          <div class="metric-value">{{ percentiles.rain ?? '—' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Cold Percentile</div>
          <div class="metric-value">{{ percentiles.cold ?? '—' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Years Compared</div>
          <div class="metric-value">{{ yearsBack ?? '—' }}</div>
        </div>
      </div>

      <div v-if="reasons?.length" class="reason-block">
        <h4 class="tiny-title">Key Factors</h4>
        <ul class="reason-list">
          <li v-for="r in reasons" :key="r.key">
            {{ r.label }} · Percentile {{ r.pct }}
          </li>
        </ul>
      </div>

      <div v-if="comparisonText" class="comparison">
        {{ comparisonText }}
      </div>

      <div v-if="meta?.observed_raw" class="observed-block">
        <h4 class="tiny-title">Observed Weather</h4>
        <ul class="reason-list">
          <li>Wind: {{ meta.observed_raw.wind ?? '—' }}</li>
          <li>Rain: {{ meta.observed_raw.rain ?? '—' }}</li>
          <li>Cold / Temp Min: {{ meta.observed_raw.cold ?? '—' }}</li>
        </ul>
      </div>

      <div v-if="meta" class="comparison">
        <div><strong>Data Source:</strong> {{ meta.data_source ?? '—' }}</div>
        <div><strong>Timezone:</strong> {{ meta.timezone ?? '—' }}</div>
        <div><strong>Date:</strong> {{ meta.date ?? '—' }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.safety--overlay {
  background: rgba(22, 29, 39, 0.88);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.safety__scroll {
  padding: 1rem 1.1rem 1.1rem;
  overflow-y: auto;
  overflow-x: hidden;
  flex: 1 1 0;
  min-height: 0;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.safety__scroll::-webkit-scrollbar {
  width: 8px;
}

.safety__scroll::-webkit-scrollbar-track {
  background: transparent;
}

.safety__scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.18);
  border-radius: 4px;
}

.safety__scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.28);
}

.safety__h {
  margin: 0 0 0.5rem;
  font-size: 1rem;
  font-weight: 700;
}

.safety__sub {
  margin: 1rem 0 0.4rem;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.tiny-title {
  margin: 0.75rem 0 0.35rem;
  font-size: 0.8rem;
  color: var(--muted);
  font-weight: 600;
}

.hint {
  font-size: 0.8125rem;
  line-height: 1.45;
  margin: 0 0 0.5rem;
}

.hint--muted {
  color: var(--muted);
}

.hint--err {
  color: #ff9d97;
  margin: 0 0 0.5rem;
}

.ai-loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--muted);
  margin-bottom: 0.5rem;
}

.ai-loading__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse 0.9s ease-in-out infinite alternate;
}

@keyframes pulse {
  to {
    opacity: 0.35;
    transform: scale(0.85);
  }
}

.ai-body {
  white-space: pre-wrap;
  line-height: 1.55;
  font-size: 0.875rem;
  padding: 0.65rem 0.75rem;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 0.35rem;
}

.risk-line {
  margin: 0 0 0.5rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.risk-score {
  font-size: 0.8125rem;
  color: var(--muted);
}

.metric-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.6rem;
  margin-top: 0.6rem;
}

.metric-card {
  padding: 0.65rem 0.75rem;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.metric-label {
  font-size: 0.72rem;
  color: var(--muted);
  margin-bottom: 0.2rem;
}

.metric-value {
  font-size: 1rem;
  font-weight: 700;
}

.reason-block {
  margin-top: 0.5rem;
}

.reason-list {
  margin: 0.25rem 0 0;
  padding-left: 1.1rem;
  line-height: 1.5;
  font-size: 0.84rem;
}

.comparison {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 0.78rem;
  color: var(--muted);
  line-height: 1.5;
}

.observed-block {
  margin-top: 0.75rem;
}
</style>