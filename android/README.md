# 安卓 APK 工程

这是原生 Android 手机端工程，使用 Java 编写，定位功能调用手机系统 `LocationManager`，不依赖浏览器定位。

## 功能

- 运行时申请 `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`。
- 通过手机 GPS 或网络定位获取经纬度。
- 根据经纬度匹配最近城市，并把城市和坐标提交到服务端保存。
- 从服务端读取天气、AQI、污染物和趋势数据。
- 服务端不可用时使用本机模拟数据，保证离线演示。

## 打包 APK

 .\gradlew.bat  assembleRelease

## 手机连接服务端

手机和电脑需要在同一个局域网内。先启动 Spring Boot 服务端，然后在 App 顶部“服务器地址”输入：

```text
http://电脑局域网IP:3000
```

示例：

```text
http://192.168.1.10:3000
```

不要填 `localhost`，因为手机上的 `localhost` 指手机自身，不是电脑。
