export async function fetchPredicted24h(metrics) {
    const res = await fetch('/api/predict/24h', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(metrics),
    })
  
    if (!res.ok) {
      throw new Error(`Prediction API failed: ${res.status}`)
    }
  
    return await res.json()
  }