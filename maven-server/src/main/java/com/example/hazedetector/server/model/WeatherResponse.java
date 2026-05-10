package com.example.hazedetector.server.model;

import java.time.Instant;
import java.util.List;

public record WeatherResponse(
    String city,
    Instant updatedAt,
    String source,
    WeatherInfo weather,
    AirQuality air,
    List<HourlyPoint> hourly
) {
}
