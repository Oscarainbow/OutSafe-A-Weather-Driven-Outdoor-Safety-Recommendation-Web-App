package com.outsafe.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OpenMeteoService {

    private static final String DAILY_FIELDS =
            "temperature_2m_max,temperature_2m_min,apparent_temperature_min,precipitation_sum,"
                    + "wind_speed_10m_max,wind_gusts_10m_max";

    private final RestTemplate restTemplate;

    public OpenMeteoService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ForecastDailyData getForecastDaily(double latitude, double longitude, LocalDate date, String timezone) {
        String url = UriComponentsBuilder
                .fromUriString("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("daily", DAILY_FIELDS)
                .queryParam("timezone", timezone == null || timezone.isBlank() ? "auto" : timezone)
                .queryParam("start_date", date)
                .queryParam("end_date", date)
                .toUriString();

        ForecastResponse response = restTemplate.getForObject(url, ForecastResponse.class);

        validateDailyResponse(response, "forecast");

        return rowAtIndex(response.daily(), 0);
    }

    public ForecastDailyData getHistoricalDaily(double latitude, double longitude, LocalDate date, String timezone) {
        String url = UriComponentsBuilder
                .fromUriString("https://archive-api.open-meteo.com/v1/archive")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("daily", DAILY_FIELDS)
                .queryParam("timezone", timezone == null || timezone.isBlank() ? "auto" : timezone)
                .queryParam("start_date", date)
                .queryParam("end_date", date)
                .toUriString();

        ForecastResponse response = restTemplate.getForObject(url, ForecastResponse.class);

        validateDailyResponse(response, "historical");

        return rowAtIndex(response.daily(), 0);
    }

    /**
     * Historical daily series for a date range (inclusive). Used to pool &quot;same calendar month&quot; samples
     * across past years for the Today vs Past diagram.
     */
    public List<ForecastDailyData> getHistoricalDailyRange(double latitude, double longitude,
                                                          LocalDate start, LocalDate end, String timezone) {
        if (start == null || end == null || start.isAfter(end)) {
            return List.of();
        }
        String url = UriComponentsBuilder
                .fromUriString("https://archive-api.open-meteo.com/v1/archive")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("daily", DAILY_FIELDS)
                .queryParam("timezone", timezone == null || timezone.isBlank() ? "auto" : timezone)
                .queryParam("start_date", start)
                .queryParam("end_date", end)
                .toUriString();

        ForecastResponse response = restTemplate.getForObject(url, ForecastResponse.class);

        validateDailyResponse(response, "historical range");

        Daily d = response.daily();
        List<ForecastDailyData> out = new ArrayList<>(d.time().size());
        for (int i = 0; i < d.time().size(); i++) {
            out.add(rowAtIndex(d, i));
        }
        return out;
    }

    private void validateDailyResponse(ForecastResponse response, String source) {
        if (response == null || response.daily() == null || response.daily().time() == null || response.daily().time().isEmpty()) {
            throw new RuntimeException("Failed to fetch " + source + " data from Open-Meteo");
        }
    }

    private Double atOrNull(List<Double> values, int index) {
        if (values == null || index < 0 || index >= values.size()) return null;
        return values.get(index);
    }

    private ForecastDailyData rowAtIndex(Daily d, int i) {
        return new ForecastDailyData(
                atOrNull(d.temperature_2m_max(), i),
                atOrNull(d.temperature_2m_min(), i),
                atOrNull(d.apparent_temperature_min(), i),
                atOrNull(d.precipitation_sum(), i),
                atOrNull(d.wind_speed_10m_max(), i),
                atOrNull(d.wind_gusts_10m_max(), i)
        );
    }

    public record ForecastDailyData(
            Double temperatureMax,
            Double temperatureMin,
            Double apparentTemperatureMin,
            Double precipitationSum,
            Double windSpeedMax,
            Double windGustMax
    ) {}

    public record ForecastResponse(
            Daily daily
    ) {}

    public record Daily(
            List<String> time,
            List<Double> temperature_2m_max,
            List<Double> temperature_2m_min,
            List<Double> apparent_temperature_min,
            List<Double> precipitation_sum,
            List<Double> wind_speed_10m_max,
            List<Double> wind_gusts_10m_max
    ) {}
}