<script setup>
import { ref } from 'vue'
import MapPicker from './components/MapPicker.vue'
import LocationPicker from './components/LocationPicker.vue'
import SafetyResult from './components/SafetyResult.vue'

const lat = ref(41.4993)
const lon = ref(-81.6944)

const loading = ref(false)
const error = ref('')
const result = ref(null)
const aiAdvice = ref('')
const aiAdviceLoading = ref(false)
const aiAdviceError = ref('')
const rawMeteoData = ref(null)

function onMapSelect({ lat: la, lon: lo }) {
  lat.value = Math.round(la * 1e5) / 1e5
  lon.value = Math.round(lo * 1e5) / 1e5
}

function isAdviceApiConfigured() {
  return false
}

async function onLocationSubmit({ lat: subLat, lon: subLon, elevation, yearsBack, activityPrompt }) {
  loading.value = true
  error.value = ''
  result.value = null
  aiAdvice.value = ''
  aiAdviceError.value = ''
  aiAdviceLoading.value = false
  rawMeteoData.value = null

  lat.value = subLat
  lon.value = subLon

  try {
    // Mock the UI state since API logic is removed for future integration
    setTimeout(() => {
      result.value = {
        level: 'caution',
        score: 65,
        percentiles: { wind: 80, rain: 20, cold: 60 },
        yearsBack,
        hasHistory: true,
        archiveRequested: yearsBack,
        archiveSuccess: yearsBack,
        historySampleDays: yearsBack * 30,
      }
      loading.value = false
    }, 800)
  } catch (e) {
    error.value = e.message || '请求失败，请检查网络或稍后重试'
    loading.value = false
  }
}
</script>

<template>
  <div class="map-app">
    <MapPicker fullscreen :lat="lat" :lon="lon" @select="onMapSelect" />

    <header class="map-app__brand" aria-label="OutSafe">
      <span class="map-app__brand-name">OutSafe</span>
      <span class="map-app__brand-sub">Outdoor Weather Risk</span>
    </header>

    <aside class="map-app__sidebar" aria-label="Location and Analysis">
      <LocationPicker v-model:lat="lat" v-model:lon="lon" @submit="onLocationSubmit" />

      <div v-if="loading" class="overlay-card overlay-card--status" role="status" aria-live="polite">
        <div class="spinner" aria-hidden="true" />
        <p class="status-text">Fetching forecast and historical data...</p>
      </div>

      <div v-else-if="error" class="overlay-card overlay-card--error">
        {{ error }}
      </div>

      <SafetyResult
        v-if="result"
        overlay
        :level="result.level"
        :score="result.score"
        :percentiles="result.percentiles"
        :years-back="result.yearsBack"
        :has-history="result.hasHistory"
        :archive-requested="result.archiveRequested"
        :archive-success="result.archiveSuccess"
        :history-sample-days="result.historySampleDays"
        :advice-api-configured="isAdviceApiConfigured()"
        :ai-advice="aiAdvice"
        :ai-advice-loading="aiAdviceLoading"
        :ai-advice-error="aiAdviceError"
      />
    </aside>
  </div>
</template>

<style scoped>
.map-app {
  position: fixed;
  inset: 0;
  z-index: 0;
  background: var(--bg);
}

.map-app__brand {
  position: absolute;
  z-index: 400;
  top: 12px;
  left: 12px;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(15, 20, 25, 0.72);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.35);
  pointer-events: none;
}

.map-app__brand-name {
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.map-app__brand-sub {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.map-app__sidebar {
  position: absolute;
  z-index: 500;
  top: 12px;
  right: 12px;
  bottom: 12px;
  width: min(380px, calc(100vw - 24px));
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
  pointer-events: none;
}

.map-app__sidebar > * {
  pointer-events: auto;
}

.map-app__sidebar > *:not(.safety--overlay) {
  flex-shrink: 0;
}

.map-app__sidebar > .safety--overlay {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 640px) {
  .map-app__sidebar {
    top: auto;
    left: 8px;
    right: 8px;
    bottom: 8px;
    width: auto;
    max-height: min(58vh, 520px);
  }

  .map-app__brand {
    top: 8px;
    left: 8px;
    padding: 8px 12px;
  }
}

.overlay-card {
  background: rgba(22, 29, 39, 0.88);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
}

.overlay-card--status {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
}

.spinner {
  width: 1.35rem;
  height: 1.35rem;
  flex-shrink: 0;
  border: 2px solid rgba(255, 255, 255, 0.15);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.status-text {
  margin: 0;
  font-size: 0.875rem;
  color: var(--muted);
}

.overlay-card--error {
  padding: 0.85rem 1rem;
  color: #ffb4b0;
  font-size: 0.875rem;
  line-height: 1.45;
  border-color: rgba(248, 81, 73, 0.45);
  background: rgba(248, 81, 73, 0.14);
}
</style>
