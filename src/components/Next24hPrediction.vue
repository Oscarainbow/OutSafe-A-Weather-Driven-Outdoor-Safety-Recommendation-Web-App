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
      <h2>Predicted Weather in 24 Hours</h2>
      <p>Frontend Random Forest prediction</p>
    </div>

    <h3 class="next24-card__summary">
      {{ prediction.summary }}
    </h3>

    <div class="next24-card__grid">
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
  padding: 1rem;
}

.next24-card__header h2 {
  margin: 0;
  font-size: 1rem;
}

.next24-card__header p {
  margin: 0.25rem 0 0;
  color: var(--muted);
  font-size: 0.85rem;
}

.next24-card__summary {
  margin: 0.9rem 0;
  font-size: 1.15rem;
}

.next24-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
}

.metric {
  padding: 0.7rem 0.8rem;
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
</style>