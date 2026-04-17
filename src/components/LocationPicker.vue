<script setup>
import { onMounted, ref, watch } from 'vue'

const ACTIVITY_PROMPT_KEY = 'outsafe_activity_prompt'

const emit = defineEmits(['submit'])
const lat = defineModel('lat', { type: Number, default: 41.4993 })
const lon = defineModel('lon', { type: Number, default: -81.6944 })

const elevation = ref('')
const yearsBack = ref(5)
const activityPrompt = ref('')
const copyHint = ref('')

onMounted(() => {
  try {
    const s = localStorage.getItem(ACTIVITY_PROMPT_KEY)
    if (typeof s === 'string') activityPrompt.value = s
  } catch {
    /* ignore */
  }
})

watch(activityPrompt, (v) => {
  try {
    localStorage.setItem(ACTIVITY_PROMPT_KEY, v)
  } catch {
    /* ignore */
  }
})

function formatCoord(n) {
  if (n == null || Number.isNaN(n)) return '—'
  return (Math.round(Number(n) * 1e5) / 1e5).toFixed(5)
}

async function copyCoords() {
  const text = `${formatCoord(lat.value)}, ${formatCoord(lon.value)}`
  try {
    await navigator.clipboard.writeText(text)
    copyHint.value = 'Copied'
  } catch {
    copyHint.value = 'Copy failed'
  }
  setTimeout(() => {
    copyHint.value = ''
  }, 2000)
}

function onSubmit() {
  emit('submit', {
    lat: Number(lat.value),
    lon: Number(lon.value),
    elevation: elevation.value === '' ? undefined : Number(elevation.value),
    yearsBack: Math.min(10, Math.max(1, Number(yearsBack.value) || 5)),
    activityPrompt: String(activityPrompt.value || '').trim(),
  })
}
</script>

<template>
  <div class="overlay-card">
    <h2 class="overlay-card__title">Location & Analysis</h2>
    <p class="overlay-card__lead">Click on the map to drop a pin. Coordinates will sync here. Click analyze when ready.</p>

    <div class="coord-hero">
      <div class="coord-hero__block">
        <span class="coord-hero__label">Latitude</span>
        <input
          id="ov-lat"
          v-model.number="lat"
          class="coord-hero__input"
          type="number"
          step="any"
          aria-label="Latitude"
        />
      </div>
      <div class="coord-hero__block">
        <span class="coord-hero__label">Longitude</span>
        <input
          id="ov-lon"
          v-model.number="lon"
          class="coord-hero__input"
          type="number"
          step="any"
          aria-label="Longitude"
        />
      </div>
    </div>
    <div class="coord-actions">
      <button type="button" class="btn-ghost" @click="copyCoords">Copy Coords</button>
      <span v-if="copyHint" class="copy-hint">{{ copyHint }}</span>
    </div>

    <details class="advanced">
      <summary>Advanced Options</summary>
      <div class="form-group" style="margin-top: 0.65rem">
        <label for="ov-elev">Elevation (m, optional)</label>
        <input id="ov-elev" v-model="elevation" type="number" step="any" placeholder="Leave empty for auto" />
      </div>
      <div class="form-group">
        <label for="ov-years">Historical Comparison (Years)</label>
        <input id="ov-years" v-model.number="yearsBack" type="number" min="1" max="10" />
      </div>
    </details>

    <div class="form-group" style="margin-bottom: 0.5rem">
      <label for="ov-act">Activity Description (For AI)</label>
      <textarea
        id="ov-act"
        v-model="activityPrompt"
        class="activity-textarea"
        rows="3"
        placeholder="e.g.: Hiking up a mountain, sensitive to strong winds and rain; or just a short drive."
      />
    </div>

    <button type="button" class="btn-primary" @click="onSubmit">Analyze Location</button>
  </div>
</template>

<style scoped>
.overlay-card {
  background: rgba(22, 29, 39, 0.88);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  padding: 1rem 1.1rem 1.1rem;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
}

.overlay-card__title {
  margin: 0 0 0.35rem;
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.overlay-card__lead {
  margin: 0 0 0.85rem;
  font-size: 0.8125rem;
  color: var(--muted);
  line-height: 1.45;
}

.coord-hero {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.6rem;
  margin-bottom: 0.5rem;
}

.coord-hero__block {
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 0.45rem 0.55rem;
}

.coord-hero__label {
  display: block;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--muted);
  margin-bottom: 0.2rem;
}

.coord-hero__input {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--text);
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
  padding: 0.1rem 0;
  outline: none;
}

.coord-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.65rem;
  flex-wrap: wrap;
}

.btn-ghost {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text);
  border: 1px solid rgba(255, 255, 255, 0.14);
  padding: 0.35rem 0.65rem;
  border-radius: 8px;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-ghost:hover {
  background: rgba(255, 255, 255, 0.1);
}

.copy-hint {
  font-size: 0.75rem;
  color: var(--success, #3fb950);
}

.advanced {
  margin-bottom: 0.65rem;
  font-size: 0.875rem;
  color: var(--muted);
}

.advanced summary {
  cursor: pointer;
  font-weight: 600;
  color: var(--accent);
  user-select: none;
}

.advanced summary::-webkit-details-marker {
  display: none;
}

.advanced[open] summary {
  margin-bottom: 0.25rem;
}

.activity-textarea {
  width: 100%;
  box-sizing: border-box;
  font: inherit;
  padding: 0.5rem 0.6rem;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.2);
  color: var(--text);
  resize: vertical;
  min-height: 4rem;
  line-height: 1.45;
}

.btn-primary {
  width: 100%;
  margin-top: 0.25rem;
  padding: 0.65rem 1rem;
  border-radius: 10px;
  border: none;
  font-weight: 700;
  font-size: 0.9375rem;
  cursor: pointer;
  background: linear-gradient(180deg, #6eb4ff 0%, var(--accent) 100%);
  color: #fff;
  box-shadow: 0 2px 12px rgba(88, 166, 255, 0.35);
}

.btn-primary:hover {
  filter: brightness(1.05);
}

.btn-primary:active {
  transform: translateY(1px);
}
</style>
