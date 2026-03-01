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

  try {
    // 先拉预报；API 的 hourly.time 是当地时区，用其日期避免 UTC 与当地日期不一致
    const forecast = await fetchForecast({ lat, lon, elevation })
    const queryDate =
      forecast.hourly?.time?.[0]?.slice(0, 10) ||
      new Date().toISOString().slice(0, 10)

    const todayMetrics = aggregateDayMetrics(forecast.hourly, queryDate)
    if (!todayMetrics) {
      error.value = '无法解析今日小时数据'
      return
    }

    const { list: archiveList, requested: archiveRequested } = await fetchArchiveSameDay({
      lat,
      lon,
      elevation,
      date: queryDate,
      yearsBack,
    })
    // 每年 archive 返回的是该年当日，需用该条数据的日期做聚合（不能用 queryDate）
    const historyMetricsList = archiveList.map((ar) => {
      const dateInArchive = ar.hourly?.time?.[0]?.slice(0, 10)
      return aggregateDayMetrics(ar.hourly, dateInArchive || queryDate)
    })
    const validHistory = historyMetricsList.filter(Boolean)
    const percentiles = computePercentiles(todayMetrics, validHistory)
    const { score, level } = compositeRisk(percentiles)

    result.value = {
      level,
      score,
      percentiles: percentiles || {},
      yearsBack,
      hasHistory: validHistory.length > 0,
      archiveRequested,
      archiveSuccess: archiveList.length,
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
    :has-history="result.hasHistory"
    :archive-requested="result.archiveRequested"
    :archive-success="result.archiveSuccess"
  />
</template>
