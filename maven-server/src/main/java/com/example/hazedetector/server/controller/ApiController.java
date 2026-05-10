package com.example.hazedetector.server.controller;

import com.example.hazedetector.server.model.LocationRecord;
import com.example.hazedetector.server.model.LocationRequest;
import com.example.hazedetector.server.model.WeatherResponse;
import com.example.hazedetector.server.repository.CityRepository;
import com.example.hazedetector.server.service.ClientSessionService;
import com.example.hazedetector.server.service.ClientSessionService.ClientSession;
import com.example.hazedetector.server.service.LocationService;
import com.example.hazedetector.server.service.WeatherService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api")
public class ApiController {
    private final CityRepository cityRepository;
    private final ClientSessionService clientSessionService;
    private final LocationService locationService;
    private final WeatherService weatherService;

    public ApiController(CityRepository cityRepository, ClientSessionService clientSessionService, LocationService locationService, WeatherService weatherService) {
        this.cityRepository = cityRepository;
        this.clientSessionService = clientSessionService;
        this.locationService = locationService;
        this.weatherService = weatherService;
    }

    @GetMapping("/cities")
    public Collection<String> cities() {
        return cityRepository.names();
    }

    @GetMapping("/location")
    public LocationRecord location(
        @RequestParam(required = false) String sessionId,
        @RequestHeader(value = ClientSessionService.HEADER_NAME, required = false) String sessionIdHeader,
        @CookieValue(value = ClientSessionService.COOKIE_NAME, required = false) String sessionIdCookie,
        HttpServletResponse response
    ) {
        ClientSession session = resolveSession(response, sessionId, sessionIdHeader, sessionIdCookie);
        return locationService.current(session.id());
    }

    @PostMapping("/location")
    public LocationRecord saveLocation(
        @RequestBody LocationRequest request,
        @RequestParam(required = false) String sessionId,
        @RequestHeader(value = ClientSessionService.HEADER_NAME, required = false) String sessionIdHeader,
        @CookieValue(value = ClientSessionService.COOKIE_NAME, required = false) String sessionIdCookie,
        HttpServletResponse response
    ) {
        ClientSession session = resolveSession(response, sessionId, sessionIdHeader, sessionIdCookie);
        return locationService.save(session.id(), request);
    }

    @GetMapping("/reverse-geocode")
    public Map<String, Object> reverseGeocode(@RequestParam double lat, @RequestParam double lon) {
        LocationRecord location = locationService.reverseGeocode(lat, lon);
        return Map.of(
            "city", location.city(),
            "district", location.district(),
            "displayName", location.displayName(),
            "coords", location.coords(),
            "districtId", location.districtId()
        );
    }

    @GetMapping("/weather")
    public WeatherResponse weather(
        @RequestParam(required = false) String city,
        @RequestParam(required = false) String sessionId,
        @RequestHeader(value = ClientSessionService.HEADER_NAME, required = false) String sessionIdHeader,
        @CookieValue(value = ClientSessionService.COOKIE_NAME, required = false) String sessionIdCookie,
        HttpServletResponse response
    ) {
        String selectedCity = CityRepository.cleanCity(city);
        ClientSession session = resolveSession(response, sessionId, sessionIdHeader, sessionIdCookie);
        LocationRecord currentLocation = locationService.current(session.id());
        String districtId = "";
        if (selectedCity.isEmpty()) {
            selectedCity = currentLocation.city();
            districtId = currentLocation.districtId();
        } else if (selectedCity.equals(currentLocation.city()) || selectedCity.equals(currentLocation.displayName())) {
            selectedCity = currentLocation.displayName();
            districtId = currentLocation.districtId();
        }
        if (!districtId.isBlank() && !currentLocation.displayName().isBlank()) {
            selectedCity = currentLocation.displayName();
        }
        return weatherService.getWeather(selectedCity, districtId);
    }

    private ClientSession resolveSession(HttpServletResponse response, String... values) {
        ClientSession session = clientSessionService.resolve(first(values));
        response.setHeader(ClientSessionService.HEADER_NAME, session.id());
        ResponseCookie cookie = ResponseCookie.from(ClientSessionService.COOKIE_NAME, session.id())
            .httpOnly(true)
            .path("/")
            .maxAge(ClientSessionService.TTL)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return session;
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public Map<String, String> badRequest(IllegalArgumentException error) {
        return Map.of("error", error.getMessage());
    }
}
