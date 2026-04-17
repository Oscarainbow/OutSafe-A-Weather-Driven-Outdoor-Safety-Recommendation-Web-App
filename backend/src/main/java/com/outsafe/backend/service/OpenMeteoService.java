package com.outsafe.backend.service;

import java.time.LocalDate;
import java.util.List;

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
                .fromUriString("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("daily", "temperature_2m_min,precipitation_sum,wind_speed_10m_max")
                .queryParam("timezone", timezone == null || timezone.isBlank() ? "auto" : timezone)
                .queryParam("start_date", date)
                .queryParam("end_date", date)
                .toUriString();

        ForecastResponse response = restTemplate.getForObject(url, ForecastResponse.class);

        validateDailyResponse(response, "forecast");

        return new ForecastDailyData(
                firstOrNull(response.daily().temperature_2m_min()),
                firstOrNull(response.daily().precipitation_sum()),
                firstOrNull(response.daily().wind_speed_10m_max())
        );
    }

    public ForecastDailyData getHistoricalDaily(double latitude, double longitude, LocalDate date, String timezone) {
        String url = UriComponentsBuilder
                .fromUriString("https://archive-api.open-meteo.com/v1/archive")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("daily", "temperature_2m_min,precipitation_sum,wind_speed_10m_max")
                .queryParam("timezone", timezone == null || timezone.isBlank() ? "auto" : timezone)
                .queryParam("start_date", date)
                .queryParam("end_date", date)
                .toUriString();

        ForecastResponse response = restTemplate.getForObject(url, ForecastResponse.class);

        validateDailyResponse(response, "historical");

        return new ForecastDailyData(
                firstOrNull(response.daily().temperature_2m_min()),
                firstOrNull(response.daily().precipitation_sum()),
                firstOrNull(response.daily().wind_speed_10m_max())
        );
    }

    private void validateDailyResponse(ForecastResponse response, String source) {
        if (response == null || response.daily() == null || response.daily().time() == null || response.daily().time().isEmpty()) {
            throw new RuntimeException("Failed to fetch " + source + " data from Open-Meteo");
        }
    }

    private Double firstOrNull(List<Double> values) {
        if (values == null || values.isEmpty()) return null;
        return values.get(0);
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