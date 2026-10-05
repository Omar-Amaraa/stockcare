"""
Serving layer for the StockCare demand-forecasting engine.

Loads the trained forecaster once at startup and scores *live* pharmacy inventory
snapshots coming from the Spring backend. It reuses the exact training feature
pipeline (features.build) for column order and categorical encodings, and the exact
shortage logic (shortage.gap) so the forecast the depot sees is the model's forecast.

Because the live backend does not (yet) stream full daily sales history, the lag /
rolling features are approximated from the pharmacy's reported average daily
consumption. Region, category, cold-chain and the calendar/epidemiological signals
are real. This is the documented ERP-integration approximation; when real POS history
is available the same endpoint accepts richer features without any contract change.
"""

from __future__ import annotations

import math
import sys
from datetime import date, datetime, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

import config as C
from features.build import build, LAGS, ROLL
from shortage.gap import safety_stock, urgency as gap_urgency

try:
    import lightgbm as lgb
    HAS_LGB = True
except Exception:  # pragma: no cover
    HAS_LGB = False

from models.train import Forecaster, train_model, CATEGORICAL


# ----------------------------------------------------------------------------
# Model loading
# ----------------------------------------------------------------------------

def load_forecaster() -> Forecaster:
    """
    Prefer loading the pre-trained booster from artifacts/model.txt (fast, reproducible).
    Rebuild the categorical maps and feature order from the panel so encodings match
    exactly. Fall back to retraining (LightGBM or NumPy-GBT) if the booster is absent.
    """
    panel_path = C.ARTIFACTS / "panel.csv"
    model_path = C.ARTIFACTS / "model.txt"
    panel = pd.read_csv(panel_path, parse_dates=["date"])
    model_df, feature_cols = build(panel)

    maps = {c: list(model_df[c].astype("category").cat.categories) for c in CATEGORICAL}

    if HAS_LGB and model_path.exists():
        booster = lgb.Booster(model_file=str(model_path))
        best_iter = booster.best_iteration or None
        return Forecaster("lightgbm", booster, feature_cols, maps, best_iter)

    # Fallback: retrain end-to-end (keeps the service runnable without the booster).
    fc, _, _, _ = train_model(model_df, feature_cols)
    return fc


# ----------------------------------------------------------------------------
# Live feature synthesis
# ----------------------------------------------------------------------------

def _is_exam(d: date) -> int:
    for (m1, d1), (m2, d2) in C.EXAM_PERIODS:
        start = (m1, d1)
        end = (m2, d2)
        cur = (d.month, d.day)
        if start <= cur <= end:
            return 1
    return 0


def _flu_index(d: date) -> float:
    """Smooth seasonal curve in [0,1] peaking mid-January (winter flu season)."""
    doy = d.timetuple().tm_yday
    peak = 15  # ~15 Jan
    val = 0.5 * (1 + math.cos(2 * math.pi * (doy - peak) / 365.0))
    return round(float(val), 4)


def _heatwave(d: date) -> int:
    return 1 if d.month in (6, 7, 8) else 0


def _season(month: int) -> int:
    return month % 12 // 3  # 0 winter .. 3 autumn


def _feature_row(item: dict, d: date) -> dict:
    """Build a single model feature row from a live inventory snapshot."""
    avg = float(item.get("averageDailyConsumption") or 0.0)
    stock = float(item.get("currentStock") or 0.0)
    row = {
        "stock": stock,
        "is_exam": _is_exam(d),
        "heatwave": _heatwave(d),
        "flu_index": _flu_index(d),
        "dow": d.weekday(),
        "month": d.month,
        "weekofyear": int(d.isocalendar().week),
        "season": _season(d.month),
        "cold_chain": 1 if item.get("coldChain") else 0,
        "stockout_roll14": 0.0,
        "region": item.get("region") or "",
        "category": _map_category(item.get("category")),
        "medicine_id": item.get("modelMedicineId") or item.get("medicationId") or "",
    }
    # Approximate lag / rolling demand from the reported average daily consumption.
    for l in LAGS:
        row[f"sold_lag{l}"] = avg
    for w in ROLL:
        row[f"sold_roll{w}"] = avg
    return row


# Map free-form catalogue categories onto the model's coarse classes when possible.
_CATEGORY_MAP = {
    "antidiabetic": "chronic", "antihypertensive": "chronic", "thyroid": "chronic",
    "statin": "chronic", "anticoagulant": "chronic", "antiplatelet": "chronic",
    "antibiotic": "essential", "bronchodilator": "essential", "vaccine": "essential",
    "rehydration": "essential", "analgesic": "essential", "opioid analgesic": "essential",
    "nsaid": "comfort", "ppi": "comfort", "antihistamine": "comfort",
    "anxiolytic": "comfort", "diuretic": "essential", "corticosteroid": "essential",
}


def _map_category(cat: str | None) -> str:
    if not cat:
        return "essential"
    key = cat.strip().lower()
    return _CATEGORY_MAP.get(key, cat if cat in ("chronic", "essential", "comfort") else "essential")


# ----------------------------------------------------------------------------
# Prediction
# ----------------------------------------------------------------------------

class PredictionEngine:
    def __init__(self):
        self.fc = load_forecaster()
        self.horizon = C.HORIZON_H

    @property
    def backend(self) -> str:
        return self.fc.backend

    @property
    def model_version(self) -> str:
        return f"{self.fc.backend}-poisson-H{self.horizon}"

    def predict(self, items: list[dict], decision_date: date, horizon: int | None = None) -> list[dict]:
        if not items:
            return []
        h = horizon or self.horizon
        rows = [_feature_row(it, decision_date) for it in items]
        df = pd.DataFrame(rows)
        demand_h = self.fc.predict(df)

        results = []
        for it, dem in zip(items, demand_h):
            avg = float(it.get("averageDailyConsumption") or 0.0)
            stock = float(it.get("currentStock") or 0.0)
            safety = float(safety_stock(np.array([avg]))[0])
            gap = max(0.0, float(dem) + safety - stock)
            if gap <= C.GAP_THRESHOLD_TAU:
                continue
            remaining = int(math.floor(stock / avg)) if avg > 0 else None
            days_to_safety = max(0.0, (stock - safety) / avg) if avg > 0 else 0.0
            shortage_date = decision_date + timedelta(days=int(math.floor(days_to_safety)))
            cat = _map_category(it.get("category"))
            results.append({
                "medicationId": it.get("medicationId"),
                "currentStock": int(stock),
                "predictedShortageDate": shortage_date.isoformat(),
                "estimatedRemainingDays": remaining,
                "predictedMissingQuantity": int(math.ceil(gap)),
                "demandForecast": round(float(dem), 1),
                "confidence": None,
                "urgency": gap_urgency(gap, float(dem), cat),
                "reason": (f"[{self.model_version}] H{h}-day demand forecast {dem:.0f} "
                           f"+ safety {safety:.0f} - stock {stock:.0f} = shortfall {math.ceil(gap)} "
                           f"units by {shortage_date.isoformat()}."),
                "modelVersion": self.model_version,
            })
        return results
