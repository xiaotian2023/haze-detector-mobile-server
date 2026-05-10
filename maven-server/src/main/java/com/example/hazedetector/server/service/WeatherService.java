package com.example.hazedetector.server.service;

import com.example.hazedetector.server.model.AirQuality;
import com.example.hazedetector.server.model.AqiLevel;
import com.example.hazedetector.server.model.HourlyPoint;
import com.example.hazedetector.server.model.WeatherInfo;
import com.example.hazedetector.server.model.WeatherResponse;
import com.example.hazedetector.server.repository.CityRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeatherService {
    private static final Path LAST_WEATHER_FILE = Path.of("data", "last-weather.txt");
    private final BaiduWeatherService baiduWeatherService;

    public WeatherService(BaiduWeatherService baiduWeatherService) {
        this.baiduWeatherService = baiduWeatherService;
    }

    public WeatherResponse getWeather(String rawCity, String districtId) {
        String city = CityRepository.cleanCity(rawCity);
        if (city.isEmpty()) city = "北京";

        WeatherResponse realWeather = baiduWeatherService.getWeather(city, districtId).orElse(null);
        if (realWeather != null) {
            saveLastWeather(realWeather);
            return realWeather;
        }

        int base = city.chars().sum();
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int temperature = Math.round((float) (16 + base % 12 + Math.sin(hour / 24.0 * Math.PI * 2) * 5));
        int humidity = Math.max(35, Math.min(92, Math.round((float) (58 + base % 20 + Math.cos(hour / 24.0 * Math.PI * 2) * 12))));
        int pm25 = Math.max(12, Math.round(30 + base % 75 + humidity * 0.35f));
        int pm10 = Math.round(pm25 * 1.45f);
        int aqi = Math.max(20, Math.min(260, Math.round(pm25 * 1.6f)));
        AqiLevel level = AqiLevel.from(aqi);

        WeatherResponse response = new WeatherResponse(
            city,
            Instant.now(),
            "Spring Boot 服务端模拟数据",
            new WeatherInfo(humidity > 78 && pm25 > 90 ? "雾霾" : "多云", temperature, temperature + 1, humidity, 6 + base % 15),
            new AirQuality(aqi, level.name(), level.color(), level.advice(), pm25, pm10, round1(0.4 + (base % 8) / 10.0), 18 + base % 42, 5 + base % 18, 60 + base % 80),
            hourly(base, hour, temperature, humidity, aqi)
        );
        saveLastWeather(response);
        return response;
    }

    private List<HourlyPoint> hourly(int base, int hour, int temperature, int humidity, int aqi) {
        List<HourlyPoint> points = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            int h = (hour - i + 24) % 24;
            int temp = Math.round((float) (temperature + Math.sin(h / 24.0 * Math.PI * 2) * 4));
            int hum = Math.max(30, Math.min(95, Math.round((float) (humidity + Math.cos(h / 24.0 * Math.PI * 2) * 8))));
            int hourAqi = Math.max(20, Math.min(300, Math.round((float) (aqi + Math.sin((h + base) / 24.0 * Math.PI * 2) * 25))));
            Instant time = LocalDateTime.now().withHour(h).withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant();
            points.add(new HourlyPoint(time, temp, hum, hourAqi));
        }
        return points;
    }

    private void saveLastWeather(WeatherResponse response) {
        try {
            Files.createDirectories(LAST_WEATHER_FILE.getParent());
            Files.writeString(LAST_WEATHER_FILE, response.toString());
        } catch (IOException error) {
            throw new IllegalStateException("保存天气数据失败", error);
        }
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
