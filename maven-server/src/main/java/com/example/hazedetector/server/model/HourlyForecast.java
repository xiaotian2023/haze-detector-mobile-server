package com.example.hazedetector.server.model;

import java.time.Instant;

public record HourlyForecast(
    Instant time,
    String text,
    int temperature,
    int humidity,
    String windDirection,
    String windClass,
    double precipitation
) {
}
