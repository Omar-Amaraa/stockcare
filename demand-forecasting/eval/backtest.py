"""
Evaluation & backtest.

Two families of metrics:
  1. Forecast accuracy  : WMAPE, MAE, bias — LightGBM vs the two baselines.
  2. Business impact     : simulated stockouts avoided by a forecast-driven
                           reorder policy vs the reactive (s,S) baseline.

All accuracy metrics are computed ONLY on the held-out validation period so
they reflect genuine out-of-sample performance.
"""

import numpy as np
import pandas as pd


def wmape(y, yhat) -> float:
    y, yhat = np.asarray(y), np.asarray(yhat)
    denom = np.abs(y).sum()
    return float(np.abs(y - yhat).sum() / denom) if denom else float("nan")


def mae(y, yhat) -> float:
    return float(np.mean(np.abs(np.asarray(y) - np.asarray(yhat))))


def bias(y, yhat) -> float:
    return float(np.mean(np.asarray(yhat) - np.asarray(y)))


def accuracy_report(y, preds: dict[str, np.ndarray]) -> dict:
    return {
        name: {"WMAPE": round(wmape(y, p), 4),
               "MAE": round(mae(y, p), 3),
               "bias": round(bias(y, p), 3)}
        for name, p in preds.items()
    }


def stockouts_avoided(valid: pd.DataFrame, demand_pred: np.ndarray,
                      horizon: int, safety_days: int) -> dict:
    """
    Replay the validation period twice per (pharmacy, medicine):
      - reactive  : the stockouts already simulated in the panel (column `stockout`)
      - forecast  : order up to (H-day demand forecast + safety stock) at each step

    Returns stockout-day counts for both and the % reduction.
    """
    v = valid.copy()
    v["demand_pred"] = demand_pred
    reactive_so = int(v["stockout"].sum())

    forecast_so = 0
    for (_, _), grp in v.groupby(["pharmacy_id", "medicine_id"], sort=False, observed=True):
        grp = grp.sort_values("date")
        avg = grp["sold_roll7"].to_numpy()
        pred = grp["demand_pred"].to_numpy()
        demand = grp["demand"].to_numpy()
        target = pred + np.ceil(avg * safety_days)     # order-up-to level
        stock = target[0] if len(target) else 0
        lead, pipeline = 3, {}
        for t in range(len(demand)):
            stock += pipeline.pop(t, 0)
            d = demand[t]
            served = min(d, stock)
            stock -= served
            if d > served:
                forecast_so += 1
            # daily review: top back up to the forecast-driven target
            if stock < target[t] and not any(a > t for a in pipeline):
                pipeline[t + lead] = max(0, target[t] - stock)

    reduction = 1 - forecast_so / reactive_so if reactive_so else 0.0
    return {
        "reactive_stockout_days": reactive_so,
        "forecast_stockout_days": forecast_so,
        "reduction_pct": round(100 * reduction, 1),
    }
