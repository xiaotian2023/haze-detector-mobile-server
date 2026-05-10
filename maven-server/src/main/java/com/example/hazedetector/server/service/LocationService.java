package com.example.hazedetector.server.service;

import com.example.hazedetector.server.model.City;
import com.example.hazedetector.server.model.Coordinates;
import com.example.hazedetector.server.model.LocationRecord;
import com.example.hazedetector.server.model.LocationRequest;
import com.example.hazedetector.server.model.ResolvedLocation;
import com.example.hazedetector.server.repository.CityRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

@Service
public class LocationService {
    private static final Path DATA_DIR = Path.of("data");
    private static final String DEFAULT_APP_ID = "default";

    private final CityRepository cityRepository;
    private final BaiduGeocodingService baiduGeocodingService;

    public LocationService(CityRepository cityRepository, BaiduGeocodingService baiduGeocodingService) {
        this.cityRepository = cityRepository;
        this.baiduGeocodingService = baiduGeocodingService;
    }

    public LocationRecord current() {
        return current(DEFAULT_APP_ID);
    }

    public LocationRecord current(String storageKey) {
        Path storeFile = storeFile(storageKey);
        Properties store = loadStore(storeFile);
        String cityName = store.getProperty("city", "北京");
        String district = store.getProperty("district", "");
        String displayName = store.getProperty("displayName", displayName(cityName, district));
        City fallbackCity = cityRepository.getOrDefault(cityName);
        Coordinates coords = new Coordinates(
            parseDouble(store.getProperty("lat"), fallbackCity.lat()),
            parseDouble(store.getProperty("lon"), fallbackCity.lon())
        );
        String districtId = store.getProperty("districtId", "");
        if (districtId.isBlank() || district.isBlank()) {
            ResolvedLocation resolvedLocation = baiduGeocodingService.resolveLocation(coords.lat(), coords.lon(), fallbackCity.name());
            if (!resolvedLocation.districtId().isBlank()) {
                cityName = resolvedLocation.city();
                district = resolvedLocation.district();
                displayName = resolvedLocation.displayName();
                districtId = resolvedLocation.districtId();
                store.setProperty("city", cityName);
                store.setProperty("district", district);
                store.setProperty("displayName", displayName);
                store.setProperty("districtId", districtId);
                saveStore(storeFile, store);
            }
        }
        Instant updatedAt = parseInstant(store.getProperty("updatedAt"));
        return new LocationRecord(CityRepository.cleanCity(cityName), district, displayName, coords, updatedAt, districtId);
    }

    public LocationRecord save(LocationRequest request) {
        return save(DEFAULT_APP_ID, request);
    }

    public LocationRecord save(String storageKey, LocationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("定位数据无效");
        }

        Coordinates coords = request.coords();
        String cityName = CityRepository.cleanCity(request.city());
        String district = "";
        String displayName = cityName;
        String districtId = "";
        if (coords != null) {
            City fallbackCity = cityRepository.nearest(coords.lat(), coords.lon());
            ResolvedLocation resolvedLocation = baiduGeocodingService.resolveLocation(coords.lat(), coords.lon(), fallbackCity.name());
            cityName = resolvedLocation.city();
            district = resolvedLocation.district();
            displayName = resolvedLocation.displayName();
            districtId = resolvedLocation.districtId();
        }

        if (cityName.isEmpty()) {
            throw new IllegalArgumentException("城市名称无效");
        }

        if (coords == null) {
            City knownCity = cityRepository.getOrDefault(cityName);
            coords = new Coordinates(knownCity.lat(), knownCity.lon());
        }

        Instant now = Instant.now();

        Properties store = new Properties();
        store.setProperty("city", cityName);
        store.setProperty("district", district);
        store.setProperty("displayName", displayName(cityName, district, displayName));
        store.setProperty("lat", String.valueOf(coords.lat()));
        store.setProperty("lon", String.valueOf(coords.lon()));
        store.setProperty("districtId", districtId);
        store.setProperty("updatedAt", now.toString());
        saveStore(storeFile(storageKey), store);
        return new LocationRecord(cityName, district, displayName(cityName, district, displayName), coords, now, districtId);
    }

    public LocationRecord reverseGeocode(double lat, double lon) {
        City fallbackCity = cityRepository.nearest(lat, lon);
        ResolvedLocation resolvedLocation = baiduGeocodingService.resolveLocation(lat, lon, fallbackCity.name());
        return new LocationRecord(resolvedLocation.city(), resolvedLocation.district(), resolvedLocation.displayName(), new Coordinates(lat, lon), Instant.now(), resolvedLocation.districtId());
    }

    private Properties loadStore(Path storeFile) {
        ensureStore(storeFile);
        Properties store = new Properties();
        try (InputStream input = Files.newInputStream(storeFile)) {
            store.load(input);
            return store;
        } catch (IOException error) {
            throw new IllegalStateException("读取定位数据失败", error);
        }
    }

    private void ensureStore(Path storeFile) {
        try {
            Files.createDirectories(DATA_DIR);
            if (!Files.exists(storeFile)) {
                Path defaultStoreFile = storeFile(DEFAULT_APP_ID);
                if (!storeFile.equals(defaultStoreFile) && Files.exists(defaultStoreFile)) {
                    Files.copy(defaultStoreFile, storeFile);
                    return;
                }
                City city = cityRepository.getOrDefault("北京");
                Properties store = new Properties();
                store.setProperty("city", city.name());
                store.setProperty("district", "");
                store.setProperty("displayName", city.name());
                store.setProperty("lat", String.valueOf(city.lat()));
                store.setProperty("lon", String.valueOf(city.lon()));
                store.setProperty("districtId", "110000");
                store.setProperty("updatedAt", Instant.now().toString());
                saveStore(storeFile, store);
            }
        } catch (IOException error) {
            throw new IllegalStateException("初始化定位存储失败", error);
        }
    }

    private void saveStore(Path storeFile, Properties store) {
        try {
            Files.createDirectories(DATA_DIR);
            try (OutputStream output = Files.newOutputStream(storeFile)) {
                store.store(output, "Haze detector location store");
            }
        } catch (IOException error) {
            throw new IllegalStateException("保存定位数据失败", error);
        }
    }

    private Path storeFile(String storageKey) {
        String safeStorageKey = safeStorageKey(storageKey);
        if (DEFAULT_APP_ID.equals(safeStorageKey)) {
            return DATA_DIR.resolve("store.properties");
        }
        return DATA_DIR.resolve("store-" + safeStorageKey + ".properties");
    }

    private String safeStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return DEFAULT_APP_ID;
        }
        String safe = storageKey.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        while (safe.contains("..")) {
            safe = safe.replace("..", ".");
        }
        if (safe.isBlank() || ".".equals(safe) || safe.length() > 80) {
            return DEFAULT_APP_ID;
        }
        return safe;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception error) {
            return Instant.now();
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception error) {
            return fallback;
        }
    }

    private String displayName(String city, String district) {
        return displayName(city, district, "");
    }

    private String displayName(String city, String district, String fallback) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        if (district == null || district.isBlank()) {
            return CityRepository.cleanCity(city);
        }
        String cleanCity = CityRepository.cleanCity(city);
        if (cleanCity.isBlank() || cleanCity.equals(district)) {
            return district;
        }
        return cleanCity + " · " + district;
    }
}
