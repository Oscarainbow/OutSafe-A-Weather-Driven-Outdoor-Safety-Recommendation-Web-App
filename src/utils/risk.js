/**
 * 对“今天”和每个历史年份做窗口聚合，再算历史百分位与综合风险分
 */

/**
 * 从 API 的 hourly 里取“当日”小时并做聚合，得到单日指标
 * @param {Object} hourly - { time: string[], temperature_2m, ... }
 * @param {string} date - YYYY-MM-DD，只保留这一天的点
 */
export function aggregateDayMetrics(hourly, date) {
  if (!hourly?.time?.length) return null
  const dateStr = date.slice(0, 10)
  const indices = hourly.time
    .map((t, i) => (String(t).startsWith(dateStr) ? i : -1))
    .filter(i => i >= 0)
  if (indices.length === 0) return null

  const pick = (arr) => indices.map(i => arr[i]).filter(v => v != null)
  const temp = pick(hourly.temperature_2m)
  const appTemp = pick(hourly.apparent_temperature)
  const humidity = pick(hourly.relative_humidity_2m)
  const precip = pick(hourly.precipitation)
  const wind = pick(hourly.windspeed_10m)
  const gust = pick(hourly.windgusts_10m)

  const max = (a) => (a.length ? Math.max(...a) : null)
  const min = (a) => (a.length ? Math.min(...a) : null)
  const sum = (a) => (a.length ? a.reduce((s, x) => s + x, 0) : null)
  const avg = (a) => (a.length ? sum(a) / a.length : null)

  return {
    temp_max: max(temp),
    temp_min: min(temp),
    apparent_min: min(appTemp),
    humidity_max: max(humidity),
    precip_sum: sum(precip),
    wind_max: max(wind),
    gust_max: max(gust),
    // 用于风险：体感最低、风最大、降水总量
    risk_cold: min(appTemp),
    risk_wind: max(gust),
    risk_rain: sum(precip),
  }
}

/**
 * 计算 value 在 sortedArr 中的百分位 (0~100)
 * 越大表示越“极端”（越高/越低视指标而定）
 */
function percentileRank(value, sortedArr) {
  if (!sortedArr.length) return 50
  const sorted = [...sortedArr].sort((a, b) => a - b)
  let count = 0
  for (const v of sorted) {
    if (v < value) count++
    else break
  }
  return Math.round((count / sorted.length) * 100)
}

/**
 * 风、降水等：越高越危险，百分位高 = 更极端
 * 体感温度：越低越危险，用 100 - 百分位 表示“多极端”
 */
export function computePercentiles(todayMetrics, historyMetricsList) {
  if (!todayMetrics || !historyMetricsList?.length) return null
  const keys = ['risk_wind', 'risk_rain', 'risk_cold']
  const hist = historyMetricsList.map(m => m).filter(Boolean)
  const windVals = hist.map(m => m.risk_wind).filter(v => v != null)
  const rainVals = hist.map(m => m.risk_rain).filter(v => v != null)
  const coldVals = hist.map(m => m.risk_cold).filter(v => v != null)

  const windPct = windVals.length ? percentileRank(todayMetrics.risk_wind, windVals) : 50
  const rainPct = rainVals.length ? percentileRank(todayMetrics.risk_rain, rainVals) : 50
  // 体感越冷越危险：cold 越小越极端，用 100 - 百分位
  const coldPct = coldVals.length ? 100 - percentileRank(todayMetrics.risk_cold, coldVals) : 50

  return {
    wind: windPct,
    rain: rainPct,
    cold: coldPct,
  }
}

/**
 * 综合风险分 0~100，越高越不推荐
 * 取各指标百分位的加权平均，再映射为总评
 */
export function compositeRisk(percentiles, weights = { wind: 0.35, rain: 0.35, cold: 0.3 }) {
  if (!percentiles) return { score: 50, level: 'unknown' }
  const { wind = 50, rain = 50, cold = 50 } = percentiles
  const score = Math.round(
    (wind * (weights.wind ?? 1/3) + rain * (weights.rain ?? 1/3) + cold * (weights.cold ?? 1/3)) /
    ((weights.wind ?? 1/3) + (weights.rain ?? 1/3) + (weights.cold ?? 1/3))
  )
  const level = score >= 70 ? 'not_recommended' : score >= 40 ? 'caution' : 'recommended'
  return { score: Math.min(100, Math.max(0, score)), level }
}

/** 总评文案 */
export function levelLabel(level) {
  const map = { not_recommended: '不建议', caution: '谨慎', recommended: '推荐', unknown: '未知' }
  return map[level] ?? '未知'
}

/**
 * 取 Top 2~3 指标（百分位从高到低）作为关键原因
 */
export function topReasons(percentiles, limit = 3) {
  if (!percentiles) return []
  const items = [
    { key: 'wind', label: '风', pct: percentiles.wind },
    { key: 'rain', label: '降水', pct: percentiles.rain },
    { key: 'cold', label: '体感寒冷', pct: percentiles.cold },
  ].filter(i => i.pct != null)
  items.sort((a, b) => b.pct - a.pct)
  return items.slice(0, limit)
}

/**
 * 生成“与过去 N 年相比”的文案
 */
export function comparisonText(percentiles, n) {
  if (!percentiles) return ''
  const parts = []
  if (percentiles.wind >= 70) parts.push(`风更极端（${percentiles.wind}%）`)
  if (percentiles.rain >= 70) parts.push(`降水偏多（${percentiles.rain}%）`)
  if (percentiles.cold >= 70) parts.push(`体感更冷（${percentiles.cold}%）`)
  if (percentiles.wind <= 30) parts.push(`风较温和（${percentiles.wind}%）`)
  if (percentiles.rain <= 30) parts.push(`降水较少（${percentiles.rain}%）`)
  if (percentiles.cold <= 30) parts.push(`体感较暖（${percentiles.cold}%）`)
  if (parts.length === 0) return `与过去 ${n} 年同日相比，整体接近历史水平。`
  return `与过去 ${n} 年同日相比：${parts.join('，')}。`
}
