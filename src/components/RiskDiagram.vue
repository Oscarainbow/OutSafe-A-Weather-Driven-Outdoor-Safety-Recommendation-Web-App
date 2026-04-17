<script setup>
const props = defineProps({
  metrics: {
    type: Array,
    default: () => []
  }
})

function getPct(val, item) {
  if (val < item.min) return 0
  if (val > item.max) return 100
  return ((val - item.min) / (item.max - item.min)) * 100
}
</script>

<template>
  <div class="risk-diagram">
    <div class="rd-header">
      <h4 class="rd-title">TODAY VS PAST (SAME MONTH)</h4>
      <p class="rd-subtitle">Gray band = historical IQR (25–75%); line = median. Marker = today. Based on up to 5 years of daily samples.</p>
    </div>
    <div class="rd-body">
      <div class="rd-row" v-for="item in metrics" :key="item.label">
        <div class="rd-labels">
          <span class="rd-label">{{ item.label }}</span>
          <span class="rd-val-text">
            <strong class="rd-current-val">{{ item.value }}</strong> {{ item.unit }} &middot; median {{ item.median }}
          </span>
        </div>
        <div class="rd-track-wrap">
          <span class="rd-bound">{{ item.min }}</span>
          <div class="rd-track">
            <div class="rd-iqr" :style="{ left: getPct(item.q25, item) + '%', width: (getPct(item.q75, item) - getPct(item.q25, item)) + '%' }"></div>
            <div class="rd-median" :style="{ left: getPct(item.median, item) + '%' }"></div>
            <div class="rd-marker" :style="{ left: getPct(item.value, item) + '%' }"></div>
          </div>
          <span class="rd-bound">{{ item.max }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.risk-diagram {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 1rem;
  margin-top: 1rem;
}
.rd-title {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--muted, #8b949e);
  margin: 0 0 0.25rem 0;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}
.rd-subtitle {
  font-size: 0.75rem;
  color: var(--muted, #8b949e);
  margin: 0 0 1rem 0;
  line-height: 1.4;
}
.rd-row {
  margin-bottom: 1.25rem;
}
.rd-row:last-child {
  margin-bottom: 0;
}
.rd-labels {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.4rem;
  font-size: 0.85rem;
}
.rd-label {
  font-weight: 600;
  color: #e6edf3;
}
.rd-val-text {
  color: var(--muted, #8b949e);
  font-size: 0.8rem;
}
.rd-current-val {
  color: #58a6ff;
  font-weight: 700;
  font-size: 0.9rem;
}
.rd-track-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.rd-bound {
  font-size: 0.7rem;
  color: var(--muted, #8b949e);
  width: 2rem;
}
.rd-bound:last-child {
  text-align: right;
}
.rd-track {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  position: relative;
}
.rd-iqr {
  position: absolute;
  top: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 3px;
}
.rd-median {
  position: absolute;
  top: -2px;
  bottom: -2px;
  width: 2px;
  background: #fff;
  transform: translateX(-50%);
}
.rd-marker {
  position: absolute;
  top: -6px;
  width: 10px;
  height: 18px;
  background: #58a6ff;
  border: 2px solid #161b22;
  border-radius: 4px;
  transform: translateX(-50%);
  box-shadow: 0 2px 4px rgba(0,0,0,0.5);
  transition: left 0.3s ease-out;
}
</style>
