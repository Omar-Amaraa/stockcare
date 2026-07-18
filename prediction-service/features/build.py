"""
Feature pipeline.

Turns the daily (pharmacy, medicine, date) panel into a supervised learning
table where each row is a *forecast decision point*:

    X = features known at day t   ->   y = total demand over days (t+1 .. t+H)

The target is the H-day forward demand, because that is exactly what the
shortage-gap logic compares against current stock.
"""

import numpy as np
import pandas as pd

import config as C

LAGS = [1, 7, 14, 30]
ROLL = [7, 14, 30]

CATEGORICAL = ["region", "category", "medicine_id"]


def build(panel: pd.DataFrame, horizon: int = C.HORIZON_H):
    panel = (panel.sort_values(["pharmacy_id", "medicine_id", "date"])
                  .reset_index(drop=True).copy())

    # Positional group slices — avoids pandas groupby-transform index ambiguity
    # and is fully deterministic per (pharmacy, medicine) series.
    groups = list(panel.groupby(["pharmacy_id", "medicine_id"], sort=False).indices.values())

    for l in LAGS:
        panel[f"sold_lag{l}"] = np.nan
    for w in ROLL:
        panel[f"sold_roll{w}"] = np.nan
    panel["y_hdemand"] = np.nan
    panel["stockout_roll14"] = np.nan

    sold, demand, stockout = panel["sold"], panel["demand"], panel["stockout"]
    loc = panel.columns.get_loc

    for idx in groups:
        s_sold = sold.iloc[idx]
        s_dem = demand.iloc[idx]
        s_so = stockout.iloc[idx]

        # target: sum of demand over the next H days (t+1 .. t+H)
        fwd = s_dem[::-1].rolling(horizon, min_periods=horizon).sum()[::-1].shift(-1)
        panel.iloc[idx, loc("y_hdemand")] = fwd.to_numpy()

        for l in LAGS:
            panel.iloc[idx, loc(f"sold_lag{l}")] = s_sold.shift(l).to_numpy()
        for w in ROLL:
            panel.iloc[idx, loc(f"sold_roll{w}")] = s_sold.shift(1).rolling(w).mean().to_numpy()
        panel.iloc[idx, loc("stockout_roll14")] = s_so.shift(1).rolling(14).mean().to_numpy()

    # ---- calendar features ---------------------------------------------------
    d = panel["date"].dt
    panel["dow"] = d.dayofweek
    panel["month"] = d.month
    panel["weekofyear"] = d.isocalendar().week.astype(int)
    panel["season"] = (d.month % 12 // 3)          # 0 winter .. 3 autumn

    feature_cols = (
        [f"sold_lag{l}" for l in LAGS]
        + [f"sold_roll{w}" for w in ROLL]
        + ["stockout_roll14", "stock",
           "is_exam", "heatwave", "flu_index",
           "dow", "month", "weekofyear", "season",
           "cold_chain"]
        + CATEGORICAL
    )

    for col in CATEGORICAL:
        panel[col] = panel[col].astype("category")

    model_df = panel.dropna(subset=["y_hdemand", f"sold_lag{max(LAGS)}"]).copy()
    return model_df, feature_cols


if __name__ == "__main__":
    panel = pd.read_csv(C.ARTIFACTS / "panel.csv", parse_dates=["date"])
    model_df, cols = build(panel)
    print(f"Supervised rows: {len(model_df):,} | features: {len(cols)}")
    print(cols)
