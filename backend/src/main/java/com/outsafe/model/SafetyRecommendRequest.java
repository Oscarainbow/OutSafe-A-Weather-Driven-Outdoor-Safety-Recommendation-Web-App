package com.outsafe.backend.model;

import java.time.LocalDate;

public record SafetyRecommendRequest(
        Double latitude,
        Double longitude,
        Double elevation,
        LocalDate date,
        Integer years_back,
        String timezone
) {}