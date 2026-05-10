package com.example.hazedetector;

import java.util.ArrayList;
import java.util.List;

final class WeatherData {
    String city;
    String source;
    String updatedAt;
    String weatherText;
    String level;
    String advice;
    int temperature;
    int apparentTemperature;
    int humidity;
    int windSpeed;
    int aqi;
    int pm25;
    int pm10;
    double co;
    int no2;
    int so2;
    int o3;
    final List<TrendPoint> points = new ArrayList<>();
    final List<ForecastDay> forecasts = new ArrayList<>();
    final List<HourlyForecast> hourlyForecasts = new ArrayList<>();
    final List<LifeIndex> indexes = new ArrayList<>();
}
