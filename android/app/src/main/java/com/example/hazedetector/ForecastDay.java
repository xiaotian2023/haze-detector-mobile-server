package com.example.hazedetector;

final class ForecastDay {
    final String date;
    final String week;
    final String dayText;
    final String nightText;
    final int high;
    final int low;
    final String windDirection;
    final String windClass;

    ForecastDay(String date, String week, String dayText, String nightText, int high, int low, String windDirection, String windClass) {
        this.date = date;
        this.week = week;
        this.dayText = dayText;
        this.nightText = nightText;
        this.high = high;
        this.low = low;
        this.windDirection = windDirection;
        this.windClass = windClass;
    }
}
