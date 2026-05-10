package com.example.hazedetector.server.model;

import java.time.Instant;

public record HourlyPoint(
    Instant time,
    int temperature,
    int humidity,
    int aqi
) {
}
