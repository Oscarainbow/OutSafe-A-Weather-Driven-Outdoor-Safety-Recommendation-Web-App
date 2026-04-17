package com.outsafe.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.outsafe.backend.model.SafetyRecommendRequest;
import com.outsafe.backend.model.SafetyRecommendResponse;
import com.outsafe.backend.service.OpenMeteoService.ForecastDailyData;

@Service
public class SafetyService {

    private final OpenMeteoService openMeteoService;

    public SafetyService(OpenMeteoService openMeteoService) {
        this.openMeteoService = openMeteoService;
    }

    private static final Map<String, Double> WEIGHTS = Map.of(
            "wind", 0.40,
            "rain", 0.30,
            "cold", 0.30
    );

    private static final double RECOMMENDED_MIN = 70.0;
    private static final double CAUTION_MIN = 40.0;

    public SafetyRecommendResponse recommend(SafetyRecommendRequest request) {

        if (request.latitude() == null || request.longitude() == null) {
            throw new IllegalArgumentException("latitude and longitude are required");
        }

        LocalDate date = (request.date() == null) ? LocalDate.now() : request.date();
        int yearsBack = (request.years_back() == null || request.years_back() <= 0) ? 5 : request.years_back();
        String timezone = (request.timezone() == null || request.timezone().isBlank()) ? "auto" : request.timezone();

        double lat = request.latitude();
        double lon = request.longitude();

        // 1) observed: selected day's weather
        ForecastDailyData observedDaily = openMeteoService.getForecastDaily(lat, lon, date, timezone);

        Map<String, Double> observed = new HashMap<>();
        observed.put("wind", nullSafe(observedDaily.windSpeedMax()));
        observed.put("rain", nullSafe(observedDaily.precipitationSum()));
        observed.put("cold", nullSafe(observedDaily.temperatureMin()));
        observed.put("high", nullSafe(observedDaily.temperatureMax()));

        // 2) historical: same date in past N years
        Map<String, List<Double>> historical = new HashMap<>();
        historical.put("wind", new ArrayList<>());
        historical.put("rain", new ArrayList<>());
        historical.put("cold", new ArrayList<>());
        historical.put("high", new ArrayList<>());

        for (int i = 1; i <= yearsBack; i++) {
            LocalDate pastDate = date.minusYears(i);
            try {
                ForecastDailyData hist = openMeteoService.getHistoricalDaily(lat, lon, pastDate, timezone);

                historical.get("wind").add(nullSafe(hist.windSpeedMax()));
                historical.get("rain").add(nullSafe(hist.precipitationSum()));
                historical.get("cold").add(nullSafe(hist.temperatureMin()));
                historical.get("high").add(nullSafe(hist.temperatureMax()));
            } catch (Exception e) {
                // skip bad historical year
            }
        }

        Map<String, Double> observedRiskValue = new HashMap<>();
        observedRiskValue.put("wind", observed.get("wind"));
        observedRiskValue.put("rain", observed.get("rain"));
        observedRiskValue.put("cold", -observed.get("cold"));

        Map<String, List<Double>> historicalRiskValue = new HashMap<>();
        historicalRiskValue.put("wind", historical.get("wind"));
        historicalRiskValue.put("rain", historical.get("rain"));
        historicalRiskValue.put("cold", historical.get("cold").stream().map(v -> -v).toList());

        Map<String, Integer> percentiles = new HashMap<>();
        for (String key : WEIGHTS.keySet()) {
            double v = observedRiskValue.get(key);
            List<Double> hist = historicalRiskValue.get(key);
            int pct = percentile(v, hist);
            percentiles.put(key, pct);
        }

        double riskScore = 0.0;
        for (String key : WEIGHTS.keySet()) {
            riskScore += percentiles.get(key) * WEIGHTS.get(key);
        }

        double safetyScore = clamp(100.0 - riskScore, 0.0, 100.0);
        String level = levelFromScore(safetyScore);
        List<SafetyRecommendResponse.ReasonItem> reasons = topReasons(percentiles, 2);
        String comparisonText = buildComparisonText(date, yearsBack, percentiles, reasons);

        Map<String, Object> meta = new HashMap<>();
        meta.put("date", date.toString());
        meta.put("timezone", timezone);
        meta.put("data_source", "open-meteo");
        meta.put("weights", WEIGHTS);
        meta.put("risk_score", round2(riskScore));
        meta.put("observed_raw", observed);

        List<SafetyRecommendResponse.DiagramMetric> diagramMetrics = List.of(
            buildMetric("Max gust", "km/h", observed.get("wind"), historical.get("wind")),
            buildMetric("Daily precip", "mm", observed.get("rain"), historical.get("rain")),
            buildMetric("Min apparent", "°C", observed.get("cold"), historical.get("cold")),
            buildMetric("Daily high", "°C", observed.get("high"), historical.get("high"))
        );

        return new SafetyRecommendResponse(
                level,
                round2(safetyScore),
                percentiles,
                yearsBack,
                reasons,
                comparisonText,
                meta,
                diagramMetrics
        );
    }

    private double nullSafe(Double value) {
        return value == null ? 0.0 : value;
    }

    private int percentile(double value, List<Double> historical) {
        if (historical == null || historical.isEmpty()) return 50;
        long count = historical.stream().filter(v -> v <= value).count();
        double pct = (count * 100.0) / historical.size();
        return (int) Math.round(clamp(pct, 0.0, 100.0));
    }

    private String levelFromScore(double safetyScore) {
        if (safetyScore >= RECOMMENDED_MIN) return "recommended";
        if (safetyScore >= CAUTION_MIN) return "caution";
        return "not_recommended";
    }

    private List<SafetyRecommendResponse.ReasonItem> topReasons(Map<String, Integer> percentiles, int k) {
        return percentiles.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(k)
                .map(e -> new SafetyRecommendResponse.ReasonItem(
                        e.getKey(),
                        labelForKey(e.getKey()),
                        e.getValue()
                ))
                .toList();
    }

    private String labelForKey(String key) {
        return switch (key) {
            case "wind" -> "High wind percentile";
            case "rain" -> "High precipitation percentile";
            case "cold" -> "Colder-than-usual percentile";
            default -> "Risk factor";
        };
    }

    private String buildComparisonText(LocalDate date, int yearsBack,
                                       Map<String, Integer> percentiles,
                                       List<SafetyRecommendResponse.ReasonItem> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "Compared with the past " + yearsBack + " years around " + date + ", conditions look typical.";
        }

        SafetyRecommendResponse.ReasonItem top = reasons.get(0);
        return "Compared with the past " + yearsBack + " years around " + date +
                ", " + top.label().toLowerCase() + " is elevated (percentile " + top.pct() + ").";
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private SafetyRecommendResponse.DiagramMetric buildMetric(String label, String unit, Double current, List<Double> history) {
        List<Double> all = new ArrayList<>(history);
        if (current != null) all.add(current);
        if (all.isEmpty()) {
            return new SafetyRecommendResponse.DiagramMetric(label, current != null ? current : 0.0, unit, 0, 0, 0, 0, 0);
        }
        all.sort(Double::compareTo);
        double min = all.get(0);
        double max = all.get(all.size() - 1);
        double q25 = percentileValue(all, 25);
        double median = percentileValue(all, 50);
        double q75 = percentileValue(all, 75);
        return new SafetyRecommendResponse.DiagramMetric(
                label,
                current != null ? round1(current) : 0.0,
                unit,
                round1(min), round1(max), round1(q25), round1(q75), round1(median)
        );
    }

    private double percentileValue(List<Double> sorted, double pct) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);
        double index = (pct / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        double weight = index - lower;
        return sorted.get(lower) * (1 - weight) + sorted.get(upper) * weight;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}