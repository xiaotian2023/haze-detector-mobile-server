package com.example.hazedetector.server.model;

public record AirQuality(
    int aqi,
    String level,
    String color,
    String advice,
    int pm25,
    int pm10,
    double co,
    int no2,
    int so2,
    int o3
) {
}
