"""
StockCare Prediction Service (Layer 1) — HTTP wrapper around the demand-forecasting engine.

Exposes the trained LightGBM (or NumPy-GBT fallback) model behind a small REST API so the
Spring backend can request shortage predictions in `external` mode. The model, features and
shortage-gap logic are unchanged from the offline pipeline; this only adds a serving surface.
"""

from __future__ import annotations

import json
from datetime import date, datetime
from pathlib import Path
from typing import Optional

from fastapi import FastAPI
from pydantic import BaseModel, Field

from serving import PredictionEngine, ROOT
import config as C

app = FastAPI(title="StockCare Prediction Service", version="1.0.0")
engine: Optional[PredictionEngine] = None


class InventorySnapshot(BaseModel):
    medicationId: str
    region: Optional[str] = None
    category: Optional[str] = None
    coldChain: bool = False
    currentStock: float = 0
    averageDailyConsumption: float = 0
    modelMedicineId: Optional[str] = None


class PredictRequest(BaseModel):
    decisionDate: Optional[str] = Field(None, description="ISO date/datetime of the decision point")
    horizonDays: Optional[int] = None
    items: list[InventorySnapshot] = []


@app.on_event("startup")
def _startup() -> None:
    global engine
    engine = PredictionEngine()


@app.get("/health")
def health() -> dict:
    ok = engine is not None
    return {
        "status": "UP" if ok else "STARTING",
        "backend": engine.backend if ok else None,
        "modelVersion": engine.model_version if ok else None,
    }


@app.get("/info")
def info() -> dict:
    metrics_path = C.ARTIFACTS / "metrics.json"
    metrics = json.loads(metrics_path.read_text()) if metrics_path.exists() else {}
    return {
        "service": "stockcare-prediction",
        "backend": engine.backend if engine else None,
        "modelVersion": engine.model_version if engine else None,
        "horizonDays": C.HORIZON_H,
        "safetyStockDays": C.SAFETY_STOCK_DAYS,
        "gapThreshold": C.GAP_THRESHOLD_TAU,
        "simulated": False,
        "trainedOn": "synthetic Tunisian pharmacy panel (2y)",
        "metrics": metrics,
    }


def _parse_date(value: Optional[str]) -> date:
    if not value:
        return datetime.utcnow().date()
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).date()
    except ValueError:
        return date.fromisoformat(value[:10])


@app.post("/predict")
def predict(req: PredictRequest) -> dict:
    assert engine is not None, "engine not initialised"
    d = _parse_date(req.decisionDate)
    items = [it.model_dump() for it in req.items]
    predictions = engine.predict(items, d, req.horizonDays)
    return {
        "modelVersion": engine.model_version,
        "backend": engine.backend,
        "simulated": False,
        "horizonDays": req.horizonDays or C.HORIZON_H,
        "decisionDate": d.isoformat(),
        "predictions": predictions,
    }
