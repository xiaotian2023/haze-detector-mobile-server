package com.example.hazedetector.server.model;

import java.time.Instant;

public record LocationRecord(String city, String district, String displayName, Coordinates coords, Instant updatedAt, String districtId) {
}
