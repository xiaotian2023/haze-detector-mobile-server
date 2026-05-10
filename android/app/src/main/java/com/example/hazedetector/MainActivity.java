package com.example.hazedetector;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 1001;
    private static final String DEFAULT_SERVER = "http://192.168.22.157:3000";
    private static final String SESSION_PREF = "clientSessionId";
    private static final int PAGE_BG = Color.rgb(239, 245, 243);
    private static final int INK = Color.rgb(18, 33, 39);
    private static final int MUTED = Color.rgb(95, 116, 122);
    private static final int TEAL = Color.rgb(18, 108, 115);
    private static final int CORAL = Color.rgb(232, 127, 72);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private EditText serverInput;
    private EditText cityInput;
    private TextView statusText;
    private TextView cityText;
    private TextView heroTemperatureText;
    private TextView heroConditionText;
    private TextView weatherText;
    private TextView temperatureText;
    private TextView detailText;
    private TextView aqiText;
    private TextView pollutantText;
    private TextView adviceText;
    private TextView updatedText;
    private LinearLayout hourlyList;
    private LinearLayout forecastList;
    private LinearLayout indexGrid;
    private TrendChartView chartView;
    private String currentCity = "北京";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("haze-detector", MODE_PRIVATE);
        currentCity = prefs.getString("city", "北京");
        buildUi();
        fetchWeather(currentCity);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(PAGE_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout hero = gradientCard(Color.rgb(13, 43, 54), Color.rgb(24, 88, 94));
        hero.setPadding(dp(20), dp(20), dp(20), dp(18));
        root.addView(hero);

        TextView title = text("雾霾探测系统", 32, Color.WHITE, true);
        hero.addView(title);

        TextView subtitle = text("手机原生定位 · 多日天气 · 空气质量指数", 14, Color.rgb(202, 231, 226), false);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        hero.addView(subtitle);

        cityText = text(currentCity, 30, Color.WHITE, true);
        hero.addView(cityText);

        heroTemperatureText = text("--°", 74, Color.WHITE, true);
        heroTemperatureText.setPadding(0, dp(4), 0, 0);
        hero.addView(heroTemperatureText);

        heroConditionText = text("-- · 体感 --° · AQI --", 16, Color.rgb(225, 244, 241), false);
        heroConditionText.setPadding(0, 0, 0, dp(8));
        hero.addView(heroConditionText);

        statusText = text("等待同步", 13, Color.rgb(200, 243, 209), true);
        statusText.setPadding(0, 0, 0, dp(16));
        hero.addView(statusText);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(buttonRow);

        Button locateButton = button("手机定位", Color.rgb(255, 229, 199), Color.rgb(13, 43, 54));
        Button refreshButton = button("刷新数据", Color.rgb(215, 241, 237), Color.rgb(13, 43, 54));
        buttonRow.addView(locateButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        refreshParams.leftMargin = dp(10);
        buttonRow.addView(refreshButton, refreshParams);

        addWeatherCard(root);
        addHourlyCard(root);
        addForecastCard(root);
        addAqiCard(root);
        addPollutantCard(root);
        addLifeIndexCard(root);
        addChartCard(root);
        addSettingsCard(root);
        addSearchCard(root);

        locateButton.setOnClickListener(v -> requestPhoneLocation());
        refreshButton.setOnClickListener(v -> fetchWeather(currentCity));
        setContentView(scrollView);
    }

    private void addSearchCard(LinearLayout root) {
        LinearLayout searchCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(searchCard, params);

        cityInput = editText(currentCity, "输入城市，例如：北京");
        Button cityButton = button("查询城市", TEAL, Color.WHITE);
        searchCard.addView(cityInput, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(48));
        buttonParams.topMargin = dp(10);
        searchCard.addView(cityButton, buttonParams);

        cityButton.setOnClickListener(v -> {
            String city = CityRepository.cleanCity(cityInput.getText().toString());
            if (city.isEmpty()) {
                setStatus("请输入城市", true);
                return;
            }
            currentCity = city;
            prefs.edit().putString("city", city).apply();
            cityText.setText(city);
            fetchWeather(city);
        });
    }

    private void addSettingsCard(LinearLayout root) {
        LinearLayout settingsCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(settingsCard, params);

        settingsCard.addView(label("服务器设置"));
        serverInput = editText(prefs.getString("server", DEFAULT_SERVER), "服务器地址，例如 http://192.168.1.10:3000");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(52));
        inputParams.topMargin = dp(10);
        settingsCard.addView(serverInput, inputParams);
    }

    private void addWeatherCard(LinearLayout root) {
        LinearLayout weatherCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(weatherCard, params);

        weatherCard.addView(label("天气详情"));
        temperatureText = text("--°C", 56, INK, true);
        weatherCard.addView(temperatureText);
        weatherText = text("--", 19, INK, true);
        weatherCard.addView(weatherText);
        detailText = text("体感 --°C · 湿度 --% · 风速 -- km/h", 15, MUTED, false);
        detailText.setPadding(0, dp(10), 0, 0);
        weatherCard.addView(detailText);
    }

    private void addForecastCard(LinearLayout root) {
        LinearLayout forecastCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(forecastCard, params);

        forecastCard.addView(label("未来 7 天预报"));
        forecastList = new LinearLayout(this);
        forecastList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = dp(10);
        forecastCard.addView(forecastList, listParams);
        renderForecasts(null);
    }

    private void addHourlyCard(LinearLayout root) {
        LinearLayout hourlyCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(hourlyCard, params);

        hourlyCard.addView(label("逐小时预报"));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        hourlyList = new LinearLayout(this);
        hourlyList.setOrientation(LinearLayout.HORIZONTAL);
        hourlyList.setPadding(0, dp(10), 0, 0);
        scroll.addView(hourlyList, new HorizontalScrollView.LayoutParams(-2, -2));
        hourlyCard.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        renderHourlyForecasts(null);
    }

    private void addAqiCard(LinearLayout root) {
        LinearLayout aqiCard = gradientCard(TEAL, Color.rgb(36, 132, 118));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(aqiCard, params);

        aqiCard.addView(labelLight("空气质量指数"));
        aqiText = text("--", 58, Color.WHITE, true);
        aqiCard.addView(aqiText);
        adviceText = text("正在生成出行建议", 16, Color.rgb(235, 250, 247), false);
        adviceText.setPadding(0, dp(8), 0, 0);
        aqiCard.addView(adviceText);
    }

    private void addPollutantCard(LinearLayout root) {
        LinearLayout pollutantCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(pollutantCard, params);

        pollutantCard.addView(label("污染物监测"));
        pollutantText = text("PM2.5 --  PM10 --  CO --\nNO₂ --  SO₂ --  O₃ --", 16, INK, false);
        pollutantText.setLineSpacing(dp(6), 1.0f);
        pollutantText.setPadding(0, dp(8), 0, 0);
        pollutantCard.addView(pollutantText);
    }

    private void addLifeIndexCard(LinearLayout root) {
        LinearLayout indexCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(indexCard, params);

        indexCard.addView(label("生活指数"));
        indexGrid = new LinearLayout(this);
        indexGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, -2);
        gridParams.topMargin = dp(10);
        indexCard.addView(indexGrid, gridParams);
        renderIndexes(null);
    }

    private void addChartCard(LinearLayout root) {
        LinearLayout chartCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(chartCard, params);

        chartCard.addView(label("温度 / 湿度 / AQI 趋势"));
        updatedText = text("--", 13, MUTED, false);
        updatedText.setPadding(0, dp(4), 0, dp(8));
        chartCard.addView(updatedText);
        chartView = new TrendChartView(this);
        chartCard.addView(chartView, new LinearLayout.LayoutParams(-1, dp(240)));
    }

    private void requestPhoneLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_LOCATION);
            return;
        }
        locateBySystem();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            locateBySystem();
        } else {
            setStatus("定位权限被拒绝，请在系统设置中允许定位", true);
        }
    }

    private void locateBySystem() {
        setStatus("正在读取手机定位", false);
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            setStatus("系统定位服务不可用", true);
            return;
        }

        try {
            Location last = bestLastLocation(manager);
            if (last != null) {
                handleLocation(last);
                return;
            }

            String provider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER
                : LocationManager.NETWORK_PROVIDER;
            manager.requestSingleUpdate(provider, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    handleLocation(location);
                }

                @Override
                public void onProviderDisabled(String provider) {
                    setStatus("请开启手机定位服务", true);
                }
            }, Looper.getMainLooper());
        } catch (SecurityException error) {
            setStatus("缺少定位权限", true);
        } catch (IllegalArgumentException error) {
            setStatus("请开启 GPS 或网络定位", true);
        }
    }

    private Location bestLastLocation(LocationManager manager) {
        Location gps = null;
        Location network = null;
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException ignored) {
            return null;
        }
        if (gps == null) return network;
        if (network == null) return gps;
        return gps.getTime() >= network.getTime() ? gps : network;
    }

    private void handleLocation(Location location) {
        setStatus("正在通过百度地图识别城市", false);
        syncLocation(currentCity, location, true);
    }

    private void syncLocation(String city, Location location, boolean loadWeather) {
        saveServer();
        setStatus("正在保存定位到服务器", false);
        executor.execute(() -> {
            try {
                WeatherClient client = new WeatherClient(serverUrl(), clientSessionId());
                String resolvedCity = client.syncLocation(city, location);
                rememberClientSession(client);
                mainHandler.post(() -> {
                    currentCity = resolvedCity;
                    prefs.edit().putString("city", resolvedCity).apply();
                    cityText.setText(resolvedCity);
                    cityInput.setText(resolvedCity);
                    setStatus("定位已保存到服务器", false);
                    if (loadWeather) fetchWeather(resolvedCity);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    setStatus("服务器不可用，已使用本机定位数据", true);
                    if (loadWeather) fetchWeather(city);
                });
            }
        });
    }

    private void fetchWeather(String city) {
        saveServer();
        setStatus("正在获取天气和 AQI", false);
        executor.execute(() -> {
            WeatherData data;
            try {
                WeatherClient client = new WeatherClient(serverUrl(), clientSessionId());
                data = client.fetchWeather(city);
                rememberClientSession(client);
            } catch (Exception error) {
                data = WeatherClient.fallbackWeather(city);
            }
            WeatherData finalData = data;
            mainHandler.post(() -> renderWeather(finalData));
        });
    }

    private void renderWeather(WeatherData data) {
        if (data.city != null && !data.city.isEmpty()) {
            currentCity = data.city;
            cityText.setText(data.city);
            cityInput.setText(data.city);
            prefs.edit().putString("city", data.city).apply();
        }
        heroTemperatureText.setText(data.temperature + "°");
        heroConditionText.setText(data.weatherText + " · 体感 " + data.apparentTemperature + "° · AQI " + data.aqi + " " + data.level);
        temperatureText.setText(data.temperature + "°C");
        weatherText.setText(data.weatherText);
        detailText.setText("体感 " + data.apparentTemperature + "°C · 湿度 " + data.humidity + "% · 风速 " + data.windSpeed + " km/h");
        aqiText.setText(data.aqi + "  " + data.level);
        pollutantText.setText("PM2.5 " + data.pm25 + " μg/m³    PM10 " + data.pm10 + " μg/m³    CO " + data.co + " mg/m³\n"
            + "NO₂ " + data.no2 + " μg/m³    SO₂ " + data.so2 + " μg/m³    O₃ " + data.o3 + " μg/m³");
        adviceText.setText(data.advice);
        updatedText.setText("更新 " + data.updatedAt + " · 来源：" + data.source);
        renderHourlyForecasts(data);
        renderForecasts(data);
        renderIndexes(data);
        chartView.setPoints(data.points);
        setStatus("数据已更新", false);
    }

    private void renderHourlyForecasts(WeatherData data) {
        if (hourlyList == null) {
            return;
        }
        hourlyList.removeAllViews();
        if (data == null || data.hourlyForecasts.isEmpty()) {
            TextView empty = text("等待逐小时数据", 15, MUTED, false);
            empty.setPadding(0, dp(6), 0, dp(2));
            hourlyList.addView(empty);
            return;
        }
        for (int i = 0; i < data.hourlyForecasts.size(); i++) {
            HourlyForecast hour = data.hourlyForecasts.get(i);
            LinearLayout item = hourlyItem(hour, i == 0);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(dp(76), -2);
            if (i > 0) itemParams.leftMargin = dp(8);
            hourlyList.addView(item, itemParams);
        }
    }

    private LinearLayout hourlyItem(HourlyForecast hour, boolean first) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(8), dp(10), dp(8), dp(10));
        item.setBackground(rounded(first ? Color.rgb(232, 246, 243) : Color.rgb(246, 250, 249), dp(16), Color.argb(22, 18, 33, 39), dp(1)));

        TextView time = text(first ? "现在" : hour.time, 12, MUTED, true);
        TextView icon = text(weatherSymbol(hour.text), 24, TEAL, true);
        TextView temp = text(hour.temperature + "°", 20, INK, true);
        TextView desc = text(hour.text, 12, MUTED, false);
        TextView humidity = text("湿 " + hour.humidity + "%", 11, MUTED, false);
        item.addView(time);
        item.addView(icon);
        item.addView(temp);
        item.addView(desc);
        item.addView(humidity);
        return item;
    }

    private void renderForecasts(WeatherData data) {
        if (forecastList == null) {
            return;
        }
        forecastList.removeAllViews();
        if (data == null || data.forecasts.isEmpty()) {
            TextView empty = text("等待天气数据", 15, MUTED, false);
            empty.setPadding(0, dp(6), 0, dp(2));
            forecastList.addView(empty);
            return;
        }
        for (int i = 0; i < data.forecasts.size(); i++) {
            ForecastDay day = data.forecasts.get(i);
            LinearLayout row = forecastRow(day);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) rowParams.topMargin = dp(8);
            forecastList.addView(row, rowParams);
        }
    }

    private LinearLayout forecastRow(ForecastDay day) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rounded(Color.rgb(246, 250, 249), dp(14), Color.argb(26, 18, 33, 39), dp(1)));

        LinearLayout dateCol = new LinearLayout(this);
        dateCol.setOrientation(LinearLayout.VERTICAL);
        TextView week = text(day.week == null || day.week.isEmpty() ? "--" : day.week, 15, INK, true);
        TextView date = text(day.date, 12, MUTED, false);
        dateCol.addView(week);
        dateCol.addView(date);
        row.addView(dateCol, new LinearLayout.LayoutParams(0, -2, 0.9f));

        LinearLayout weatherCol = new LinearLayout(this);
        weatherCol.setOrientation(LinearLayout.VERTICAL);
        TextView weather = text(day.dayText + " / " + day.nightText, 15, INK, true);
        TextView wind = text(day.windDirection + " " + day.windClass, 12, MUTED, false);
        weatherCol.addView(weather);
        weatherCol.addView(wind);
        row.addView(weatherCol, new LinearLayout.LayoutParams(0, -2, 1.35f));

        TextView temp = text(day.low + "° / " + day.high + "°", 16, CORAL, true);
        temp.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(temp, new LinearLayout.LayoutParams(0, -2, 0.85f));
        return row;
    }

    private void renderIndexes(WeatherData data) {
        if (indexGrid == null) {
            return;
        }
        indexGrid.removeAllViews();
        if (data == null || data.indexes.isEmpty()) {
            TextView empty = text("等待生活指数", 15, MUTED, false);
            empty.setPadding(0, dp(6), 0, dp(2));
            indexGrid.addView(empty);
            return;
        }
        for (int i = 0; i < data.indexes.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) rowParams.topMargin = dp(8);
            indexGrid.addView(row, rowParams);

            row.addView(indexItem(data.indexes.get(i)), new LinearLayout.LayoutParams(0, -2, 1));
            if (i + 1 < data.indexes.size()) {
                LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 1);
                rightParams.leftMargin = dp(8);
                row.addView(indexItem(data.indexes.get(i + 1)), rightParams);
            }
        }
    }

    private LinearLayout indexItem(LifeIndex index) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        item.setBackground(rounded(Color.rgb(246, 250, 249), dp(14), Color.argb(24, 18, 33, 39), dp(1)));

        TextView name = text(index.name, 12, MUTED, true);
        TextView brief = text(index.brief, 18, INK, true);
        TextView detail = text(index.detail, 12, MUTED, false);
        detail.setMaxLines(2);
        detail.setPadding(0, dp(4), 0, 0);
        item.addView(name);
        item.addView(brief);
        item.addView(detail);
        return item;
    }

    private String weatherSymbol(String text) {
        if (text == null) return "•";
        if (text.contains("雨")) return "☂";
        if (text.contains("雪")) return "❄";
        if (text.contains("晴")) return "☀";
        if (text.contains("云")) return "☁";
        if (text.contains("阴")) return "●";
        if (text.contains("霾") || text.contains("雾")) return "≋";
        return "•";
    }

    private String serverUrl() {
        String server = serverInput.getText().toString().trim();
        if (server.endsWith("/")) server = server.substring(0, server.length() - 1);
        return server.isEmpty() ? DEFAULT_SERVER : server;
    }

    private void saveServer() {
        prefs.edit().putString("server", serverUrl()).apply();
    }

    private String clientSessionId() {
        return prefs.getString(SESSION_PREF, "");
    }

    private void rememberClientSession(WeatherClient client) {
        String sessionId = client.sessionId();
        if (sessionId != null && !sessionId.isEmpty()) {
            prefs.edit().putString(SESSION_PREF, sessionId).apply();
        }
    }

    private void setStatus(String text, boolean error) {
        statusText.setText(text);
        statusText.setTextColor(error ? Color.rgb(255, 213, 204) : Color.rgb(200, 243, 209));
    }

    private LinearLayout card(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(rounded(color, dp(18), Color.argb(24, 18, 33, 39), dp(1)));
        return layout;
    }

    private LinearLayout gradientCard(int startColor, int endColor) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setOrientation(GradientDrawable.Orientation.TL_BR);
        background.setColors(new int[] {startColor, endColor});
        background.setCornerRadius(dp(20));
        layout.setBackground(background);
        return layout;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        if (strokeWidth > 0) {
            background.setStroke(strokeWidth, strokeColor);
        }
        return background;
    }

    private TextView label(String value) {
        TextView view = text(value, 12, MUTED, true);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private TextView labelLight(String value) {
        TextView view = text(value, 12, Color.argb(190, 255, 255, 255), true);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value) {
        return button(value, Color.rgb(255, 227, 197), Color.rgb(13, 43, 54));
    }

    private Button button(String value, int backgroundColor, int textColor) {
        Button view = new Button(this);
        view.setText(value);
        view.setTextColor(textColor);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setAllCaps(false);
        view.setBackground(rounded(backgroundColor, dp(14), Color.TRANSPARENT, 0));
        return view;
    }

    private EditText editText(String value, String hint) {
        EditText view = new EditText(this);
        view.setText(value);
        view.setHint(hint);
        view.setSingleLine(true);
        view.setTextSize(15);
        view.setInputType(InputType.TYPE_CLASS_TEXT);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setTextColor(INK);
        view.setHintTextColor(Color.rgb(135, 151, 154));
        view.setBackground(rounded(Color.WHITE, dp(14), Color.argb(38, 18, 33, 39), dp(1)));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
