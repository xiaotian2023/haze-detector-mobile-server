package com.example.hazedetector;

final class HourlyForecast {
    final String time;
    final String text;
    final int temperature;
    final int humidity;
    final String windDirection;
    final String windClass;
    final double precipitation;

    HourlyForecast(String time, String text, int temperature, int humidity, String windDirection, String windClass, double precipitation) {
        this.time = time;
        this.text = text;
        this.temperature = temperature;
        this.humidity = humidity;
        this.windDirection = windDirection;
        this.windClass = windClass;
        this.precipitation = precipitation;
    }
}
