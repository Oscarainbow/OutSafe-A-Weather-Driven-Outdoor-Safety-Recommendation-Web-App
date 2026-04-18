<script setup>
defineProps({
  prediction: {
    type: Object,
    default: null,
  },
})

function fmt(value, suffix = '') {
  if (value == null || Number.isNaN(Number(value))) return '--'
  return `${Math.round(Number(value) * 10) / 10}${suffix}`
}
</script>

<template>
  <section v-if="prediction" class="overlay-card next24-card">
    <div class="next24-card__header">
      <div>
        <h3 class="next24-card__title">Predicted Weather in 24 Hours</h3>
        <p class="muted">Random Forest prediction</p>
      </div>
      <span class="next24-card__summary">{{ prediction.summary }}</span>
    </div>

    <div class="next24-grid">
      <div class="metric">
        <span>High temp</span>
        <strong>{{ fmt(prediction.temp, '°C') }}</strong>
      </div>
      <div class="metric">
        <span>Low temp</span>
        <strong>{{ fmt(prediction.tempMin, '°C') }}</strong>
      </div>
      <div class="metric">
        <span>Feels like low</span>
        <strong>{{ fmt(prediction.apparent, '°C') }}</strong>
      </div>
      <div class="metric">
        <span>Humidity</span>
        <strong>{{ fmt(prediction.humidity, '%') }}</strong>
      </div>
      <div class="metric">
        <span>Rain</span>
        <strong>{{ fmt(prediction.precipitation, ' mm') }}</strong>
      </div>
      <div class="metric">
        <span>Wind</span>
        <strong>{{ fmt(prediction.wind, ' km/h') }}</strong>
      </div>
      <div class="metric">
        <span>Gust</span>
        <strong>{{ fmt(prediction.gust, ' km/h') }}</strong>
      </div>
    </div>
  </section>
</template>

<style scoped>
.next24-card {
  padding: 1rem 1.1rem 1.1rem;
  flex: 0 0 auto;
}

.next24-card__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
  margin-bottom: 0.9rem;
}

.next24-card__title {
  margin: 0 0 0.25rem;
  font-size: 1rem;
  font-weight: 700;
}

.next24-card__summary {
  padding: 0.35rem 0.65rem;
  border-radius: 999px;
  background: rgba(88, 166, 255, 0.16);
  color: var(--accent);
  font-weight: 700;
  font-size: 0.85rem;
}

.muted {
  margin: 0;
  color: var(--muted);
  font-size: 0.84rem;
}

.next24-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
}

.metric {
  padding: 0.75rem 0.8rem;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
}

.metric span {
  display: block;
  font-size: 0.78rem;
  color: var(--muted);
  margin-bottom: 0.2rem;
}

.metric strong {
  font-size: 0.96rem;
}

@media (max-width: 480px) {
  .next24-grid {
    grid-template-columns: 1fr;
  }
}
</style>
