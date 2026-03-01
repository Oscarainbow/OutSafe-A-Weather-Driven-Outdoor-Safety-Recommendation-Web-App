# OutSafe - 户外安全建议（纯前端）

基于 Vue 3 + Vite，无需后端即可运行。使用 Open-Meteo 的 Forecast / Archive API（无需 API Key）。

## 流程概览

1. **选地点**：经纬度 + 可选海拔，时区 `timezone=auto`
2. **Forecast API**：拉取今天/未来小时数据
3. **Archive API**：拉取过去 N 年「同日」小时数据
4. **窗口聚合**：对「今天」和每个历史年做单日指标聚合
5. **计算**：各指标历史百分位、综合风险分
6. **UI**：总评（不建议/谨慎/推荐）、关键原因 Top 2~3、「与过去 N 年相比」文案

## 本地运行

```bash
cd OutSafe-A-Weather-Driven-Outdoor-Safety-Recommendation-Web-App
npm install
npm run dev
```

浏览器打开终端里提示的地址（通常是 http://localhost:5173）。

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
