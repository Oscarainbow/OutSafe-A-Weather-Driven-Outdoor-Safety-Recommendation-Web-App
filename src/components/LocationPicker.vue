<script setup>
import { ref } from 'vue'
import MapPicker from './MapPicker.vue'

const emit = defineEmits(['submit'])
const lat = ref(39.9)
const lon = ref(116.4)
const elevation = ref('')
const yearsBack = ref(5)

function onMapSelect({ lat: newLat, lon: newLon }) {
  lat.value = Math.round(newLat * 1e5) / 1e5
  lon.value = Math.round(newLon * 1e5) / 1e5
}

function onSubmit() {
  const payload = {
    lat: Number(lat.value),
    lon: Number(lon.value),
    elevation: elevation.value === '' ? undefined : Number(elevation.value),
    yearsBack: Math.min(10, Math.max(1, Number(yearsBack.value) || 5)),
  }
  emit('submit', payload)
}
</script>

<template>
  <div class="card">
    <h2 style="margin-top: 0;">选择地点</h2>
    <p class="muted" style="margin-bottom: 0.5rem;">点击地图或输入经纬度，时区将自动识别（timezone=auto）</p>
    <MapPicker :lat="lat" :lon="lon" @select="onMapSelect" />
    <div class="form-row">
      <div class="form-group">
        <label>纬度</label>
        <input v-model.number="lat" type="number" step="any" placeholder="39.9" />
      </div>
      <div class="form-group">
        <label>经度</label>
        <input v-model.number="lon" type="number" step="any" placeholder="116.4" />
      </div>
    </div>
    <div class="form-group">
      <label>海拔（米，可选）</label>
      <input v-model="elevation" type="number" step="any" placeholder="留空则自动" />
    </div>
    <div class="form-group">
      <label>对比历史年数（同日）</label>
      <input v-model.number="yearsBack" type="number" min="1" max="10" />
    </div>
    <button type="button" @click="onSubmit">获取户外安全建议</button>
  </div>
</template>

<style scoped>
.muted { color: var(--muted); font-size: 0.9rem; }
</style>
