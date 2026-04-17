export async function fetchSafetyRecommendation({
  lat,
  lon,
  elevation,
  yearsBack,
  timezone = 'America/New_York',
}) {
  const params = new URLSearchParams({
    lat: String(lat),
    lon: String(lon),
    years_back: String(yearsBack ?? 5),
    timezone,
  })

  if (elevation !== undefined && elevation !== null && elevation !== '') {
    params.set('elevation', String(elevation))
  }

  const response = await fetch(`/api/safety/recommend?${params.toString()}`)

  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `Backend request failed: ${response.status}`)
  }

  return await response.json()
}