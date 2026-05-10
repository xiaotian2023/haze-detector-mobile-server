package com.example.hazedetector;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;

final class WeatherClient {
    private static final String SESSION_HEADER = "X-Client-Session-Id";

    private final String serverUrl;
    private String sessionId;

    WeatherClient(String serverUrl) {
        this(serverUrl, "");
    }

    WeatherClient(String serverUrl, String sessionId) {
        this.serverUrl = normalizeServer(serverUrl);
        this.sessionId = sessionId == null ? "" : sessionId.trim();
    }

    String syncLocation(String city, Location location) throws Exception {
        JSONObject body = new JSONObject();
        body.put("city", city);
        if (location != null) {
            JSONObject coords = new JSONObject();
            coords.put("lat", location.getLatitude());
            coords.put("lon", location.getLongitude());
            body.put("coords", coords);
        }
        String json = postJson(serverUrl + "/api/location", body.toString());
        JSONObject root = new JSONObject(json);
        String displayName = CityRepository.cleanCity(root.optString("displayName", ""));
        if (!displayName.isEmpty()) {
            return displayName;
        }
        return CityRepository.cleanCity(root.optString("city", city));
    }

    WeatherData fetchWeather(String city) throws Exception {
        String encodedCity = URLEncoder.encode(city, "UTF-8");
        String json = getText(serverUrl + "/api/weather?city=" + encodedCity);
        return parseWeather(json);
    }

    String sessionId() {
        return sessionId;
    }

    static WeatherData fallbackWeather(String city) {
        int base = 0;
        for (int i = 0; i < city.length(); i++) base += city.charAt(i);
        int hour = new Date().getHours();
        int temperature = Math.round((float) (16 + base % 12 + Math.sin(hour / 24.0 * Math.PI * 2) * 5));
        int humidity = Math.max(35, Math.min(92, Math.round((float) (58 + base % 20 + Math.cos(hour / 24.0 * Math.PI * 2) * 12))));
        int pm25 = Math.max(12, Math.round(30 + base % 75 + humidity * 0.35f));
        int aqi = Math.max(20, Math.min(260, Math.round(pm25 * 1.6f)));
        String[] levelAndAdvice = AqiAdvisor.levelAndAdvice(aqi);

        WeatherData data = new WeatherData();
        data.city = city;
        data.source = "本机模拟数据";
        data.updatedAt = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
        data.weatherText = humidity > 78 && pm25 > 90 ? "雾霾" : "多云";
        data.temperature = temperature;
        data.apparentTemperature = temperature + 1;
        data.humidity = humidity;
        data.windSpeed = 6 + base % 15;
        data.aqi = aqi;
        data.level = levelAndAdvice[0];
        data.advice = levelAndAdvice[1];
        data.pm25 = pm25;
        data.pm10 = Math.round(pm25 * 1.45f);
        data.co = Math.round((0.4 + (base % 8) / 10.0) * 10.0) / 10.0;
        data.no2 = 18 + base % 42;
        data.so2 = 5 + base % 18;
        data.o3 = 60 + base % 80;
        for (int i = 11; i >= 0; i--) {
            int h = (hour - i + 24) % 24;
            data.points.add(new TrendPoint(
                String.format(Locale.CHINA, "%02d:00", h),
                Math.round((float) (temperature + Math.sin(h / 24.0 * Math.PI * 2) * 4)),
                Math.max(30, Math.min(95, Math.round((float) (humidity + Math.cos(h / 24.0 * Math.PI * 2) * 8)))),
                Math.max(20, Math.min(300, Math.round((float) (aqi + Math.sin((h + base) / 24.0 * Math.PI * 2) * 25))))
            ));
        }
        addFallbackHourlyForecasts(data, hour, temperature, humidity);
        addFallbackForecasts(data, base, temperature);
        addFallbackIndexes(data);
        return data;
    }

    private WeatherData parseWeather(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject weather = root.getJSONObject("weather");
        JSONObject air = root.getJSONObject("air");
        WeatherData data = new WeatherData();
        data.city = root.getString("city");
        data.source = root.optString("source", "服务器");
        data.updatedAt = formatTime(root.optString("updatedAt", ""));
        data.weatherText = weather.optString("text", "--");
        data.temperature = weather.optInt("temperature", 0);
        data.apparentTemperature = weather.optInt("apparentTemperature", data.temperature);
        data.humidity = weather.optInt("humidity", 0);
        data.windSpeed = weather.optInt("windSpeed", 0);
        data.aqi = air.optInt("aqi", 0);
        data.level = air.optString("level", "--");
        data.advice = air.optString("advice", "--");
        data.pm25 = air.optInt("pm25", 0);
        data.pm10 = air.optInt("pm10", 0);
        data.co = air.optDouble("co", 0);
        data.no2 = air.optInt("no2", 0);
        data.so2 = air.optInt("so2", 0);
        data.o3 = air.optInt("o3", 0);
        JSONArray hourly = root.optJSONArray("hourly");
        if (hourly != null) {
            for (int i = 0; i < hourly.length(); i++) {
                JSONObject item = hourly.getJSONObject(i);
                data.points.add(new TrendPoint(
                    item.optString("time", ""),
                    item.optInt("temperature", data.temperature),
                    item.optInt("humidity", data.humidity),
                    item.optInt("aqi", data.aqi)
                ));
            }
        }
        JSONArray forecasts = root.optJSONArray("forecasts");
        if (forecasts != null) {
            for (int i = 0; i < forecasts.length(); i++) {
                JSONObject item = forecasts.getJSONObject(i);
                data.forecasts.add(new ForecastDay(
                    item.optString("date", "--"),
                    item.optString("week", ""),
                    item.optString("dayText", "--"),
                    item.optString("nightText", item.optString("dayText", "--")),
                    item.optInt("high", data.temperature),
                    item.optInt("low", data.temperature),
                    item.optString("windDirection", "--"),
                    item.optString("windClass", "--")
                ));
            }
        }
        if (data.forecasts.isEmpty()) {
            addFallbackForecasts(data, cityBase(data.city), data.temperature);
        }
        JSONArray hourlyForecasts = root.optJSONArray("hourlyForecasts");
        if (hourlyForecasts != null) {
            for (int i = 0; i < hourlyForecasts.length(); i++) {
                JSONObject item = hourlyForecasts.getJSONObject(i);
                data.hourlyForecasts.add(new HourlyForecast(
                    formatHour(item.optString("time", "")),
                    item.optString("text", "--"),
                    item.optInt("temperature", data.temperature),
                    item.optInt("humidity", data.humidity),
                    item.optString("windDirection", "--"),
                    item.optString("windClass", "--"),
                    item.optDouble("precipitation", 0)
                ));
            }
        }
        if (data.hourlyForecasts.isEmpty()) {
            addFallbackHourlyForecasts(data, new Date().getHours(), data.temperature, data.humidity);
        }
        JSONArray indexes = root.optJSONArray("indexes");
        if (indexes != null) {
            for (int i = 0; i < indexes.length(); i++) {
                JSONObject item = indexes.getJSONObject(i);
                data.indexes.add(new LifeIndex(
                    item.optString("name", "--"),
                    item.optString("brief", "--"),
                    item.optString("detail", "")
                ));
            }
        }
        if (data.indexes.isEmpty()) {
            addFallbackIndexes(data);
        }
        return data;
    }

    private static int cityBase(String city) {
        int base = 0;
        if (city == null) {
            return base;
        }
        for (int i = 0; i < city.length(); i++) {
            base += city.charAt(i);
        }
        return base;
    }

    private static void addFallbackForecasts(WeatherData data, int base, int temperature) {
        String[] texts = {"晴", "多云", "阴", "小雨", "霾"};
        String[] weeks = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        for (int i = 0; i < 7; i++) {
            int high = temperature + 2 + Math.round((float) Math.sin((base + i) / 3.0) * 4);
            int low = high - 5 - Math.abs((base + i) % 4);
            data.forecasts.add(new ForecastDay(
                new SimpleDateFormat("MM-dd", Locale.CHINA).format(calendar.getTime()),
                i == 0 ? "今天" : weeks[calendar.get(Calendar.DAY_OF_WEEK) - 1],
                texts[positiveMod(base + i, texts.length)],
                texts[positiveMod(base + i + 1, texts.length)],
                high,
                low,
                "东北风",
                "1-3级"
            ));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    private static void addFallbackHourlyForecasts(WeatherData data, int hour, int temperature, int humidity) {
        String[] texts = {"多云", "晴", "阴", "小雨"};
        for (int i = 0; i < 24; i++) {
            int h = (hour + i) % 24;
            data.hourlyForecasts.add(new HourlyForecast(
                String.format(Locale.CHINA, "%02d:00", h),
                texts[positiveMod(i, texts.length)],
                Math.round((float) (temperature + Math.sin(h / 24.0 * Math.PI * 2) * 4)),
                Math.max(30, Math.min(95, Math.round((float) (humidity + Math.cos(h / 24.0 * Math.PI * 2) * 8)))),
                "东北风",
                "1-3级",
                i % 7 == 0 ? 0.2 : 0
            ));
        }
    }

    private static void addFallbackIndexes(WeatherData data) {
        data.indexes.add(new LifeIndex("穿衣指数", "舒适", "适合穿薄外套、长袖衬衫等春秋服装。"));
        data.indexes.add(new LifeIndex("运动指数", "较适宜", "空气质量尚可，适合适量户外运动。"));
        data.indexes.add(new LifeIndex("紫外线指数", "中等", "外出可适当涂抹防晒用品。"));
        data.indexes.add(new LifeIndex("洗车指数", "适宜", "近期降水概率较低，适合洗车。"));
        data.indexes.add(new LifeIndex("感冒指数", "少发", "昼夜温差不大，感冒概率较低。"));
        data.indexes.add(new LifeIndex("晨练指数", "适宜", "清晨空气较好，适合晨练。"));
    }

    private static int positiveMod(int value, int size) {
        int result = value % size;
        return result < 0 ? result + size : result;
    }

    private String getText(String rawUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");
        applySession(connection);
        return readResponse(connection);
    }

    private String postJson(String rawUrl, String json) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        applySession(connection);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(body);
        }
        return readResponse(connection);
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        rememberSession(connection);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(),
            StandardCharsets.UTF_8
        ));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        if (code < 200 || code >= 300) throw new IllegalStateException(builder.toString());
        return builder.toString();
    }

    private String formatTime(String iso) {
        try {
            String normalized = iso.replace("Z", "+0000");
            if (normalized.contains(".")) {
                normalized = normalized.substring(0, normalized.indexOf(".")) + "+0000";
            }
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(normalized);
            return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(date);
        } catch (Exception error) {
            return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
        }
    }

    private String formatHour(String iso) {
        try {
            String normalized = iso.replace("Z", "+0000");
            if (normalized.contains(".")) {
                normalized = normalized.substring(0, normalized.indexOf(".")) + "+0000";
            }
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(normalized);
            return new SimpleDateFormat("HH:mm", Locale.CHINA).format(date);
        } catch (Exception error) {
            return "--";
        }
    }

    private static String normalizeServer(String value) {
        String server = value == null ? "" : value.trim();
        if (server.endsWith("/")) server = server.substring(0, server.length() - 1);
        return server;
    }

    private void applySession(HttpURLConnection connection) {
        if (sessionId != null && !sessionId.isEmpty()) {
            connection.setRequestProperty(SESSION_HEADER, sessionId);
        }
    }

    private void rememberSession(HttpURLConnection connection) {
        String nextSessionId = connection.getHeaderField(SESSION_HEADER);
        if (nextSessionId != null && !nextSessionId.trim().isEmpty()) {
            sessionId = nextSessionId.trim();
        }
    }
}
