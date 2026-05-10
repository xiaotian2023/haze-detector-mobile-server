package com.example.hazedetector.server.repository;

import com.example.hazedetector.server.model.City;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class CityRepository {
    private final Map<String, City> cities = new LinkedHashMap<>();

    public CityRepository() {
        add("北京", 39.9042, 116.4074);
        add("上海", 31.2304, 121.4737);
        add("广州", 23.1291, 113.2644);
        add("深圳", 22.5431, 114.0579);
        add("杭州", 30.2741, 120.1551);
        add("南京", 32.0603, 118.7969);
        add("成都", 30.5728, 104.0668);
        add("重庆", 29.5630, 106.5516);
        add("武汉", 30.5928, 114.3055);
        add("西安", 34.3416, 108.9398);
        add("天津", 39.3434, 117.3616);
        add("郑州", 34.7466, 113.6254);
        add("长沙", 28.2282, 112.9388);
        add("青岛", 36.0671, 120.3826);
        add("济南", 36.6512, 117.1201);
        add("合肥", 31.8206, 117.2272);
        add("福州", 26.0745, 119.2965);
        add("厦门", 24.4798, 118.0894);
        add("昆明", 25.0389, 102.7183);
        add("沈阳", 41.8057, 123.4315);
        add("哈尔滨", 45.8038, 126.5349);
        add("石家庄", 38.0428, 114.5149);
        add("太原", 37.8706, 112.5489);
        add("南昌", 28.6820, 115.8582);
        add("南宁", 22.8170, 108.3669);
        add("海口", 20.0440, 110.1999);
        add("兰州", 36.0611, 103.8343);
        add("乌鲁木齐", 43.8256, 87.6168);
        add("呼和浩特", 40.8426, 111.7492);
        add("拉萨", 29.6520, 91.1721);
    }

    public Collection<String> names() {
        return cities.keySet();
    }

    public City getOrDefault(String cityName) {
        return cities.getOrDefault(cleanCity(cityName), cities.get("北京"));
    }

    public City nearest(double lat, double lon) {
        City selected = cities.get("北京");
        double min = Double.MAX_VALUE;
        for (City city : cities.values()) {
            double distance = Math.hypot(city.lat() - lat, city.lon() - lon);
            if (distance < min) {
                min = distance;
                selected = city;
            }
        }
        return selected;
    }

    public static String cleanCity(String city) {
        return city == null ? "" : city.trim().replaceAll("市$", "");
    }

    private void add(String name, double lat, double lon) {
        cities.put(name, new City(name, lat, lon));
    }
}
