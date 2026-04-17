function predictTree(tree, featureMap) {
    let node = 0
  
    while (true) {
      const left = tree.children_left[node]
      const right = tree.children_right[node]
  
      if (left === -1 && right === -1) {
        return tree.value[node]
      }
  
      const featureIndex = tree.feature[node]
      const threshold = tree.threshold[node]
      const featureName = featureMap[featureIndex]
      const value = Number(featureMap.__input__[featureName] ?? 0)
  
      node = value <= threshold ? left : right
    }
  }
  
  function predictForest(forest, inputFeatures) {
    const featureNames = forest.feature_names
    const treeInput = { ...featureNames, __input__: inputFeatures }
  
    const preds = forest.trees.map((tree) => predictTree(tree, treeInput))
    const avg = preds.reduce((a, b) => a + b, 0) / preds.length
    return avg
  }
  
  export function predict24hWeather(modelJson, todayMetrics) {
    const models = modelJson.models
  
    const temp = predictForest(models.next_temp_max, todayMetrics)
    const tempMin = predictForest(models.next_temp_min, todayMetrics)
    const apparent = predictForest(models.next_apparent_min, todayMetrics)
    const humidity = predictForest(models.next_humidity_max, todayMetrics)
    const precipitation = predictForest(models.next_precip_sum, todayMetrics)
    const wind = predictForest(models.next_wind_max, todayMetrics)
    const gust = predictForest(models.next_gust_max, todayMetrics)
  
    let summary = 'Mild'
    if (precipitation >= 8) summary = 'Rain likely'
    else if (wind >= 35) summary = 'Windy'
    else if (temp >= 28) summary = 'Warm'
    else if (tempMin <= 0) summary = 'Cold'
  
    return {
      summary,
      temp,
      tempMin,
      apparent,
      humidity,
      precipitation,
      wind,
      gust,
    }
  }