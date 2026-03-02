<!-- Copilot / AI agent instructions for contributors and automated agents -->
# OutSafe — Copilot Instructions

Purpose: give an AI coding agent the minimal, high-value context to be immediately productive in this repo.

Big picture
- Frontend: Vue 3 + Vite (src/). It currently fetches Open‑Meteo directly and performs aggregation + percentile + scoring in the browser.
- Backend: Spring Boot (backend/). Exposes a single safety API (`/api/safety/recommend`) and a `SafetyService` that currently uses mock data.
- Docs: `docs/BACKEND_API_SPEC.md` defines the API contract the frontend expects — preserve this contract when changing the backend.

Where to look (quick links)
- Frontend entry: src/main.js
- Weather API helpers: src/api/weather.js (Open‑Meteo calls: Forecast + Archive)
- Aggregation & scoring (frontend): src/utils/risk.js
- Main UI: src/App.vue and components/* (LocationPicker.vue, SafetyResult.vue)
- Backend controller: backend/src/main/java/com/outsafe/backend/controller/SafetyController.java
- Backend service logic: backend/src/main/java/com/outsafe/service/SafetyService.java
- Backend models (response/request): backend/src/main/java/com/outsafe/model/*
- API spec: docs/BACKEND_API_SPEC.md

Key conventions & patterns (do not break them)
- Stable JSON response shape: the frontend expects `level`, `score`, `percentiles` (keys: `wind`, `rain`, `cold`), `years_back`, optional `reasons` (array of {key,label,pct}), `comparison_text`, `meta`. See docs/BACKEND_API_SPEC.md for examples.
- Percentiles meaning: higher = more extreme / more risky. For `cold` the code inverts temperature (uses -temp) so that "higher percentile" means "colder-than-usual".
- Aggregation names must match frontend: e.g. `risk_wind`, `risk_rain`, `risk_cold` (see src/utils/risk.js and SafetyService mock).
- Weighting & thresholds live in `SafetyService` (backend) and `src/utils/risk.js` (frontend). Minor numeric drift exists — prefer to change both if adjusting policy.

Build / run (developer workflows)
- Frontend (dev):
  - npm install
  - npm run dev  (Vite serves at http://localhost:5173)
- Frontend (build/preview): `npm run build` / `npm run preview`
- Backend (Windows): from `backend/` run `mvnw.cmd spring-boot:run` to start the Spring service; or `mvnw.cmd package` to build a jar. On non-Windows use `./mvnw`.

API integration notes
- The frontend is implemented to call Open‑Meteo directly. If you switch to backend mode, ensure the backend returns the exact response shape in docs/BACKEND_API_SPEC.md so the UI needs minimal changes.
- Example quick test: GET `/api/safety/recommend?lat=39.9&lon=116.4&date=2026-03-01&years_back=5` — SafetyController supports GET for quick debugging.

Repo quirks & gotchas
- Some Java source files declare package `com.outsafe.backend.model` but live under `.../java/com/outsafe/model/` — be careful when moving files or refactoring packages; keep package declarations and file paths in sync.
- `SafetyService` currently returns mock data (see TODO in file). Replacing mocks should: (1) preserve response keys, (2) keep `percentiles` in 0–100, and (3) keep `reasons` shape.
- README.md still describes a "pure frontend" flow; backend code and docs exist. When changing behavior, update README and docs/BACKEND_API_SPEC.md accordingly.

Editing guidance for an AI agent
- When implementing backend logic replacing mocks, prefer reusing aggregation logic from `src/utils/risk.js` (port algorithms) or call the frontend utilities as reference.
- Keep unit/functional parity: unit names and JSON keys must remain stable to avoid breaking the UI.
- For changes that touch policy (weights, thresholds), update both frontend and backend files and add a short note in `docs/BACKEND_API_SPEC.md`.

If unclear / next steps
- Ask which runtime to use for verification (frontend dev server or backend `mvnw.cmd`). Provide a small test query and a short assertion to validate the returned JSON.

— End of file
