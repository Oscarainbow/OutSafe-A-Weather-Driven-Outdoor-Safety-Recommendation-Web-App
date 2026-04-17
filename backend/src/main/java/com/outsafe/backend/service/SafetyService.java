package com.outsafe.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Per-factor weights for combining into one weather risk score (sum = 1).
     * Each factor score = {@link #ABSOLUTE_COMFORT_WEIGHT} × absolute human stress (0–100)
     * + {@link #VS_HISTORY_WEIGHT} × percentile vs same calendar days in past years.
     */
    private static final Map<String, Double> WEIGHTS = Map.of(
            "wind", 0.28,
            "rain", 0.22,
            "cold", 0.28,
            "heat", 0.22
    );

    /** Prefer objective comfort / exposure; history is secondary. */
    private static final double ABSOLUTE_COMFORT_WEIGHT = 0.65;
    private static final double VS_HISTORY_WEIGHT = 0.35;

    private static final double RECOMMENDED_MIN = 70.0;
    private static final double CAUTION_MIN = 40.0;

    /**
     * Hard "force not recommended" lines (one per family). If triggered, appears in
     * {@code force_not_recommended_factors} and overall level becomes {@code not_recommended}.
     */
    private static final double WIND_FORCE_NOT_RECOMMENDED_KMH = 90.0;
    private static final double RAIN_FORCE_NOT_RECOMMENDED_MM = 80.0;
    private static final double HEAT_FORCE_NOT_RECOMMENDED_C = 40.0;
    private static final double COLD_FORCE_NOT_RECOMMENDED_C = -30.0;
    private static final double ELEV_FORCE_NOT_RECOMMENDED_M = 4800.0;

    private static final double FORCE_GATE_SAFETY_CAP = 12.0;

    /** Elevation (m): softer tiers (not in force list unless above). */
    private static final double ELEV_SEVERE_M = 4000.0;
    private static final double ELEV_SEVERE_CAP_SAFETY = 28.0;
    private static final double ELEV_MODERATE_M = 3000.0;
    private static final double ELEV_MODERATE_PENALTY = 14.0;
    private static final double ELEV_LIGHT_M = 2000.0;
    private static final double ELEV_LIGHT_PENALTY = 6.0;

    /** Absolute temperature soft tiers (°C), applied after weather-relative score. */
    private static final double TEMP_HIGH_HOT = 36.0;
    private static final double TEMP_HIGH_HOT_CAP = 42.0;
    private static final double TEMP_LOW_COLD = -25.0;
    private static final double TEMP_LOW_COLD_CAP = 38.0;

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
        observed.put("gust", nullSafe(observedDaily.windGustMax()));
        observed.put("rain", nullSafe(observedDaily.precipitationSum()));
        observed.put("cold", nullSafe(observedDaily.temperatureMin()));
        observed.put("apparent_min", nullSafe(observedDaily.apparentTemperatureMin()));
        observed.put("high", nullSafe(observedDaily.temperatureMax()));

        // 2) historical: same date in past N years
        Map<String, List<Double>> historical = new HashMap<>();
        historical.put("wind", new ArrayList<>());
        historical.put("rain", new ArrayList<>());
        historical.put("cold", new ArrayList<>());
        historical.put("heat", new ArrayList<>());

        for (int i = 1; i <= yearsBack; i++) {
            LocalDate pastDate = date.minusYears(i);
            try {
                ForecastDailyData hist = openMeteoService.getHistoricalDaily(lat, lon, pastDate, timezone);

                historical.get("wind").add(windRiskSample(hist));
                historical.get("rain").add(nullSafe(hist.precipitationSum()));
                historical.get("cold").add(nullSafe(hist.temperatureMin()));
                historical.get("heat").add(nullSafe(hist.temperatureMax()));
            } catch (Exception e) {
                // skip bad historical year
            }
        }

        // 3) Same calendar month, past N years: daily samples for the Today vs Past diagram (IQR / median).
        Map<String, List<Double>> monthHistory = new HashMap<>();
        monthHistory.put("gust", new ArrayList<>());
        monthHistory.put("rain", new ArrayList<>());
        monthHistory.put("apparent_min", new ArrayList<>());
        monthHistory.put("high", new ArrayList<>());

        for (int i = 1; i <= yearsBack; i++) {
            int pastYear = date.getYear() - i;
            LocalDate monthStart = LocalDate.of(pastYear, date.getMonthValue(), 1);
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            try {
                List<ForecastDailyData> days = openMeteoService.getHistoricalDailyRange(lat, lon, monthStart, monthEnd, timezone);
                for (ForecastDailyData d : days) {
                    if (d.windGustMax() != null) {
                        monthHistory.get("gust").add(d.windGustMax());
                    }
                    if (d.precipitationSum() != null) {
                        monthHistory.get("rain").add(d.precipitationSum());
                    }
                    if (d.apparentTemperatureMin() != null) {
                        monthHistory.get("apparent_min").add(d.apparentTemperatureMin());
                    }
                    if (d.temperatureMax() != null) {
                        monthHistory.get("high").add(d.temperatureMax());
                    }
                }
            } catch (Exception e) {
                // skip years with no archive
            }
        }

        double todayWindRisk = windRiskSample(observedDaily);
        observed.put("wind_risk_sample", todayWindRisk);

        Map<String, Double> observedRiskValue = new HashMap<>();
        observedRiskValue.put("wind", todayWindRisk);
        observedRiskValue.put("rain", observed.get("rain"));
        observedRiskValue.put("cold", -observed.get("cold"));
        observedRiskValue.put("heat", observed.get("high"));

        Map<String, List<Double>> historicalRiskValue = new HashMap<>();
        historicalRiskValue.put("wind", historical.get("wind"));
        historicalRiskValue.put("rain", historical.get("rain"));
        historicalRiskValue.put("cold", historical.get("cold").stream().map(v -> -v).toList());
        historicalRiskValue.put("heat", historical.get("heat"));

        Map<String, Integer> percentilesVsHistory = new HashMap<>();
        for (String key : WEIGHTS.keySet()) {
            double v = observedRiskValue.get(key);
            List<Double> hist = historicalRiskValue.get(key);
            percentilesVsHistory.put(key, percentile(v, hist));
        }

        double tMax = observed.get("high");
        Double apparentForCold = observedDaily.apparentTemperatureMin();
        double coldExposure = apparentForCold != null ? Math.min(observed.get("cold"), apparentForCold) : observed.get("cold");
        double rainMm = observed.get("rain");

        Map<String, Double> absoluteHumanStress = new HashMap<>();
        absoluteHumanStress.put("heat", absoluteHeatStressPct(tMax));
        absoluteHumanStress.put("cold", absoluteColdStressPct(coldExposure));
        absoluteHumanStress.put("wind", absoluteWindStressPct(todayWindRisk));
        absoluteHumanStress.put("rain", absoluteRainStressPct(rainMm));

        Map<String, Integer> percentiles = new HashMap<>();
        for (String key : WEIGHTS.keySet()) {
            double combined = ABSOLUTE_COMFORT_WEIGHT * absoluteHumanStress.get(key)
                    + VS_HISTORY_WEIGHT * percentilesVsHistory.get(key);
            percentiles.put(key, (int) Math.round(clamp(combined, 0.0, 100.0)));
        }

        double weatherPercentileRisk = 0.0;
        for (String key : WEIGHTS.keySet()) {
            weatherPercentileRisk += percentiles.get(key) * WEIGHTS.get(key);
        }

        // Safety points:0–100, higher = safer outdoors (before absolute gates).
        double safetyPoints = clamp(100.0 - weatherPercentileRisk, 0.0, 100.0);
        double weatherOnlySafety = safetyPoints;

        List<String> hardRules = new ArrayList<>();
        List<SafetyRecommendResponse.ForceNotRecommendedFactor> forceNotRecommended = new ArrayList<>();

        safetyPoints = applyAbsoluteTemperatureGates(safetyPoints, observedDaily, observed, hardRules, forceNotRecommended);
        safetyPoints = applyElevationGates(safetyPoints, request.elevation(), hardRules, forceNotRecommended);
        safetyPoints = applyWindAndRainForceGates(safetyPoints, todayWindRisk, observed.get("rain"), hardRules, forceNotRecommended);

        safetyPoints = clamp(safetyPoints, 0.0, 100.0);
        String level = levelFromScoreWithHardRules(safetyPoints, forceNotRecommended, hardRules);

        List<SafetyRecommendResponse.ReasonItem> reasons = buildReasons(percentiles, hardRules, request.elevation(), forceNotRecommended);
        String comparisonText = buildComparisonText(date, yearsBack, percentiles, reasons, forceNotRecommended);

        Map<String, Object> meta = new HashMap<>();
        meta.put("date", date.toString());
        meta.put("timezone", timezone);
        meta.put("data_source", "open-meteo");
        meta.put("scoring", "safety_points_higher_safer");
        meta.put("weights", WEIGHTS);
        meta.put("percentiles_vs_same_past_days", percentilesVsHistory);
        meta.put("absolute_human_stress_0_100", roundStressMap(absoluteHumanStress));
        meta.put("scoring_model", String.format(
                "per_factor_score = %.2f * absolute_human_stress + %.2f * percentile_vs_history; then weighted sum.",
                ABSOLUTE_COMFORT_WEIGHT, VS_HISTORY_WEIGHT));
        meta.put("weather_percentile_risk", round2(weatherPercentileRisk));
        meta.put("weather_safety_points", round2(weatherOnlySafety));
        meta.put("risk_score", round2(100.0 - safetyPoints));
        meta.put("hard_rules", hardRules);
        meta.put("force_not_recommended_factors", forceNotRecommended);
        if (request.elevation() != null) {
            meta.put("elevation_m", request.elevation());
        }
        meta.put("observed_raw", observed);

        Double todayGust = observedDaily.windGustMax() != null ? observedDaily.windGustMax() : observedDaily.windSpeedMax();
        Double todayApparentMin = observedDaily.apparentTemperatureMin() != null
                ? observedDaily.apparentTemperatureMin()
                : observedDaily.temperatureMin();

        List<SafetyRecommendResponse.DiagramMetric> diagramMetrics = List.of(
                buildMetric("Max gust", "km/h", todayGust, monthHistory.get("gust")),
                buildMetric("Daily precip", "mm", observedDaily.precipitationSum(), monthHistory.get("rain")),
                buildMetric("Min apparent", "°C", todayApparentMin, monthHistory.get("apparent_min")),
                buildMetric("Daily high", "°C", observedDaily.temperatureMax(), monthHistory.get("high"))
        );

        return new SafetyRecommendResponse(
                level,
                round2(safetyPoints),
                percentiles,
                yearsBack,
                reasons,
                comparisonText,
                meta,
                diagramMetrics,
                List.copyOf(forceNotRecommended)
        );
    }

    /** Daily high (°C): higher = more heat stress for typical outdoor activity. */
    private static double absoluteHeatStressPct(double tMax) {
        return segmentLinear(tMax,
                new double[]{10, 18, 24, 28, 32, 36, 40, 44},
                new double[]{5, 10, 18, 25, 38, 55, 78, 95});
    }

    /**
     * Cold exposure (°C): min of air min and apparent min — lower values = more cold stress.
     */
    private static double absoluteColdStressPct(double coldExposureC) {
        return segmentLinear(coldExposureC,
                new double[]{-35, -22, -12, -4, 2, 8, 15},
                new double[]{95, 78, 58, 42, 30, 18, 8});
    }

    /** Effective wind (km/h): stronger = more exposure / stability hazard. */
    private static double absoluteWindStressPct(double windKmh) {
        return segmentLinear(windKmh,
                new double[]{0, 15, 30, 45, 60, 75, 90},
                new double[]{5, 18, 35, 52, 68, 82, 95});
    }

    /** Daily precipitation sum (mm): more = wetter / visibility / footing risk. */
    private static double absoluteRainStressPct(double mm) {
        return segmentLinear(mm,
                new double[]{0, 2, 8, 20, 40, 70, 100},
                new double[]{4, 15, 32, 50, 68, 85, 96});
    }

    /**
     * Piecewise linear interpolation through (x[i], y[i]); clamps below first x to y[0], above last x to y[last].
     */
    private static double segmentLinear(double x, double[] xs, double[] ys) {
        if (xs.length == 0 || xs.length != ys.length) {
            return 50.0;
        }
        if (x <= xs[0]) {
            return ys[0];
        }
        if (x >= xs[xs.length - 1]) {
            return ys[ys.length - 1];
        }
        for (int i = 0; i < xs.length - 1; i++) {
            if (x >= xs[i] && x <= xs[i + 1]) {
                double t = (x - xs[i]) / (xs[i + 1] - xs[i]);
                return ys[i] + t * (ys[i + 1] - ys[i]);
            }
        }
        return ys[ys.length - 1];
    }

    private static Map<String, Double> roundStressMap(Map<String, Double> m) {
        Map<String, Double> out = new HashMap<>();
        for (var e : m.entrySet()) {
            double v = e.getValue();
            out.put(e.getKey(), Math.round(v * 100.0) / 100.0);
        }
        return out;
    }

    /** Effective wind exposure: max(gust, sustained), for risk comparison. */
    private static double windRiskSample(ForecastDailyData d) {
        double gust = d.windGustMax() != null ? d.windGustMax() : 0.0;
        double sustained = d.windSpeedMax() != null ? d.windSpeedMax() : 0.0;
        return Math.max(gust, sustained);
    }

    private double applyAbsoluteTemperatureGates(double safetyPoints, ForecastDailyData daily,
                                                   Map<String, Double> observed, List<String> hardRules,
                                                   List<SafetyRecommendResponse.ForceNotRecommendedFactor> forcedNr) {
        double tMax = observed.get("high");
        double tMin = observed.get("cold");
        Double apparent = daily.apparentTemperatureMin();
        double coldExposure = apparent != null ? Math.min(tMin, apparent) : tMin;

        double s = safetyPoints;
        if (tMax >= HEAT_FORCE_NOT_RECOMMENDED_C) {
            s = Math.min(s, FORCE_GATE_SAFETY_CAP);
            hardRules.add("extreme_heat");
            forcedNr.add(new SafetyRecommendResponse.ForceNotRecommendedFactor(
                    "heat",
                    "extreme_heat",
                    round1(tMax),
                    HEAT_FORCE_NOT_RECOMMENDED_C,
                    "°C",
                    String.format("Daily high %.1f °C meets or exceeds the extreme-heat limit (≥ %.0f °C) — not recommended for outdoor exertion.",
                            tMax, HEAT_FORCE_NOT_RECOMMENDED_C)
            ));
        } else if (tMax >= TEMP_HIGH_HOT) {
            s = Math.min(s, TEMP_HIGH_HOT_CAP);
            hardRules.add("high_heat");
        }

        if (coldExposure <= COLD_FORCE_NOT_RECOMMENDED_C) {
            s = Math.min(s, FORCE_GATE_SAFETY_CAP);
            hardRules.add("extreme_cold");
            forcedNr.add(new SafetyRecommendResponse.ForceNotRecommendedFactor(
                    "cold",
                    "extreme_cold",
                    round1(coldExposure),
                    COLD_FORCE_NOT_RECOMMENDED_C,
                    "°C",
                    String.format("Cold exposure %.1f °C meets or exceeds the extreme-cold limit (≤ %.0f °C) — not recommended.",
                            coldExposure, COLD_FORCE_NOT_RECOMMENDED_C)
            ));
        } else if (coldExposure <= TEMP_LOW_COLD) {
            s = Math.min(s, TEMP_LOW_COLD_CAP);
            hardRules.add("severe_cold");
        }
        return s;
    }

    private double applyElevationGates(double safetyPoints, Double elevationM, List<String> hardRules,
                                       List<SafetyRecommendResponse.ForceNotRecommendedFactor> forcedNr) {
        if (elevationM == null || elevationM.isNaN()) {
            return safetyPoints;
        }
        double elv = elevationM;
        double s = safetyPoints;
        if (elv >= ELEV_FORCE_NOT_RECOMMENDED_M) {
            s = Math.min(s, FORCE_GATE_SAFETY_CAP);
            hardRules.add("extreme_elevation");
            forcedNr.add(new SafetyRecommendResponse.ForceNotRecommendedFactor(
                    "elevation",
                    "extreme_elevation",
                    round1(elv),
                    ELEV_FORCE_NOT_RECOMMENDED_M,
                    "m",
                    String.format("Elevation %.0f m meets or exceeds the extreme-altitude limit (≥ %.0f m) — not recommended for casual outdoor activity.",
                            elv, ELEV_FORCE_NOT_RECOMMENDED_M)
            ));
        } else if (elv >= ELEV_SEVERE_M) {
            s = Math.min(s, ELEV_SEVERE_CAP_SAFETY);
            hardRules.add("severe_elevation");
        } else {
            if (elv >= ELEV_MODERATE_M) {
                s -= ELEV_MODERATE_PENALTY;
                hardRules.add("moderate_elevation");
            } else if (elv >= ELEV_LIGHT_M) {
                s -= ELEV_LIGHT_PENALTY;
                hardRules.add("light_elevation");
            }
        }
        return s;
    }

    private double applyWindAndRainForceGates(double safetyPoints, double windRiskKmh, double rainMm,
                                              List<String> hardRules,
                                              List<SafetyRecommendResponse.ForceNotRecommendedFactor> forcedNr) {
        double s = safetyPoints;
        if (windRiskKmh >= WIND_FORCE_NOT_RECOMMENDED_KMH) {
            s = Math.min(s, FORCE_GATE_SAFETY_CAP);
            hardRules.add("extreme_wind");
            forcedNr.add(new SafetyRecommendResponse.ForceNotRecommendedFactor(
                    "wind",
                    "extreme_wind",
                    round1(windRiskKmh),
                    WIND_FORCE_NOT_RECOMMENDED_KMH,
                    "km/h",
                    String.format("Wind / gust effective speed %.1f km/h meets or exceeds the limit (≥ %.0f km/h) — not recommended in exposed terrain.",
                            windRiskKmh, WIND_FORCE_NOT_RECOMMENDED_KMH)
            ));
        }
        if (rainMm >= RAIN_FORCE_NOT_RECOMMENDED_MM) {
            s = Math.min(s, FORCE_GATE_SAFETY_CAP);
            hardRules.add("extreme_rain");
            forcedNr.add(new SafetyRecommendResponse.ForceNotRecommendedFactor(
                    "rain",
                    "extreme_rain",
                    round1(rainMm),
                    RAIN_FORCE_NOT_RECOMMENDED_MM,
                    "mm",
                    String.format("Daily precipitation %.1f mm meets or exceeds the limit (≥ %.0f mm) — not recommended (flood / exposure risk).",
                            rainMm, RAIN_FORCE_NOT_RECOMMENDED_MM)
            ));
        }
        return s;
    }

    private String levelFromScoreWithHardRules(double safetyPoints,
                                               List<SafetyRecommendResponse.ForceNotRecommendedFactor> forcedNr,
                                               List<String> hardRules) {
        if (!forcedNr.isEmpty()) {
            return "not_recommended";
        }
        if (hardRules.contains("severe_elevation") && safetyPoints < RECOMMENDED_MIN) {
            return safetyPoints >= CAUTION_MIN ? "caution" : "not_recommended";
        }
        return levelFromScore(safetyPoints);
    }

    private List<SafetyRecommendResponse.ReasonItem> buildReasons(Map<String, Integer> percentiles,
                                                                 List<String> hardRules,
                                                                 Double elevationM,
                                                                 List<SafetyRecommendResponse.ForceNotRecommendedFactor> forcedNr) {
        List<SafetyRecommendResponse.ReasonItem> out = new ArrayList<>();
        for (SafetyRecommendResponse.ForceNotRecommendedFactor f : forcedNr) {
            out.add(new SafetyRecommendResponse.ReasonItem(f.factor(), f.message(), 100));
        }
        if (!hasForcedFactor(forcedNr, "elevation")) {
            if (hardRules.contains("severe_elevation")) {
                String detail = elevationM != null
                        ? String.format("Very high elevation (~%.0f m) — high physiological risk for most people.", elevationM)
                        : "Very high elevation — high physiological risk for most people.";
                out.add(new SafetyRecommendResponse.ReasonItem("elevation", detail, 95));
            } else if (hardRules.contains("moderate_elevation")) {
                out.add(new SafetyRecommendResponse.ReasonItem("elevation", "Moderate altitude — expect reduced performance and weather volatility.", 70));
            }
        }
        if (!hasForcedFactor(forcedNr, "heat") && hardRules.contains("high_heat")) {
            out.add(new SafetyRecommendResponse.ReasonItem("heat", "High heat — use caution, hydration, and shade.", 75));
        }
        if (!hasForcedFactor(forcedNr, "cold") && hardRules.contains("severe_cold")) {
            out.add(new SafetyRecommendResponse.ReasonItem("cold", "Severe cold — limit exposure and dress for the worst case.", 80));
        }
        out.addAll(topReasons(percentiles, 2));
        return out.stream().limit(8).toList();
    }

    private static boolean hasForcedFactor(List<SafetyRecommendResponse.ForceNotRecommendedFactor> forcedNr, String factor) {
        return forcedNr.stream().anyMatch(f -> factor.equals(f.factor()));
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
            case "wind" -> "Wind risk (comfort + vs past)";
            case "rain" -> "Rain / wet exposure (comfort + vs past)";
            case "cold" -> "Cold exposure (comfort + vs past)";
            case "heat" -> "Heat exposure (comfort + vs past)";
            default -> "Risk factor";
        };
    }

    private String buildComparisonText(LocalDate date, int yearsBack,
                                       Map<String, Integer> percentiles,
                                       List<SafetyRecommendResponse.ReasonItem> reasons,
                                       List<SafetyRecommendResponse.ForceNotRecommendedFactor> forcedNr) {
        String base;
        if (!forcedNr.isEmpty()) {
            base = forcedNr.get(0).message();
            if (forcedNr.size() > 1) {
                base += String.format(" (+%d other hard limit(s) also apply.)", forcedNr.size() - 1);
            }
        } else if (reasons == null || reasons.isEmpty()) {
            base = "Compared with the past " + yearsBack + " years around " + date + ", conditions look typical.";
        } else {
            SafetyRecommendResponse.ReasonItem top = reasons.get(0);
            boolean gateFirst = Set.of("elevation", "heat", "wind", "rain").contains(top.key())
                    || ("cold".equals(top.key()) && top.label().toLowerCase().contains("extreme"));
            if (gateFirst) {
                base = top.label();
            } else {
                base = "Compared with the past " + yearsBack + " years around " + date +
                        ", " + top.label().toLowerCase() + " is elevated (percentile " + top.pct() + ").";
            }
        }
        return base;
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * Diagram track: IQR and median are computed from historical samples only (same month, past years).
     * Axis min/max include today's value so the marker stays visible.
     */
    private SafetyRecommendResponse.DiagramMetric buildMetric(String label, String unit, Double current, List<Double> historyRaw) {
        List<Double> h = new ArrayList<>();
        if (historyRaw != null) {
            for (Double v : historyRaw) {
                if (v != null) {
                    h.add(v);
                }
            }
        }
        h.sort(Double::compareTo);

        double cur = current != null ? current : 0.0;

        double q25;
        double median;
        double q75;
        if (h.isEmpty()) {
            q25 = median = q75 = cur;
        } else {
            q25 = percentileValue(h, 25);
            median = percentileValue(h, 50);
            q75 = percentileValue(h, 75);
        }

        double minV;
        double maxV;
        if (h.isEmpty()) {
            minV = maxV = cur;
        } else {
            minV = h.get(0);
            maxV = h.get(h.size() - 1);
        }
        if (current != null) {
            minV = Math.min(minV, current);
            maxV = Math.max(maxV, current);
        }

        if (Math.abs(maxV - minV) < 1e-9) {
            double pad = switch (unit) {
                case "km/h" -> 1.0;
                case "mm" -> 0.1;
                default -> 0.5;
            };
            minV -= pad;
            maxV += pad;
        }

        return new SafetyRecommendResponse.DiagramMetric(
                label,
                round1(cur),
                unit,
                round1(minV), round1(maxV), round1(q25), round1(q75), round1(median)
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