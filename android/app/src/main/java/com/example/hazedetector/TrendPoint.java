package com.example.hazedetector;

final class TrendPoint {
    final String time;
    final int temperature;
    final int humidity;
    final int aqi;

    TrendPoint(String time, int temperature, int humidity, int aqi) {
        this.time = time;
        this.temperature = temperature;
        this.humidity = humidity;
        this.aqi = aqi;
    }
}
