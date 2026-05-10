package com.example.hazedetector;

final class CityRepository {
    private CityRepository() {
    }

    static String cleanCity(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("市$", "");
    }
}
