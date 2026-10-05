"""
End-to-end pipeline for Layer 1 (Demand Forecasting).

    generate -> features -> train (+ baselines) -> evaluate -> emit restock queries

Run:  python run.py
Writes artifacts to ./artifacts/ and prints a compact report.
"""

import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config as C
from data.generate import generate
from features.build import build
from models.train import train_model, predict
from models import baselines
from shortage.gap import compute_gaps, emit_queries
from eval.backtest import accuracy_report, stockouts_avoided


def main():
    print("=" * 68)
    print("StockCare — Layer 1: Demand Forecasting Engine")
    print("=" * 68)

    # 1) synthetic data ------------------------------------------------------
    panel = generate()
    panel.to_csv(C.ARTIFACTS / "panel.csv", index=False)
    print(f"[1] Synthetic panel: {len(panel):,} rows "
          f"({panel['pharmacy_id'].nunique()} pharmacies x "
          f"{panel['medicine_id'].nunique()} medicines x {C.HISTORY_DAYS} days)")

    # 2) features ------------------------------------------------------------
    model_df, feature_cols = build(panel)
    print(f"[2] Supervised rows: {len(model_df):,} | features: {len(feature_cols)} "
          f"| horizon H = {C.HORIZON_H} days")

    # 3) train model (LightGBM, or NumPy fallback) + baselines --------------
    model, train, valid, cutoff = train_model(model_df, feature_cols)
    model.save(C.ARTIFACTS / "model.txt")
    model_name = "LightGBM" if model.backend == "lightgbm" else "NumPy-GBT (fallback)"
    y = valid["y_hdemand"].to_numpy()
    preds = {
        model_name:       predict(model, valid, feature_cols),
        "MovingAverage":  baselines.moving_average(valid),
        "SeasonalNaive":  baselines.seasonal_naive(panel, valid),
    }
    extra = f"best_iter={model.best_iteration}" if model.best_iteration else "160 trees"
    print(f"[3] Trained {model_name} ({extra}); "
          f"validation from {cutoff.date()} ({len(valid):,} rows)")

    # 4) evaluate ------------------------------------------------------------
    acc = accuracy_report(y, preds)
    biz = stockouts_avoided(valid, preds[model_name], C.HORIZON_H, C.SAFETY_STOCK_DAYS)

    print("\n[4] Forecast accuracy (held-out validation):")
    print(f"    {'model':<15}{'WMAPE':>9}{'MAE':>9}{'bias':>9}")
    for name, m in acc.items():
        print(f"    {name:<15}{m['WMAPE']:>9.3f}{m['MAE']:>9.2f}{m['bias']:>9.2f}")
    lift = (1 - acc[model_name]["WMAPE"] / acc["MovingAverage"]["WMAPE"]) * 100
    print(f"    -> {model_name} WMAPE is {lift:.1f}% lower than the moving-average baseline")

    print("\n    Business metric — simulated stockout days on validation window:")
    print(f"       reactive (baseline) : {biz['reactive_stockout_days']:,}")
    print(f"       forecast-driven     : {biz['forecast_stockout_days']:,}")
    print(f"       reduction           : {biz['reduction_pct']}%")

    # 5) emit restock queries for the latest decision date -------------------
    last_date = valid["date"].max()
    snap = valid[valid["date"] == last_date].copy()
    snap_pred = predict(model, snap, feature_cols)
    gaps = compute_gaps(snap, snap_pred)
    queries = emit_queries(gaps)
    print(f"\n[5] Restock queries emitted for {last_date.date()}: "
          f"{len(queries)} / {len(snap)} (pharmacy,medicine) pairs")
    for q in queries[:6]:
        print(f"       {q['pharmacy_id']} {q['medicine_id']}  "
              f"qty={q['quantity']:<4} urgency={q['urgency']}")

    # persist artifacts ------------------------------------------------------
    metrics = {"horizon_H": C.HORIZON_H, "cutoff": str(cutoff.date()),
               "accuracy": acc, "wmape_lift_vs_movingavg_pct": round(lift, 1),
               "business": biz}
    (C.ARTIFACTS / "metrics.json").write_text(json.dumps(metrics, indent=2))
    (C.ARTIFACTS / "sample_queries.json").write_text(json.dumps(queries, indent=2))
    saved = "panel.csv | metrics.json | sample_queries.json"
    if model.backend == "lightgbm":
        saved += " | model.txt"
    print(f"\nArtifacts written to {C.ARTIFACTS}/")
    print("  " + saved)


if __name__ == "__main__":
    main()
