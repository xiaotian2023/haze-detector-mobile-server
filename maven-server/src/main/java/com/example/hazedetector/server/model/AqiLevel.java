package com.example.hazedetector.server.model;

public record AqiLevel(String name, String color, String advice) {
    public static AqiLevel from(int aqi) {
        if (aqi <= 50) return new AqiLevel("优", "#2fbf71", "空气质量理想，适合户外活动。");
        if (aqi <= 100) return new AqiLevel("良", "#95c11f", "可正常出行，敏感人群减少长时间户外运动。");
        if (aqi <= 150) return new AqiLevel("轻度污染", "#f2b705", "敏感人群建议佩戴口罩，减少剧烈运动。");
        if (aqi <= 200) return new AqiLevel("中度污染", "#f28c28", "建议佩戴防护口罩，儿童与老人减少外出。");
        if (aqi <= 300) return new AqiLevel("重度污染", "#c0392b", "尽量留在室内，关闭门窗并使用空气净化设备。");
        return new AqiLevel("严重污染", "#7b1e3a", "避免外出，必要出行需全程佩戴高等级防护口罩。");
    }
}
