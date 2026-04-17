package com.outsafe.backend.model;

import java.util.List;
import java.util.Map;

public record SafetyRecommendResponse(
        String level,
        double score,
        Map<String, Integer> percentiles,
        int years_back,
        List<ReasonItem> reasons,
        String comparison_text,
        Map<String, Object> meta,
        List<DiagramMetric> diagram_metrics,
        List<ForceNotRecommendedFactor> force_not_recommended_factors
) {
    public record ReasonItem(String key, String label, int pct) {}

    /**
     * A hard threshold was exceeded; outdoor activity is forced to "not recommended" regardless of other signals.
     */
    public record ForceNotRecommendedFactor(
            String factor,
            String code,
            double observed,
            double threshold,
            String unit,
            String message
    ) {}

    public record DiagramMetric(String label, double value, String unit, double min, double max, double q25, double q75, double median) {}
}