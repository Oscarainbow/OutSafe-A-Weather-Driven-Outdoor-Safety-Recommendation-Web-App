# OutSafe - Weather-Driven Outdoor Safety Recommendation

OutSafe is a full-stack web application designed to provide users with weather-driven safety recommendations for outdoor activities. 
It features a **Vue 3 + Vite** frontend and a **Java Spring Boot** backend, leveraging the **Open-Meteo API** for real-time and historical weather data, and the **Gemini 2.5 Flash AI** to generate human-friendly, concise safety advice.

## System Architecture & Workflow

1. **Location Selection**: The user selects a location (latitude, longitude) and optionally elevation. The timezone is automatically detected (`timezone=auto`).
2. **Backend Proxy**: The frontend sends the request parameters to the local Spring Boot backend via the `/api/safety/recommend` endpoint.
3. **Backend Processing**: 
   - The backend fetches the forecast data for the selected day and the historical archive data for the same day over the past `N` years using the Open-Meteo API.
   - It performs window aggregations, calculates historical percentiles, and computes a composite risk score (e.g., max wind gusts, daily precipitation, minimum apparent temperature).
   - It also generates detailed quartile metrics (min, max, median, IQR) for visual comparison.
4. **Frontend Visualization & AI Advice**: 
   - The frontend receives the computed results and renders an intuitive "TODAY VS PAST" risk diagram.
   - The raw weather data, historical comparisons, and user's planned activity are sent to the Gemini AI model.
   - Gemini returns a personalized, easy-to-understand safety recommendation in Markdown format.

## Prerequisites

Ensure you have the following installed on your system:
- **Node.js** (for running the Vue frontend)
- **Java 17 or higher** (for running the Spring Boot backend)
- Ensure the `JAVA_HOME` environment variable is correctly configured on your machine.

## Local Development (Running the App)

To run the application, you need to start **both** the backend and frontend servers simultaneously in separate terminal windows.

### 1. Environment Setup
Create a `.env` file in the root directory of the project and configure your Google Gemini API key:
```env
VITE_GEMINI_API_KEY=your_gemini_api_key_here
```

### 2. Start the Backend (Spring Boot)
Open a new terminal in the project root and run:
```bash
npm run dev:backend
```
*(Alternatively, navigate to the `backend` directory and run: `mvnw spring-boot:run`)*

The backend server will start on `http://localhost:8081`.

### 3. Start the Frontend (Vue/Vite)
Open a second terminal in the project root, install the dependencies, and start the Vite dev server:
```bash
npm install
npm run dev
```
Open your browser and navigate to the URL shown in the terminal (usually `http://localhost:5173`). All frontend requests to `/api` are automatically proxied to the backend running on port 8081.

## Project Structure

```text
OutSafe/
├── backend/                   # Spring Boot Java Backend
│   ├── src/main/java/...      # Controllers, Services, and Models
│   ├── pom.xml                # Maven dependencies
│   └── mvnw                   # Maven wrapper
├── src/                       # Vue 3 Frontend
│   ├── api/                   # API integration (AI fetch logic)
│   ├── components/            # Vue components (LocationPicker, RiskDiagram, SafetyResult)
│   ├── utils/                 # Utility functions
│   ├── App.vue                # Main application view
│   └── main.js                # Frontend entry point
├── .env                       # Environment variables (e.g., API keys)
├── package.json               # NPM scripts and dependencies
└── vite.config.js             # Vite configuration and proxy settings
```