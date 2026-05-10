# 雾霾探测系统

根据实验文档实现的雾霾探测系统，包含 Android 手机端 APK 工程、Spring Boot 服务端和实验报告。

## 项目组成

- `android/`：原生 Android Java 工程，使用手机系统定位功能，可用 Android Studio 打包 APK。
- `maven-server/`：Maven + Spring Boot 服务端，保存定位城市、行政区并提供天气/AQI 接口。
- `docs/report.md`：实验报告。

## 推荐运行方式

先启动 Spring Boot 服务端：

```bash
cd maven-server
mvn spring-boot:run
```

如果要用百度地图根据手机经纬度自动识别城市和区县，启动前配置百度地图 Web 服务端 AK：

```bash
BAIDU_MAP_AK=你的百度地图AK mvn spring-boot:run
```

如果 AK 使用 `sn` 签名校验，同时配置百度控制台里显示的 Security Key：

```bash
BAIDU_MAP_AK=你的百度地图AK BAIDU_MAP_SK=你的百度地图SK mvn spring-boot:run
```

也可以在项目根目录或 `maven-server/` 下创建 `.env`，支持 `BAIDU_MAP_AK` / `BAIDU_MAP_SK`，也支持简写 `ak` / `sk`。配置 AK 后，服务端会先用百度逆地理编码识别城市、区县和行政区划代码，再用百度天气 `weather/v1` 获取天气详情。未配置 AK 或接口不可用时，服务端会使用内置城市坐标和模拟天气兜底。

手机和电脑连接同一 Wi-Fi 后，在 Android App 的“服务器地址”输入：

```text
http://电脑局域网IP:3000
```

然后点击“手机定位”，App 会请求定位权限，读取手机 GPS/网络定位，把百度解析到的城市、区县、行政区划代码和坐标保存到服务端，并展示天气、AQI、污染物和趋势图。位置标题会显示类似 `西安 · 未央区`；刷新天气不会覆盖这个标题，避免区县闪一下又变回城市。

“查询城市”只调用：

```text
GET /api/weather?city=城市名
```

它只查询天气，不会调用 `/api/location`，也不会覆盖手机定位保存的位置。

## 多 App / 多设备共用同一个服务端

服务端支持按客户端 session 隔离定位状态。同一个服务端可以同时给多个 App、多个包名、同一个 App 的多台手机使用，每个客户端保存自己的定位城市和区县，不会互相覆盖。

客户端第一次请求 `/api/location` 或 `/api/weather` 时，如果没有 session，或带来的 session 无效/过期，服务端会生成一个新的随机 UUID session，并通过响应头和 Cookie 发回：

```text
X-Client-Session-Id: 生成的session
Set-Cookie: Haze-Session=生成的session; Max-Age=2592000; Path=/; HttpOnly; SameSite=Lax
```

Android 端会把服务端返回的 session 保存到本机 `SharedPreferences`，后续请求通过 `X-Client-Session-Id` 带回。session 默认 30 天有效，每次有效请求都会续期。服务端按 session 保存到类似 `data/store-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.properties` 的独立文件；旧客户端不带 session 时仍会被分配一个新 session。

## APK 打包

 .\gradlew.bat  assembleRelease

## 已验证

- Spring Boot 服务端 `mvn -DskipTests package` 编译通过。
- `/api/location`、`/api/weather`、`/api/reverse-geocode` 接口验证通过。
- Android 端 Java 文件已按职责拆分，不再把所有逻辑塞进一个文件。
