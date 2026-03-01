<script setup>
import { ref } from 'vue'
import { fetchForecast, fetchArchiveSameDay } from './api/weather'
import {
  aggregateDayMetrics,
  computePercentiles,
  compositeRisk,
} from './utils/risk'
import LocationPicker from './components/LocationPicker.vue'
import SafetyResult from './components/SafetyResult.vue'

const loading = ref(false)
const error = ref('')
const result = ref(null) // { level, score, percentiles, yearsBack }

async function onLocationSubmit({ lat, lon, elevation, yearsBack }) {
  loading.value = true
  error.value = ''
  result.value = null
  const today = new Date().toISOString().slice(0, 10)

  try {
    const [forecast, archiveList] = await Promise.all([
      fetchForecast({ lat, lon, elevation }),
      fetchArchiveSameDay({ lat, lon, elevation, date: today, yearsBack }),
    ])

    const todayMetrics = aggregateDayMetrics(forecast.hourly, today)
    if (!todayMetrics) {
      error.value = '无法解析今日小时数据'
      return
    }

    const historyMetricsList = archiveList.map((ar) =>
      aggregateDayMetrics(ar.hourly, today)
    )
    const percentiles = computePercentiles(todayMetrics, historyMetricsList)
    const { score, level } = compositeRisk(percentiles)

    result.value = {
      level,
      score,
      percentiles: percentiles || {},
      yearsBack,
    }
  } catch (e) {
    error.value = e.message || '请求失败，请检查网络或稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <h1 style="margin-bottom: 1rem;">OutSafe · 户外安全建议</h1>
  <LocationPicker @submit="onLocationSubmit" />
  <div v-if="loading" class="loading">正在拉取天气与历史数据…</div>
  <p v-else-if="error" class="error">{{ error }}</p>
  <SafetyResult
    v-else-if="result"
    :level="result.level"
    :score="result.score"
    :percentiles="result.percentiles"
    :years-back="result.yearsBack"
  />
</template>
