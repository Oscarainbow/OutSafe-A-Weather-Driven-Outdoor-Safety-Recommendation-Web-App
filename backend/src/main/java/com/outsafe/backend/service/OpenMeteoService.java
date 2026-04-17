package com.outsafe.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OpenMeteoService {

    private final RestTemplate restTemplate;

    public OpenMeteoService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ForecastDailyData getForecastDaily(double latitude, double longitude, LocalDate date, String timezone) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("daily", "temperature_2m_min,precipitation_sum,wind_speed_10m_max")
                .queryParam("timezone", timezone == null || timezone.isBlank() ? "auto" : timezone)
                .queryParam("start_date", date)
                .queryParam("end_date", date)
                .toUriString();

        ForecastResponse response = restTemplate.getForObject(url, ForecastResponse.class);

        if (response == null || response.daily() == null || response.daily().time() == null || response.daily().time().isEmpty()) {
            throw new RuntimeException("Failed to fetch forecast data from Open-Meteo");
        }

        return new ForecastDailyData(
                response.daily().temperature_2m_min().get(0),
                response.daily().precipitation_sum().get(0),
                response.daily().wind_speed_10m_max().get(0)
        );
    }

    public ForecastDailyData getHistoricalDaily(double latitude, double longitude, LocalDate date, String timezone) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://archive-api.open-meteo.com/v1/archive")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("daily", "temperature_2m_min,precipitation_sum,wind_speed_10m_max")
                .queryParam("timezone", timezone == null || timezone.isBlank() ? "auto" : timezone)
                .queryParam("start_date", date)
                .queryParam("end_date", date)
                .toUriString();

        ForecastResponse response = restTemplate.getForObject(url, ForecastResponse.class);

        if (response == null || response.daily() == null || response.daily().time() == null || response.daily().time().isEmpty()) {
            throw new RuntimeException("Failed to fetch historical data from Open-Meteo");
        }

        return new ForecastDailyData(
                response.daily().temperature_2m_min().get(0),
                response.daily().precipitation_sum().get(0),
                response.daily().wind_speed_10m_max().get(0)
        );
    }

    public record ForecastDailyData(
            Double temperatureMin,
            Double precipitationSum,
            Double windSpeedMax
    ) {}

    public record ForecastResponse(
            Daily daily
    ) {}

    public record Daily(
            List<String> time,
            List<Double> temperature_2m_min,
            List<Double> precipitation_sum,
            List<Double> wind_speed_10m_max
    ) {}
}