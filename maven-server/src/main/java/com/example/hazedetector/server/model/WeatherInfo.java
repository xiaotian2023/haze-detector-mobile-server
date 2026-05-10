package com.example.hazedetector.server.model;

public record WeatherInfo(
    String text,
    int temperature,
    int apparentTemperature,
    int humidity,
    int windSpeed
) {
}
