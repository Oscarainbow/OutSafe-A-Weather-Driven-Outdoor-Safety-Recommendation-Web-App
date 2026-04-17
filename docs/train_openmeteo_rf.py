#!/usr/bin/env python
# coding: utf-8

# In[5]:


import json
from dataclasses import dataclass
from typing import Dict, List, Any

import numpy as np
import pandas as pd
import requests
from sklearn.ensemble import RandomForestRegressor

HISTORICAL_URL = "https://archive-api.open-meteo.com/v1/archive"

HOURLY_VARS = [
    "temperature_2m",
    "apparent_temperature",
    "relative_humidity_2m",
    "precipitation",
    "windspeed_10m",
    "windgusts_10m",
]


@dataclass
class TrainConfig:
    latitude: float
    longitude: float
    start_date: str
    end_date: str
    timezone: str = "auto"
    elevation: float | None = None
    n_estimators: int = 200
    max_depth: int = 10
    random_state: int = 42


def fetch_openmeteo_history(cfg: TrainConfig) -> Dict[str, Any]:
    params = {
        "latitude": cfg.latitude,
        "longitude": cfg.longitude,
        "start_date": cfg.start_date,
        "end_date": cfg.end_date,
        "hourly": ",".join(HOURLY_VARS),
        "timezone": cfg.timezone,
    }
    if cfg.elevation is not None:
        params["elevation"] = cfg.elevation

    r = requests.get(HISTORICAL_URL, params=params, timeout=60)
    r.raise_for_status()
    return r.json()


def aggregate_day_metrics(hourly: Dict[str, List[Any]], date: str) -> Dict[str, float] | None:
    times = hourly.get("time", [])
    if not times:
        return None

    idx = [i for i, t in enumerate(times) if str(t).startswith(date)]
    if not idx:
        return None

    def pick(name: str) -> List[float]:
        arr = hourly.get(name, [])
        return [arr[i] for i in idx if i < len(arr) and arr[i] is not None]

    temp = pick("temperature_2m")
    apparent = pick("apparent_temperature")
    humidity = pick("relative_humidity_2m")
    precip = pick("precipitation")
    wind = pick("windspeed_10m")
    gust = pick("windgusts_10m")

    def safe_max(x: List[float]) -> float | None:
        return max(x) if x else None

    def safe_min(x: List[float]) -> float | None:
        return min(x) if x else None

    def safe_sum(x: List[float]) -> float | None:
        return float(sum(x)) if x else None

    metrics = {
        "temp_max": safe_max(temp),
        "temp_min": safe_min(temp),
        "apparent_min": safe_min(apparent),
        "humidity_max": safe_max(humidity),
        "precip_sum": safe_sum(precip),
        "wind_max": safe_max(wind),
        "gust_max": safe_max(gust),
    }

    if any(v is None for v in metrics.values()):
        return None
    return metrics


def build_daily_dataframe(hourly: Dict[str, List[Any]]) -> pd.DataFrame:
    times = hourly.get("time", [])
    dates = sorted({str(t)[:10] for t in times})
    rows = []

    for d in dates:
        row = aggregate_day_metrics(hourly, d)
        if row:
            row["date"] = d
            rows.append(row)

    df = pd.DataFrame(rows).sort_values("date").reset_index(drop=True)
    return df


def make_supervised(df: pd.DataFrame) -> pd.DataFrame:
    target_cols = [
        "temp_max",
        "temp_min",
        "apparent_min",
        "humidity_max",
        "precip_sum",
        "wind_max",
        "gust_max",
    ]

    out = df.copy()
    for col in target_cols:
        out[f"next_{col}"] = out[col].shift(-1)

    out = out.dropna().reset_index(drop=True)
    return out


def export_forest(model: RandomForestRegressor, feature_names: List[str]) -> Dict[str, Any]:
    forest = {
        "feature_names": feature_names,
        "trees": [],
    }

    for estimator in model.estimators_:
        tree = estimator.tree_
        forest["trees"].append({
            "children_left": tree.children_left.tolist(),
            "children_right": tree.children_right.tolist(),
            "feature": tree.feature.tolist(),
            "threshold": tree.threshold.tolist(),
            "value": tree.value.reshape(-1).tolist(),
        })

    return forest


def train_and_export(cfg: TrainConfig, output_json: str = "rf_weather_24h.json") -> None:
    raw = fetch_openmeteo_history(cfg)
    hourly = raw["hourly"]

    daily = build_daily_dataframe(hourly)
    supervised = make_supervised(daily)

    feature_cols = [
        "temp_max",
        "temp_min",
        "apparent_min",
        "humidity_max",
        "precip_sum",
        "wind_max",
        "gust_max",
    ]

    target_cols = [
        "next_temp_max",
        "next_temp_min",
        "next_apparent_min",
        "next_humidity_max",
        "next_precip_sum",
        "next_wind_max",
        "next_gust_max",
    ]

    export_payload = {
        "feature_names": feature_cols,
        "models": {},
        "metadata": {
            "latitude": cfg.latitude,
            "longitude": cfg.longitude,
            "start_date": cfg.start_date,
            "end_date": cfg.end_date,
            "n_rows": int(len(supervised)),
        },
    }

    for target in target_cols:
        model = RandomForestRegressor(
            n_estimators=cfg.n_estimators,
            max_depth=cfg.max_depth,
            random_state=cfg.random_state,
        )
        model.fit(supervised[feature_cols], supervised[target])
        export_payload["models"][target] = export_forest(model, feature_cols)

    with open(output_json, "w", encoding="utf-8") as f:
        json.dump(export_payload, f)

    print(f"Saved model to {output_json}")
    print(f"Training rows: {len(supervised)}")


if __name__ == "__main__":
    cfg = TrainConfig(
        latitude=41.4993,
        longitude=-81.6944,
        start_date="2021-01-01",
        end_date="2025-12-31",
        timezone="auto",
        elevation=None,
        n_estimators=200,
        max_depth=10,
        random_state=42,
    )
    train_and_export(cfg, output_json="rf_weather_24h.json")


# In[ ]:





# In[ ]:




