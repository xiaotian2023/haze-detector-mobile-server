# 安卓 APK 工程

这是原生 Android 手机端工程，使用 Java 编写，定位功能调用手机系统 `LocationManager`，不依赖浏览器定位。

## 功能

- 运行时申请 `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`。
- 通过手机 GPS 或网络定位获取经纬度。
- 根据经纬度匹配最近城市，并把城市和坐标提交到服务端保存。
- 从服务端读取天气、AQI、污染物和趋势数据。
- 服务端不可用时使用本机模拟数据，保证离线演示。

## 打包 APK

当前运行环境没有 Android SDK / Gradle / ADB，所以这里不能直接生成 `.apk`。在本机用 Android Studio 打包：

1. 打开 Android Studio。
2. 选择 `Open`，打开本目录 `android/`。
3. 等待 Gradle 同步完成。
4. 选择 `Build > Build Bundle(s) / APK(s) > Build APK(s)`。
5. APK 输出路径通常为 `android/app/build/outputs/apk/debug/app-debug.apk`。

项目已内置 `app/debug.keystore` 作为演示签名证书，`debug` 和 `release` APK 都会默认使用它签名，避免打包时报 `Missing keystore`。如果要发布到应用商店，请在 Android Studio 中创建自己的发布 keystore，并替换 `app/build.gradle` 里的 `release` 签名配置。

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
