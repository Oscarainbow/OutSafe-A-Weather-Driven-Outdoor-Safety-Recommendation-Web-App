package com.outsafe.backend.model;

public class PredictionRequest {
    private Double temp_max;
    private Double temp_min;
    private Double apparent_min;
    private Double humidity_max;
    private Double precip_sum;
    private Double wind_max;
    private Double gust_max;

    public Double getTemp_max() { return temp_max; }
    public void setTemp_max(Double temp_max) { this.temp_max = temp_max; }

    public Double getTemp_min() { return temp_min; }
    public void setTemp_min(Double temp_min) { this.temp_min = temp_min; }

    public Double getApparent_min() { return apparent_min; }
    public void setApparent_min(Double apparent_min) { this.apparent_min = apparent_min; }

    public Double getHumidity_max() { return humidity_max; }
    public void setHumidity_max(Double humidity_max) { this.humidity_max = humidity_max; }

    public Double getPrecip_sum() { return precip_sum; }
    public void setPrecip_sum(Double precip_sum) { this.precip_sum = precip_sum; }

    public Double getWind_max() { return wind_max; }
    public void setWind_max(Double wind_max) { this.wind_max = wind_max; }

    public Double getGust_max() { return gust_max; }
    public void setGust_max(Double gust_max) { this.gust_max = gust_max; }
}