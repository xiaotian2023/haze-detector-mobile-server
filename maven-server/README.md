# Spring Boot 服务端

这是雾霾探测系统服务端，使用 Maven + Spring Boot 管理，供 Android APK 调用。

## 运行

```bash
mvn spring-boot:run
```

默认监听：

```text
http://0.0.0.0:3000
```

如需修改端口：

```bash
PORT=8080 mvn spring-boot:run
```

如需让定位根据经纬度自动解析真实城市，请配置百度地图 Web 服务端 AK：

```bash
BAIDU_MAP_AK=你的百度地图AK mvn spring-boot:run
```

如果该 AK 在百度控制台配置为 `sn` 签名校验，还需要同时配置 Security Key：

```bash
BAIDU_MAP_AK=你的百度地图AK BAIDU_MAP_SK=你的百度地图SK mvn spring-boot:run
```

服务端也会自动读取项目根目录或 `maven-server/` 目录下的 `.env` 文件，支持以下两种写法：

```env
BAIDU_MAP_AK=你的百度地图AK
BAIDU_MAP_SK=你的百度地图SK
```

```env
ak=你的百度地图AK
sk=你的百度地图SK
```

服务端会调用百度逆地理编码接口，根据手机上传的 WGS84 经纬度读取 `addressComponent.city`、`addressComponent.district` 和 `adcode`。天气详情会使用百度天气接口：

```text
https://api.map.baidu.com/weather/v1/?district_id=行政区划代码&data_type=all&ak=你的AK
```

如果没有配置 AK、没有定位到行政区划代码，或百度接口不可用，会回退到内置模拟天气和最近城市匹配。百度接口返回 `text/javascript;charset=utf-8`，服务端按字符串读取后再用 Jackson 解析，避免 `RestTemplate` 因 Content-Type 报错。

## 打包

```bash
mvn -DskipTests package
java -jar target/haze-detector-server-1.0.0.jar
```

## 接口

- `GET /api/cities`：城市列表。
- `GET /api/location`：读取服务器端保存的定位城市、区县、坐标和行政区划代码。
- `POST /api/location`：保存定位坐标，并优先通过百度地图解析城市、区县和行政区划代码。
- `GET /api/reverse-geocode?lat=31.2&lon=121.5`：根据经纬度解析城市、区县、展示名称和行政区划代码。
- `GET /api/weather?city=北京`：获取天气、AQI、污染物和趋势数据。手动查询城市只调用这个接口，不会覆盖已保存的定位位置。

## 多 App / 多设备共用

同一个服务端可以给多个 APK、多个前端 App 或同一个 APK 的多台手机使用。服务端不使用包名作为唯一身份，而是发放随机 session，并按 session 保存独立定位状态。

客户端请求 `/api/location` 或 `/api/weather` 时，如果没有带 session，或 session 已过期/无效，服务端会创建新 session，并在响应中返回：

```text
X-Client-Session-Id: 生成的session
Set-Cookie: Haze-Session=生成的session; Max-Age=2592000; Path=/; HttpOnly; SameSite=Lax
```

App 客户端后续请求带请求头：

```text
X-Client-Session-Id: 已保存的session
```

浏览器或支持 Cookie 的客户端可以直接使用 Cookie：

```text
Cookie: Haze-Session=已保存的session
```

session 默认 30 天有效，每次有效请求都会续期。定位数据保存到 `data/store-{session}.properties`，session 过期或伪造时服务端会重新创建一个新的 session。

## 分层结构

```text
controller/   REST API
service/      定位保存、天气与 AQI 业务逻辑
repository/   城市坐标仓库
model/        请求和响应 DTO
```
