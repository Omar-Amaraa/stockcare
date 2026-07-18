"""
Naive baselines the LightGBM model must beat to justify its existence.

Both predict the H-day forward demand:
  - MovingAverage : H * mean(daily sold over the last `window` days)
  - SeasonalNaive : demand observed in the same H-day window one year ago
"""

import numpy as np
import pandas as pd

import config as C


def moving_average(model_df: pd.DataFrame, window: int = 14, horizon: int = C.HORIZON_H) -> np.ndarray:
    # sold_roll14 already = mean daily sold over last 14 days (shifted by 1)
    col = f"sold_roll{window}"
    if col not in model_df:
        col = "sold_roll14"
    return (model_df[col].to_numpy() * horizon)


def seasonal_naive(panel: pd.DataFrame, model_df: pd.DataFrame, horizon: int = C.HORIZON_H) -> np.ndarray:
    """H-day demand from ~52 weeks ago for the same (pharmacy, medicine)."""
    p = panel.sort_values(["pharmacy_id", "medicine_id", "date"]).copy()
    g = p.groupby(["pharmacy_id", "medicine_id"], sort=False)

    def fwd_sum(s):
        return s[::-1].rolling(horizon, min_periods=horizon).sum()[::-1].shift(-1)
    p["y_hdemand"] = g["demand"].transform(fwd_sum)
    # value from 364 days ago (multiple of 7 -> same weekday alignment)
    p["seasonal"] = g["y_hdemand"].shift(364)

    key = ["pharmacy_id", "medicine_id", "date"]
    merged = model_df[key].merge(p[key + ["seasonal"]], on=key, how="left")
    vals = merged["seasonal"].to_numpy()
    # fall back to moving average where last-year data is missing
    fallback = moving_average(model_df, horizon=horizon)
    return np.where(np.isnan(vals), fallback, vals)
