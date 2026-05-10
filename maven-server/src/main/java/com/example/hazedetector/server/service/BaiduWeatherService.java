package com.example.hazedetector.server.service;

import com.example.hazedetector.server.model.AirQuality;
import com.example.hazedetector.server.model.AqiLevel;
import com.example.hazedetector.server.model.DailyForecast;
import com.example.hazedetector.server.model.HourlyForecast;
import com.example.hazedetector.server.model.HourlyPoint;
import com.example.hazedetector.server.model.LifeIndex;
import com.example.hazedetector.server.model.WeatherInfo;
import com.example.hazedetector.server.model.WeatherResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BaiduWeatherService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaiduWeatherService.class);
    private static final String WEATHER_URL = "https://api.map.baidu.com/weather/v1/";
    private static final String WEATHER_PATH = "/weather/v1/";

    private final String ak;
    private final String sk;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public BaiduWeatherService(
        @Value("${baidu.map.ak:}") String ak,
        @Value("${baidu.map.sk:}") String sk,
        ObjectMapper objectMapper,
        RestTemplateBuilder restTemplateBuilder
    ) {
        this.ak = EnvFile.get(ak, "BAIDU_MAP_AK", "ak");
        this.sk = EnvFile.get(sk, "BAIDU_MAP_SK", "sk");
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
    }

    public Optional<WeatherResponse> getWeather(String city, String districtId) {
        if (ak.isBlank() || districtId == null || districtId.isBlank()) {
            return Optional.empty();
        }

        try {
            String responseBody = restTemplate.getForObject(URI.create(buildWeatherUrl(districtId)), String.class);
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || root.path("status").asInt(-1) != 0) {
                LOGGER.warn("百度天气接口返回异常，districtId={}，response={}", districtId, root);
                return Optional.empty();
            }

            JsonNode result = root.path("result");
            JsonNode now = result.path("now");
            int temperature = parseInt(now.path("temp").asText(), 0);
            int humidity = parseInt(now.path("rh").asText(), 0);
            AirQuality air = parseAir(now).orElseGet(() -> simulatedAir(city, humidity));
            List<HourlyForecast> hourlyForecasts = hourlyForecasts(result.path("forecast_hours"), temperature, humidity);

            WeatherResponse response = new WeatherResponse(
                cityFromResult(result, city),
                Instant.now(),
                "百度天气",
                new WeatherInfo(
                    firstNonBlank(now.path("text").asText(), "--"),
                    temperature,
                    parseInt(firstNonBlank(now.path("feels_like").asText(), now.path("feelsLike").asText()), temperature),
                    humidity,
                    windSpeed(now.path("wind_class").asText())
                ),
                air,
                hourlyPoints(hourlyForecasts, temperature, humidity, air.aqi()),
                forecasts(result.path("forecasts"), city, temperature),
                hourlyForecasts,
                indexes(result.path("indexes"))
            );
            return Optional.of(response);
        } catch (Exception error) {
            LOGGER.warn("百度天气接口请求失败，districtId={}", districtId, error);
            return Optional.empty();
        }
    }

    private Optional<AirQuality> parseAir(JsonNode now) {
        int aqi = parseInt(now.path("aqi").asText(), 0);
        if (aqi <= 0) {
            return Optional.empty();
        }

        AqiLevel level = AqiLevel.from(aqi);
        return Optional.of(new AirQuality(
            aqi,
            firstNonBlank(now.path("aqi_level").asText(), level.name()),
            level.color(),
            level.advice(),
            parseInt(firstNonBlank(now.path("pm25").asText(), now.path("pm2_5").asText()), 0),
            parseInt(now.path("pm10").asText(), 0),
            round1(parseDouble(now.path("co").asText(), 0)),
            parseInt(now.path("no2").asText(), 0),
            parseInt(now.path("so2").asText(), 0),
            parseInt(now.path("o3").asText(), 0)
        ));
    }

    private List<DailyForecast> forecasts(JsonNode forecastNodes, String city, int currentTemperature) {
        List<DailyForecast> days = new ArrayList<>();
        if (forecastNodes != null && forecastNodes.isArray()) {
            for (JsonNode item : forecastNodes) {
                if (days.size() >= 7) {
                    break;
                }
                String dayText = firstNonBlank(
                    item.path("text_day").asText(),
                    item.path("textDay").asText(),
                    item.path("text").asText(),
                    "--"
                );
                String nightText = firstNonBlank(
                    item.path("text_night").asText(),
                    item.path("textNight").asText(),
                    dayText
                );
                days.add(new DailyForecast(
                    firstNonBlank(item.path("date").asText(), "--"),
                    firstNonBlank(item.path("week").asText(), ""),
                    dayText,
                    nightText,
                    parseInt(firstNonBlank(item.path("high").asText(), item.path("temp_high").asText()), currentTemperature + 3),
                    parseInt(firstNonBlank(item.path("low").asText(), item.path("temp_low").asText()), currentTemperature - 3),
                    firstNonBlank(item.path("wd_day").asText(), item.path("wd_night").asText(), item.path("wind_dir").asText(), "--"),
                    firstNonBlank(item.path("wc_day").asText(), item.path("wc_night").asText(), item.path("wind_class").asText(), "--")
                ));
            }
        }
        return days.isEmpty() ? simulatedForecasts(city, currentTemperature) : days;
    }

    private List<HourlyForecast> hourlyForecasts(JsonNode forecastNodes, int currentTemperature, int currentHumidity) {
        List<HourlyForecast> hours = new ArrayList<>();
        if (forecastNodes != null && forecastNodes.isArray()) {
            for (JsonNode item : forecastNodes) {
                if (hours.size() >= 24) {
                    break;
                }
                Instant time = parseBaiduTime(item.path("data_time").asText(""));
                hours.add(new HourlyForecast(
                    time,
                    firstNonBlank(item.path("text").asText(), "--"),
                    parseInt(item.path("temp_fc").asText(), currentTemperature),
                    parseInt(item.path("rh").asText(), currentHumidity),
                    firstNonBlank(item.path("wind_dir").asText(), "--"),
                    firstNonBlank(item.path("wind_class").asText(), "--"),
                    round1(parseDouble(item.path("prec_1h").asText(), 0))
                ));
            }
        }
        return hours.isEmpty() ? simulatedHourlyForecasts(currentTemperature, currentHumidity) : hours;
    }

    private List<HourlyPoint> hourlyPoints(List<HourlyForecast> forecasts, int temperature, int humidity, int aqi) {
        if (forecasts == null || forecasts.isEmpty()) {
            return hourly(temperature, humidity, aqi);
        }
        List<HourlyPoint> points = new ArrayList<>();
        for (HourlyForecast forecast : forecasts) {
            points.add(new HourlyPoint(forecast.time(), forecast.temperature(), forecast.humidity(), aqi));
        }
        return points;
    }

    private List<LifeIndex> indexes(JsonNode indexNodes) {
        List<LifeIndex> items = new ArrayList<>();
        if (indexNodes != null && indexNodes.isArray()) {
            for (JsonNode item : indexNodes) {
                if (items.size() >= 8) {
                    break;
                }
                items.add(new LifeIndex(
                    firstNonBlank(item.path("name").asText(), "--"),
                    firstNonBlank(item.path("brief").asText(), "--"),
                    firstNonBlank(item.path("detail").asText(), "")
                ));
            }
        }
        return items.isEmpty() ? simulatedIndexes() : items;
    }

    private String buildWeatherUrl(String districtId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("district_id", districtId);
        params.put("data_type", "all");
        params.put("ak", ak);

        String queryString = toQueryString(params);
        if (!sk.isBlank()) {
            queryString += "&sn=" + calculateSn(WEATHER_PATH, queryString);
        }
        return WEATHER_URL + "?" + queryString;
    }

    private String calculateSn(String path, String queryString) {
        String wholeStr = path + "?" + queryString + sk;
        String encoded = URLEncoder.encode(wholeStr, StandardCharsets.UTF_8);
        return md5(encoded);
    }

    private String toQueryString(Map<String, String> params) {
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (queryString.length() > 0) {
                queryString.append("&");
            }
            queryString.append(entry.getKey())
                .append("=")
                .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return queryString.toString();
    }

    private String cityFromResult(JsonNode result, String fallbackCity) {
        if (fallbackCity != null && !fallbackCity.isBlank()) {
            return fallbackCity;
        }

        JsonNode address = result.path("address");
        JsonNode location = result.path("location");
        return firstNonBlank(
            address.path("city").asText(),
            address.path("name").asText(),
            location.path("city").asText(),
            location.path("name").asText(),
            fallbackCity
        );
    }

    private AirQuality simulatedAir(String city, int humidity) {
        int base = city == null ? 0 : city.chars().sum();
        int pm25 = Math.max(12, Math.round(30 + base % 75 + humidity * 0.35f));
        int pm10 = Math.round(pm25 * 1.45f);
        int aqi = Math.max(20, Math.min(260, Math.round(pm25 * 1.6f)));
        AqiLevel level = AqiLevel.from(aqi);
        return new AirQuality(aqi, level.name(), level.color(), level.advice(), pm25, pm10, round1(0.4 + (base % 8) / 10.0), 18 + base % 42, 5 + base % 18, 60 + base % 80);
    }

    private List<HourlyPoint> hourly(int temperature, int humidity, int aqi) {
        List<HourlyPoint> points = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        for (int i = 11; i >= 0; i--) {
            int h = (hour - i + 24) % 24;
            int temp = Math.round((float) (temperature + Math.sin(h / 24.0 * Math.PI * 2) * 4));
            int hum = Math.max(30, Math.min(95, Math.round((float) (humidity + Math.cos(h / 24.0 * Math.PI * 2) * 8))));
            int hourAqi = Math.max(20, Math.min(300, Math.round((float) (aqi + Math.sin(h / 24.0 * Math.PI * 2) * 25))));
            Instant time = now.withHour(h).withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant();
            points.add(new HourlyPoint(time, temp, hum, hourAqi));
        }
        return points;
    }

    private List<DailyForecast> simulatedForecasts(String city, int currentTemperature) {
        int base = city == null ? 0 : city.chars().sum();
        List<DailyForecast> days = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String[] texts = {"晴", "多云", "阴", "小雨", "霾"};
        String[] weeks = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < 7; i++) {
            LocalDateTime date = now.plusDays(i);
            int high = currentTemperature + 2 + Math.round((float) Math.sin((base + i) / 3.0) * 4);
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

    private List<HourlyForecast> simulatedHourlyForecasts(int temperature, int humidity) {
        List<HourlyForecast> hours = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String[] texts = {"多云", "晴", "阴", "小雨"};
        for (int i = 0; i < 24; i++) {
            LocalDateTime time = now.plusHours(i).withMinute(0).withSecond(0).withNano(0);
            int h = time.getHour();
            hours.add(new HourlyForecast(
                time.atZone(ZoneId.systemDefault()).toInstant(),
                texts[i % texts.length],
                Math.round((float) (temperature + Math.sin(h / 24.0 * Math.PI * 2) * 4)),
                Math.max(30, Math.min(95, Math.round((float) (humidity + Math.cos(h / 24.0 * Math.PI * 2) * 8)))),
                "东北风",
                "1-3级",
                i % 7 == 0 ? 0.2 : 0
            ));
        }
        return hours;
    }

    private List<LifeIndex> simulatedIndexes() {
        return List.of(
            new LifeIndex("穿衣指数", "舒适", "适合穿薄外套、长袖衬衫等春秋服装。"),
            new LifeIndex("运动指数", "较适宜", "空气质量尚可，适合适量户外运动。"),
            new LifeIndex("紫外线指数", "中等", "外出可适当涂抹防晒用品。"),
            new LifeIndex("洗车指数", "适宜", "近期降水概率较低，适合洗车。"),
            new LifeIndex("感冒指数", "少发", "昼夜温差不大，感冒概率较低。"),
            new LifeIndex("晨练指数", "适宜", "清晨空气较好，适合晨练。")
        );
    }

    private Instant parseBaiduTime(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.systemDefault())
                .toInstant();
        } catch (Exception error) {
            return Instant.now();
        }
    }

    private int windSpeed(String windClass) {
        if (windClass == null || windClass.isBlank()) {
            return 0;
        }
        String digits = windClass.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        return parseInt(String.valueOf(digits.charAt(0)), 0);
    }

    private int parseInt(String value, int fallback) {
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (Exception error) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception error) {
            return fallback;
        }
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                result.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前 Java 环境不支持 MD5", error);
        }
    }
}
