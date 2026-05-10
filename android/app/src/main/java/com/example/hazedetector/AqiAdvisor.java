package com.example.hazedetector;

final class AqiAdvisor {
    private AqiAdvisor() {
    }

    static String[] levelAndAdvice(int aqi) {
        if (aqi <= 50) return new String[] {"优", "空气质量理想，适合户外活动。"};
        if (aqi <= 100) return new String[] {"良", "可正常出行，敏感人群减少长时间户外运动。"};
        if (aqi <= 150) return new String[] {"轻度污染", "敏感人群建议佩戴口罩，减少剧烈运动。"};
        if (aqi <= 200) return new String[] {"中度污染", "建议佩戴防护口罩，儿童与老人减少外出。"};
        if (aqi <= 300) return new String[] {"重度污染", "尽量留在室内，关闭门窗并使用空气净化设备。"};
        return new String[] {"严重污染", "避免外出，必要出行需全程佩戴高等级防护口罩。"};
    }
}
