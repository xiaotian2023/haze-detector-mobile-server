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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 1001;
    private static final String DEFAULT_SERVER = "http://192.168.22.157:3000";
    private static final String SESSION_PREF = "clientSessionId";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private EditText serverInput;
    private EditText cityInput;
    private TextView statusText;
    private TextView cityText;
    private TextView weatherText;
    private TextView temperatureText;
    private TextView detailText;
    private TextView aqiText;
    private TextView pollutantText;
    private TextView adviceText;
    private TextView updatedText;
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
        scrollView.setBackgroundColor(Color.rgb(215, 233, 229));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout hero = card(Color.rgb(13, 43, 54));
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.addView(hero);

        TextView title = text("雾霾探测系统", 32, Color.WHITE, true);
        title.setLetterSpacing(-0.04f);
        hero.addView(title);

        TextView subtitle = text("手机原生定位 · 天气详情 · 空气质量指数", 14, Color.rgb(202, 231, 226), false);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        hero.addView(subtitle);

        cityText = text(currentCity, 30, Color.WHITE, true);
        hero.addView(cityText);

        statusText = text("等待同步", 13, Color.rgb(200, 243, 209), true);
        statusText.setPadding(0, dp(6), 0, dp(16));
        hero.addView(statusText);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(buttonRow);

        Button locateButton = button("手机定位");
        Button refreshButton = button("刷新数据");
        buttonRow.addView(locateButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        refreshParams.leftMargin = dp(10);
        buttonRow.addView(refreshButton, refreshParams);

        serverInput = editText(prefs.getString("server", DEFAULT_SERVER), "服务器地址，例如 http://192.168.1.10:3000");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(52));
        inputParams.topMargin = dp(16);
        root.addView(serverInput, inputParams);

        addSearchCard(root);
        addWeatherCard(root);
        addAqiCard(root);
        addPollutantCard(root);
        addChartCard(root);

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
        Button cityButton = button("查询城市");
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

    private void addWeatherCard(LinearLayout root) {
        LinearLayout weatherCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(weatherCard, params);

        weatherCard.addView(label("天气详情"));
        temperatureText = text("--°C", 56, Color.rgb(16, 38, 45), true);
        weatherCard.addView(temperatureText);
        weatherText = text("--", 19, Color.rgb(16, 38, 45), true);
        weatherCard.addView(weatherText);
        detailText = text("体感 --°C · 湿度 --% · 风速 -- km/h", 15, Color.rgb(99, 128, 135), false);
        detailText.setPadding(0, dp(10), 0, 0);
        weatherCard.addView(detailText);
    }

    private void addAqiCard(LinearLayout root) {
        LinearLayout aqiCard = card(Color.rgb(18, 108, 115));
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
        pollutantText = text("PM2.5 --  PM10 --  CO --\nNO₂ --  SO₂ --  O₃ --", 16, Color.rgb(16, 38, 45), false);
        pollutantText.setLineSpacing(dp(6), 1.0f);
        pollutantText.setPadding(0, dp(8), 0, 0);
        pollutantCard.addView(pollutantText);
    }

    private void addChartCard(LinearLayout root) {
        LinearLayout chartCard = card(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(chartCard, params);

        chartCard.addView(label("温度 / 湿度 / AQI 趋势"));
        updatedText = text("--", 13, Color.rgb(99, 128, 135), false);
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
        temperatureText.setText(data.temperature + "°C");
        weatherText.setText(data.weatherText);
        detailText.setText("体感 " + data.apparentTemperature + "°C · 湿度 " + data.humidity + "% · 风速 " + data.windSpeed + " km/h");
        aqiText.setText(data.aqi + "  " + data.level);
        pollutantText.setText("PM2.5 " + data.pm25 + " μg/m³    PM10 " + data.pm10 + " μg/m³    CO " + data.co + " mg/m³\n"
            + "NO₂ " + data.no2 + " μg/m³    SO₂ " + data.so2 + " μg/m³    O₃ " + data.o3 + " μg/m³");
        adviceText.setText(data.advice);
        updatedText.setText("更新 " + data.updatedAt + " · 来源：" + data.source);
        chartView.setPoints(data.points);
        setStatus("数据已更新", false);
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
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), Color.argb(28, 16, 38, 45));
        layout.setBackground(background);
        return layout;
    }

    private TextView label(String value) {
        TextView view = text(value, 12, Color.rgb(99, 128, 135), true);
        view.setLetterSpacing(0.12f);
        return view;
    }

    private TextView labelLight(String value) {
        TextView view = text(value, 12, Color.argb(190, 255, 255, 255), true);
        view.setLetterSpacing(0.12f);
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
        Button view = new Button(this);
        view.setText(value);
        view.setTextColor(Color.rgb(13, 43, 54));
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(255, 227, 197));
        background.setCornerRadius(dp(18));
        view.setBackground(background);
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
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), Color.argb(40, 16, 38, 45));
        view.setBackground(background);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
