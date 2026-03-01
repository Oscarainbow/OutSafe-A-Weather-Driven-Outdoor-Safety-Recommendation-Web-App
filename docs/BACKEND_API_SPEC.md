# OutSafe 后端接口规范

前端当前在浏览器内完成：拉 Forecast/Archive、聚合、百分位、综合风险、展示。接入后端后，**前端只负责传参 + 展示**，计算由后端完成。

下面约定：**前端传什么、后端收什么、后端返回什么**，以及两种实现方案（纯数据算法 / AI 模型）的差异。

---

## 一、统一接口：户外安全建议

### 1.1 请求

**方法**：`POST`（推荐，因带 body）或 `GET`（参数 query，适合简单调试）

**路径（示例）**：`/api/safety/recommend` 或 `/api/v1/safety`

**前端传入参数**（后端接收）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `lat` | number | 是 | 纬度，如 39.9 |
| `lon` | number | 是 | 经度，如 116.4 |
| `elevation` | number \| null | 否 | 海拔（米），不传或 null 表示由后端/API 自动 |
| `date` | string | 否 | 查询日期，`YYYY-MM-DD`，不传则用“当天”（建议按请求时的服务端日期或前端传的当天） |
| `years_back` | number | 否 | 对比历史年数（同日），默认 5，范围建议 1～10 |
| `timezone` | string | 否 | 时区，如 `Asia/Shanghai`；不传则后端用 `timezone=auto` 或根据经纬度推断 |

**请求体示例（POST JSON）**：

```json
{
  "lat": 39.9,
  "lon": 116.4,
  "elevation": null,
  "date": "2026-03-01",
  "years_back": 5
}
```

**GET 示例**：  
`/api/safety/recommend?lat=39.9&lon=116.4&date=2026-03-01&years_back=5`

---

### 1.2 响应（统一结构，两种方案都建议遵守）

后端**必须**返回前端当前 UI 依赖的字段，这样前端可以无缝从“纯前端计算”切到“调后端”。

**HTTP**：成功 `200`，业务错误可 `4xx` 并带 `message`。

**响应体（JSON）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `level` | string | 总评等级：`recommended` \| `caution` \| `not_recommended` \| `unknown` |
| `score` | number | 综合风险分 0～100，越高越不推荐 |
| `percentiles` | object | 各指标相对历史同日的百分位 0～100，见下表 |
| `years_back` | number | 实际使用的历史年数 |
| `reasons` | array | 可选，关键原因 Top 2～3，见下表 |
| `comparison_text` | string | 可选，“与过去 N 年相比”的简短文案，前端可不用自己拼 |
| `meta` | object | 可选，如数据来源、模型版本、耗时等 |

**percentiles 结构**（与前端现有命名一致）：

```json
{
  "wind": 92,
  "rain": 15,
  "cold": 85
}
```

- `wind`：风速/阵风在历史同日中的百分位，越高越极端  
- `rain`：降水在历史同日中的百分位，越高越极端  
- `cold`：体感寒冷程度（如体感最低温越低越危险），越高越不推荐（前端用“体感更冷”等文案）

**reasons 结构**（可选，若后端不返，前端可用 percentiles 自己排 Top 2～3）：

```json
[
  { "key": "wind", "label": "风", "pct": 92 },
  { "key": "cold", "label": "体感寒冷", "pct": 85 }
]
```

**完整响应示例**：

```json
{
  "level": "not_recommended",
  "score": 78,
  "percentiles": { "wind": 92, "rain": 15, "cold": 85 },
  "years_back": 5,
  "reasons": [
    { "key": "wind", "label": "风", "pct": 92 },
    { "key": "cold", "label": "体感寒冷", "pct": 85 }
  ],
  "comparison_text": "与过去 5 年同日相比：风更极端（92%），体感更冷（85%）。",
  "meta": { "source": "algorithm", "latency_ms": 120 }
}
```

错误时建议统一格式，例如：

```json
{
  "error": true,
  "message": "无效的经纬度或日期",
  "code": "INVALID_INPUT"
}
```

---

## 二、方案 A：纯数据 + 数据库 + 算法

### 2.1 思路

- 后端维护**历史天气数据库**（例如按网格或站点、按日或小时存储）。
- “当天/目标日”的天气可由：**实时调 Open-Meteo Forecast**，或从自己的缓存/表中取。
- 历史同日数据从**数据库**按 `(位置, 日期)` 查询，不再每次调 Archive API。
- 后端用与前端一致的**聚合 + 百分位 + 综合风险**算法（或更复杂的规则）算出 `level`、`score`、`percentiles`，再拼 `reasons`、`comparison_text`。

### 2.2 后端接收（与统一接口一致）

- 接收：`lat`, `lon`, `elevation?`, `date?`, `years_back?`, `timezone?`。
- 不要求前端传原始小时数据；由后端自己取“当日”与“历史同日”数据。

### 2.3 后端内部数据流

1. **今日/目标日数据**  
   - 若 DB 有当天的预报或实况：从 DB 读；否则调 Open-Meteo Forecast，可选落库。
2. **历史同日数据**  
   - 从 DB 查 `date` 对应过去 `years_back` 年的同月同日（如 2026-03-01 → 2025-03-01, 2024-03-01, …）。
3. **聚合**  
   - 对“当日”和“每个历史年”的 hourly 做与前端一致的窗口聚合（如日最高温、日最大阵风、日体感最低、日降水总和等），得到 `today_metrics` 和 `history_metrics[]`。
4. **百分位**  
   - 用 `today_metrics` 在 `history_metrics` 中算各指标百分位（风、降水、体感冷等），得到 `percentiles`。
5. **综合风险**  
   - 由 `percentiles` 加权得到 `score`，再根据阈值得到 `level`（如 score≥70 → not_recommended）。
6. **输出**  
   - 按 1.2 节格式返回 `level`, `score`, `percentiles`, `years_back`, 以及可选的 `reasons`, `comparison_text`, `meta`。

### 2.4 数据库建议（存“前面几年的天气”）

- **表设计（示例）**  
  - 按“网格点”或“站点”：`(lat, lon)` 或 `(grid_id)`, `date`（日期）, 可选 `elevation`。  
  - 存聚合后的日指标即可，减少体积；若需更细可存小时级。
- **字段示例（日聚合）**  
  - `date`, `lat`, `lon`, `temp_max`, `temp_min`, `apparent_min`, `humidity_max`, `precip_sum`, `wind_max`, `gust_max`, `risk_cold`, `risk_wind`, `risk_rain` 等（与前端 `aggregateDayMetrics` 对齐）。
- **历史数据从哪来**  
  - 离线用 Open-Meteo Archive（或其它数据源）按区域、日期批量拉取，写入 DB；后续只查 DB，不再实时调 Archive。

这样前端只需调用一个接口，传位置和日期等，后端返回统一响应。

---

## 三、方案 B：AI 模型分析

### 3.1 思路

- 后端仍然需要“当日”和“历史同日”的天气数据（可从 DB 或实时调 API）。
- 把**结构化输入**（如当日 + 历史聚合指标、或小时序列）交给 **AI 模型**（如 LLM 或小模型），由模型输出：总评、风险分、关键原因、与历史对比的文案等。

### 3.2 后端接收（与统一接口一致）

- 与方案 A 相同：`lat`, `lon`, `elevation?`, `date?`, `years_back?`, `timezone?`。  
- 前端不传原始小时数据；后端自己取数据再喂给模型。

### 3.3 后端给模型的结构化输入（建议）

为便于模型稳定输出，建议至少包含：

- **位置与时间**：`lat`, `lon`, `date`, `years_back`。
- **当日聚合指标**：如 `temp_max`, `temp_min`, `apparent_min`, `precip_sum`, `wind_max`, `gust_max`, `humidity_max` 等（与前端/方案 A 一致）。
- **历史同日统计**：如过去 N 年同日的各指标 min/max/avg 或分位数，便于模型做“与历史对比”的表述。

可选：若模型支持长序列，可再提供当日或历史的小时序列（温度、体感、风、降水等）。

### 3.4 模型输出 → 统一响应格式

- 模型输出需要**映射到 1.2 节的统一响应**，以便前端不改 UI 逻辑：
  - `level`：从模型文案或分类中映射到 `recommended` / `caution` / `not_recommended` / `unknown`。
  - `score`：模型若给出 0～100 分可直接用；若只给等级，可按区间赋默认分（如 not_recommended→75）。
  - `percentiles`：若模型直接输出百分位则填入；若只输出自然语言，可用 NER/规则或小模型从文案里抽，或部分用算法补算（如历史 DB 算百分位，模型只补文案）。
  - `reasons`：从模型生成的“关键原因”列表映射为 `{ key, label, pct }[]`（若模型只给文案，可固定 key/label，pct 从 percentiles 取）。
  - `comparison_text`：直接用模型生成的“与过去 N 年相比”的句子。

这样前端仍然只认一个响应结构，方案 B 只是后端实现从“规则算法”换成“AI 生成 + 结构化映射”。

---

## 四、前端对接方式小结

| 项目 | 说明 |
|------|------|
| **调谁** | 后端统一接口，例如 `POST /api/safety/recommend`（或 GET + query）。 |
| **传什么** | `lat`, `lon`, 可选 `elevation`, `date`, `years_back`, `timezone`。 |
| **收什么** | `level`, `score`, `percentiles`, `years_back`；可选 `reasons`, `comparison_text`, `meta`。 |
| **方案 A** | 后端用 DB 存历史天气 + 与前端一致的聚合/百分位/风险算法，返回上述结构。 |
| **方案 B** | 后端用 DB 或 API 取数据，经 AI 模型生成结果，再映射成同一响应结构。 |

前端在“后端模式”下：用户选点后只发一次请求，把当前表单（经纬度、海拔、日期、历史年数）传给该接口，用返回的 `level`、`score`、`percentiles`、`years_back` 以及可选的 `reasons`、`comparison_text` 渲染现有 UI，无需再调 Open-Meteo。
