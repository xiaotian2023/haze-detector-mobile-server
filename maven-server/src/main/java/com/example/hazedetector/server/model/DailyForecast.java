package com.example.hazedetector.server.model;

public record DailyForecast(
    String date,
    String week,
    String dayText,
    String nightText,
    int high,
    int low,
    String windDirection,
    String windClass
) {
}
