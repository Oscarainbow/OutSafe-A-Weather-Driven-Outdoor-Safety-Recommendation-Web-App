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
        List<DiagramMetric> diagram_metrics
) {
    public record ReasonItem(String key, String label, int pct) {}
    public record DiagramMetric(String label, double value, String unit, double min, double max, double q25, double q75, double median) {}
}