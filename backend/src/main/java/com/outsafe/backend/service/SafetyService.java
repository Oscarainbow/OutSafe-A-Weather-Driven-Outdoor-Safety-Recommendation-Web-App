package com.outsafe.backend.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.outsafe.backend.model.SafetyRecommendRequest;
import com.outsafe.backend.model.SafetyRecommendResponse;

/**
 * Service layer responsible for computing safety recommendations.
 * Core idea:
 * 1) For each risk factor, compute percentile vs. historical distribution.
 * 2) Convert percentiles into a weighted risk score (0-100).
 * 3) safetyScore = 100 - riskScore
 * 4) Map safetyScore to level: recommended / caution / not_recommended
 * 5) Provide top contributing factors as reasons.
 */
@Service
public class SafetyService {

    // Tunable weights (sum to 1.0)
    private static final Map<String, Double> WEIGHTS = Map.of(
            "wind", 0.40,
            "rain", 0.30,
            "cold", 0.30
    );

    // Score thresholds (simple + stable)
    private static final double RECOMMENDED_MIN = 70.0;
    private static final double CAUTION_MIN = 40.0;

    public SafetyRecommendResponse recommend(SafetyRecommendRequest request) {

        // Defaults
        LocalDate date = (request.date() == null) ? LocalDate.now() : request.date();
        int yearsBack = (request.years_back() == null || request.years_back() <= 0) ? 5 : request.years_back();
        String timezone = (request.timezone() == null || request.timezone().isBlank()) ? "auto" : request.timezone();

        // ------------------------------------------------------------
        // TODO (later): Replace the following "observed" and "historical" mock data
        // with:
        //  - Open-Meteo API fetch (current/forecast/historical)
        //  - or DB/Timescale query
        // ------------------------------------------------------------

        // Mock observed values for the selected date (units are just placeholders)
        // You can later compute these from Open-Meteo daily/hourly fields.
        Map<String, Double> observed = Map.of(
                "wind", 12.5,   // e.g., wind speed (m/s)
                "rain",  6.0,   // e.g., precipitation (mm)
                "cold", -3.0    // e.g., temperature (°C) where "lower = colder"
        );

        // Mock historical distributions for the same calendar day over past yearsBack years
        // Later, these lists should be built from real historical series.
        Map<String, List<Double>> historical = Map.of(
                "wind", List.of(3.2, 4.1, 5.0, 6.8, 7.3, 8.0, 9.5, 10.2, 11.0, 12.0, 13.7),
                "rain", List.of(0.0, 0.0, 0.3, 0.8, 1.2, 2.0, 3.1, 4.5, 5.2, 7.0, 9.4),
                // Note: for "cold", more negative means colder => higher risk.
                // We want percentile to represent "how cold (risky) it is".
                // So we compute percentile on (-temperature) instead of temperature.
                "cold", List.of(-10.0, -8.0, -6.0, -5.0, -3.0, -2.0, 0.0, 1.0, 2.0, 3.0)
        );

        // Convert observed values into "risk-direction" comparable values.
        // wind: higher wind => higher risk (use as-is)
        // rain: higher rain => higher risk (use as-is)
        // cold: colder => higher risk, so use (-temperature) as "coldness"
        Map<String, Double> observedRiskValue = new HashMap<>();
        observedRiskValue.put("wind", observed.get("wind"));
        observedRiskValue.put("rain", observed.get("rain"));
        observedRiskValue.put("cold", -observed.get("cold")); // e.g., -(-3) = 3 coldness

        Map<String, List<Double>> historicalRiskValue = new HashMap<>();
        historicalRiskValue.put("wind", historical.get("wind"));
        historicalRiskValue.put("rain", historical.get("rain"));
        historicalRiskValue.put("cold", historical.get("cold").stream().map(v -> -v).collect(Collectors.toList()));

        // 1) Compute percentiles (0–100)
        Map<String, Integer> percentiles = new HashMap<>();
        for (String key : WEIGHTS.keySet()) {
            double v = observedRiskValue.get(key);
            List<Double> hist = historicalRiskValue.get(key);
            int pct = percentile(v, hist);
            percentiles.put(key, pct);
        }

        // 2) Compute weighted risk score (0–100), then safety score (0–100)
        double riskScore = 0.0;
        for (String key : WEIGHTS.keySet()) {
            riskScore += percentiles.get(key) * WEIGHTS.get(key);
        }
        double safetyScore = clamp(100.0 - riskScore, 0.0, 100.0);

        // 3) Level by safety score
        String level = levelFromScore(safetyScore);

        // 4) Reasons: top 2 highest percentiles (most unusual / most risky)
        List<SafetyRecommendResponse.ReasonItem> reasons = topReasons(percentiles, 2);

        // 5) Human-readable summary
        String comparisonText = buildComparisonText(date, yearsBack, percentiles, reasons);

        // 6) Meta for transparency/debug
        Map<String, Object> meta = new HashMap<>();
        meta.put("date", date.toString());
        meta.put("timezone", timezone);
        meta.put("data_source", "mock");
        meta.put("weights", WEIGHTS);
        meta.put("risk_score", round2(riskScore));
        meta.put("observed_raw", observed);

        return new SafetyRecommendResponse(
                level,
                round2(safetyScore),
                percentiles,
                yearsBack,
                reasons,
                comparisonText,
                meta
        );
    }

    /**
     * Percentile rank: percentage of historical values <= value.
     * Returns int in [0, 100].
     */
    private int percentile(double value, List<Double> historical) {
        if (historical == null || historical.isEmpty()) return 50; // neutral fallback
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
}