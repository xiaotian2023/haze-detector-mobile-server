package com.example.hazedetector.server.service;

import com.example.hazedetector.server.model.AirQuality;
import com.example.hazedetector.server.model.AqiLevel;
import com.example.hazedetector.server.model.DailyForecast;
import com.example.hazedetector.server.model.HourlyForecast;
import com.example.hazedetector.server.model.HourlyPoint;
import com.example.hazedetector.server.model.LifeIndex;
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
        List<HourlyForecast> hourlyForecasts = hourlyForecasts(hour, temperature, humidity);

        WeatherResponse response = new WeatherResponse(
            city,
            Instant.now(),
            "Spring Boot 服务端模拟数据",
            new WeatherInfo(humidity > 78 && pm25 > 90 ? "雾霾" : "多云", temperature, temperature + 1, humidity, 6 + base % 15),
            new AirQuality(aqi, level.name(), level.color(), level.advice(), pm25, pm10, round1(0.4 + (base % 8) / 10.0), 18 + base % 42, 5 + base % 18, 60 + base % 80),
            hourlyFromForecasts(hourlyForecasts, aqi),
            forecasts(base, temperature),
            hourlyForecasts,
            indexes()
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

    private List<HourlyPoint> hourlyFromForecasts(List<HourlyForecast> forecasts, int aqi) {
        List<HourlyPoint> points = new ArrayList<>();
        for (HourlyForecast forecast : forecasts) {
            points.add(new HourlyPoint(forecast.time(), forecast.temperature(), forecast.humidity(), aqi));
        }
        return points;
    }

    private List<HourlyForecast> hourlyForecasts(int hour, int temperature, int humidity) {
        List<HourlyForecast> hours = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String[] texts = {"多云", "晴", "阴", "小雨"};
        for (int i = 0; i < 24; i++) {
            LocalDateTime time = now.plusHours(i).withMinute(0).withSecond(0).withNano(0);
            int h = (hour + i) % 24;
            int temp = Math.round((float) (temperature + Math.sin(h / 24.0 * Math.PI * 2) * 4));
            int hum = Math.max(30, Math.min(95, Math.round((float) (humidity + Math.cos(h / 24.0 * Math.PI * 2) * 8))));
            hours.add(new HourlyForecast(
                time.atZone(ZoneId.systemDefault()).toInstant(),
                texts[Math.floorMod(i, texts.length)],
                temp,
                hum,
                "东北风",
                "1-3级",
                i % 7 == 0 ? 0.2 : 0
            ));
        }
        return hours;
    }

    private List<DailyForecast> forecasts(int base, int temperature) {
        List<DailyForecast> days = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String[] texts = {"晴", "多云", "阴", "小雨", "霾"};
        String[] weeks = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < 7; i++) {
            LocalDateTime date = now.plusDays(i);
            int high = temperature + 2 + Math.round((float) Math.sin((base + i) / 3.0) * 4);
            int low = high - 5 - Math.abs((base + i) % 4);
            String dayText = texts[Math.floorMod(base + i, texts.length)];
            String nightText = texts[Math.floorMod(base + i + 1, texts.length)];
            days.add(new DailyForecast(
                String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth()),
                i == 0 ? "今天" : weeks[date.getDayOfWeek().getValue() - 1],
                dayText,
                nightText,
                high,
                low,
                "东北风",
                "1-3级"
            ));
        }
        return days;
    }

    private List<LifeIndex> indexes() {
        return List.of(
            new LifeIndex("穿衣指数", "舒适", "适合穿薄外套、长袖衬衫等春秋服装。"),
            new LifeIndex("运动指数", "较适宜", "空气质量尚可，适合适量户外运动。"),
            new LifeIndex("紫外线指数", "中等", "外出可适当涂抹防晒用品。"),
            new LifeIndex("洗车指数", "适宜", "近期降水概率较低，适合洗车。"),
            new LifeIndex("感冒指数", "少发", "昼夜温差不大，感冒概率较低。"),
            new LifeIndex("晨练指数", "适宜", "清晨空气较好，适合晨练。")
        );
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
