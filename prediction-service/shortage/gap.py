"""
Shortage-gap logic and restock-query emitter — the interface to the depot.

    G(i,m) = max(0, D(i,m) + S(i,m) - X(i,m))

where D = H-day demand forecast, S = safety stock, X = current stock.
When G > tau, a restock query is emitted. This JSON is the ONLY thing
downstream (Layers 2 & 3) consumes, so the model behind D can change freely.
"""

from datetime import datetime, timezone

import numpy as np
import pandas as pd

import config as C


def safety_stock(daily_avg: np.ndarray, days: int = C.SAFETY_STOCK_DAYS) -> np.ndarray:
    return np.ceil(daily_avg * days)


def urgency(gap: float, demand: float, category: str) -> str:
    """Coarse urgency tag; refined later by Layer 2's priority score."""
    cover_ratio = gap / max(demand, 1e-6)
    if category == "chronic" or cover_ratio > 0.75:
        return "high"
    if cover_ratio > 0.4:
        return "medium"
    return "low"


def compute_gaps(snapshot: pd.DataFrame, demand_pred: np.ndarray,
                 tau: float = C.GAP_THRESHOLD_TAU) -> pd.DataFrame:
    """
    snapshot : rows for a single decision date with columns
               [pharmacy_id, medicine_id, category, stock, sold_roll7]
    demand_pred : H-day demand forecast aligned to snapshot rows
    """
    out = snapshot.copy()
    out["demand_h"] = np.round(demand_pred, 1)
    out["safety_stock"] = safety_stock(out["sold_roll7"].to_numpy())
    out["gap"] = np.maximum(0, out["demand_h"] + out["safety_stock"] - out["stock"])
    out["needs_restock"] = out["gap"] > tau
    return out


def emit_queries(gaps: pd.DataFrame, at: datetime | None = None) -> list[dict]:
    at = at or datetime.now(timezone.utc)
    ts = at.isoformat()
    q = []
    for _, r in gaps[gaps["needs_restock"]].iterrows():
        q.append({
            "pharmacy_id": r["pharmacy_id"],
            "medicine_id": r["medicine_id"],
            "quantity": int(np.ceil(r["gap"])),
            "urgency": urgency(r["gap"], r["demand_h"], r["category"]),
            "timestamp": ts,
        })
    return q
