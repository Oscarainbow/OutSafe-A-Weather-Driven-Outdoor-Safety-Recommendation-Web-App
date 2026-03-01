package com.outsafe.backend.model;

import java.time.LocalDate;

public record SafetyRecommendRequest(
        double lat,
        double lon,
        Double elevation,
        LocalDate date,
        Integer years_back,
        String timezone
) {}