package com.outsafe.backend.model;

public class PredictionResponse {
    private String summary;
    private Double temp;
    private Double tempMin;
    private Double apparent;
    private Double humidity;
    private Double precipitation;
    private Double wind;
    private Double gust;

    public PredictionResponse() {}

    public PredictionResponse(String summary, Double temp, Double tempMin, Double apparent,
                              Double humidity, Double precipitation, Double wind, Double gust) {
        this.summary = summary;
        this.temp = temp;
        this.tempMin = tempMin;
        this.apparent = apparent;
        this.humidity = humidity;
        this.precipitation = precipitation;
        this.wind = wind;
        this.gust = gust;
    }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Double getTemp() { return temp; }
    public void setTemp(Double temp) { this.temp = temp; }

    public Double getTempMin() { return tempMin; }
    public void setTempMin(Double tempMin) { this.tempMin = tempMin; }

    public Double getApparent() { return apparent; }
    public void setApparent(Double apparent) { this.apparent = apparent; }

    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }

    public Double getPrecipitation() { return precipitation; }
    public void setPrecipitation(Double precipitation) { this.precipitation = precipitation; }

    public Double getWind() { return wind; }
    public void setWind(Double wind) { this.wind = wind; }

    public Double getGust() { return gust; }
    public void setGust(Double gust) { this.gust = gust; }
}