package com.example.hazedetector.server.service;

import com.example.hazedetector.server.model.ResolvedLocation;
import com.example.hazedetector.server.repository.CityRepository;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class BaiduGeocodingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaiduGeocodingService.class);
    private static final String REVERSE_GEOCODING_URL = "https://api.map.baidu.com/reverse_geocoding/v3/";
    private static final String REVERSE_GEOCODING_PATH = "/reverse_geocoding/v3/";

    private final String ak;
    private final String sk;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public BaiduGeocodingService(
        @Value("${baidu.map.ak:}") String ak,
        @Value("${baidu.map.sk:}") String sk,
        ObjectMapper objectMapper,
        RestTemplateBuilder restTemplateBuilder
    ) {
        this.ak = EnvFile.get(ak, "BAIDU_MAP_AK", "ak");
        this.sk = EnvFile.get(sk, "BAIDU_MAP_SK", "sk");
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(4))
            .setReadTimeout(Duration.ofSeconds(4))
            .build();
    }

    public String resolveCity(double lat, double lon, String fallbackCity) {
        return resolveLocation(lat, lon, fallbackCity).city();
    }

    public ResolvedLocation resolveLocation(double lat, double lon, String fallbackCity) {
        Optional<ResolvedLocation> baiduLocation = reverseGeocode(lat, lon);
        if (baiduLocation.isPresent()) {
            return baiduLocation.get();
        }
        String city = CityRepository.cleanCity(fallbackCity);
        return new ResolvedLocation(city, "", city, "");
    }

    public boolean isConfigured() {
        return !ak.isBlank();
    }

    private Optional<ResolvedLocation> reverseGeocode(double lat, double lon) {
        if (ak.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(buildReverseGeocodingUrl(lat, lon));
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root == null || root.path("status").asInt(-1) != 0) {
                LOGGER.warn("百度逆地理编码接口返回异常，lat={}，lon={}，response={}", lat, lon, root);
                return Optional.empty();
            }

            JsonNode address = root.path("result").path("addressComponent");
            String city = firstNonBlank(
                address.path("city").asText(),
                address.path("province").asText()
            );
            city = CityRepository.cleanCity(city);
            if (city.isEmpty()) {
                return Optional.empty();
            }
            String district = CityRepository.cleanCity(address.path("district").asText(""));
            return Optional.of(new ResolvedLocation(city, district, displayName(city, district), address.path("adcode").asText("")));
        } catch (Exception error) {
            LOGGER.warn("百度逆地理编码接口请求失败，lat={}，lon={}", lat, lon, error);
            return Optional.empty();
        }
    }

    private String buildReverseGeocodingUrl(double lat, double lon) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ak", ak);
        params.put("output", "json");
        params.put("coordtype", "wgs84ll");
        params.put("location", lat + "," + lon);

        String queryString = toQueryString(params);
        if (!sk.isBlank()) {
            queryString += "&sn=" + calculateSn(REVERSE_GEOCODING_PATH, queryString);
        }
        return REVERSE_GEOCODING_URL + "?" + queryString;
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String displayName(String city, String district) {
        if (district == null || district.isBlank()) {
            return city;
        }
        if (city == null || city.isBlank() || city.equals(district)) {
            return district;
        }
        return city + " · " + district;
    }
}
