# OutSafe - 户外安全建议（前后端分离架构）

基于 Vue 3 + Vite 的前端，以及 Spring Boot 的后端。使用 Open-Meteo 的 Forecast / Archive API 和 Gemini AI 提供风险分析和安全建议。

## 流程概览

1. **选地点**：经纬度 + 可选海拔，时区 `timezone=auto`
2. **后端代理**：前端发送请求至本地的 Spring Boot 后端 `/api/safety/recommend`。
3. **后端计算**：后端获取当天的 Forecast 数据，以及过去 N 年的 Archive 数据，计算窗口聚合、历史百分位和综合风险分。
4. **前端展示与 AI 建议**：前端收到计算结果后渲染风险滑块图，并将结果和用户的活动提示发送至 Gemini 模型获取智能安全建议。

## 本地运行 (需要同时启动前端和后端)

**1. 配置环境**
- 确保已安装 **Node.js** 和 **Java 17+** (且配置了 `JAVA_HOME`)。
- 在项目根目录创建一个 `.env` 文件，并配置你的 Gemini API 密钥：
  ```
  VITE_GEMINI_API_KEY=你的API密钥
  ```

**2. 启动后端 (Spring Boot)**
打开一个新的终端并运行：
```bash
npm run dev:backend
# 或者进入 backend 目录手动运行: cd backend && mvnw spring-boot:run
```
后端将在 `http://localhost:8080` 启动。

**3. 启动前端 (Vue/Vite)**
打开另一个新的终端并运行：
```bash
npm install
npm run dev
```
浏览器打开终端里提示的地址（通常是 `http://localhost:5173`）。前端的请求会自动代理到 `8080` 端口。

## 项目结构

```
src/
  main.js           # 入口
  App.vue           # 主页面：地点选择 + 结果展示
  style.css         # 全局样式
  api/
    weather.js      # Forecast / Archive 请求（Open-Meteo）
  utils/
    risk.js         # 聚合、百分位、综合风险、文案
  components/
    LocationPicker.vue   # 经纬度、海拔、历史年数
    SafetyResult.vue     # 总评、关键原因、与历史对比
```

## 构建

```bash
npm run build
npm run preview   # 预览构建结果
```
