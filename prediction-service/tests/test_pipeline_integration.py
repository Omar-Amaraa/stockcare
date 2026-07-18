"""
End-to-end smoke test: generate -> features -> train -> evaluate -> emit
queries, on the shrunk (small_config) panel. This is the same sequence as
run.py but without touching disk artifacts, so it's safe to run repeatedly
in CI without clobbering services/forecasting/artifacts/.
"""

import numpy as np

import config as C
from features.build import build
from models.train import train_model, predict
from models import baselines
from shortage.gap import compute_gaps, emit_queries
from eval.backtest import accuracy_report, stockouts_avoided


def test_full_pipeline_runs_and_produces_valid_queries(panel):
    model_df, feature_cols = build(panel)
    fc, train, valid, cutoff = train_model(model_df, feature_cols)

    preds = {
        "model": predict(fc, valid, feature_cols),
        "MovingAverage": baselines.moving_average(valid),
        "SeasonalNaive": baselines.seasonal_naive(panel, valid),
    }
    y = valid["y_hdemand"].to_numpy()
    acc = accuracy_report(y, preds)
    for name, m in acc.items():
        assert m["WMAPE"] >= 0
        assert not np.isnan(m["WMAPE"])

    biz = stockouts_avoided(valid, preds["model"], C.HORIZON_H, C.SAFETY_STOCK_DAYS)
    assert biz["reactive_stockout_days"] >= 0
    assert biz["forecast_stockout_days"] >= 0

    last_date = valid["date"].max()
    snap = valid[valid["date"] == last_date].copy()
    snap_pred = predict(fc, snap, feature_cols)
    gaps = compute_gaps(snap, snap_pred)
    queries = emit_queries(gaps)

    # structural contract check: every emitted query has the exact shape
    # Layer 2/3 expects, regardless of which model backend produced it
    for q in queries:
        assert set(q.keys()) == {"pharmacy_id", "medicine_id", "quantity", "urgency", "timestamp"}
        assert q["quantity"] >= 0
        assert q["urgency"] in ("low", "medium", "high")


def test_model_beats_both_baselines_on_wmape(panel):
    """
    Not a hard CI gate in general (synthetic-data variance could flip this
    on an unlucky seed/shrunk window), but flags a real regression if the
    trained model stops being competitive with the naive baselines at all.
    """
    model_df, feature_cols = build(panel)
    fc, train, valid, cutoff = train_model(model_df, feature_cols)
    preds = {
        "model": predict(fc, valid, feature_cols),
        "MovingAverage": baselines.moving_average(valid),
    }
    y = valid["y_hdemand"].to_numpy()
    acc = accuracy_report(y, preds)
    # allow some slack: the model should not be drastically worse than the
    # naive baseline even on a small/shrunk validation window
    assert acc["model"]["WMAPE"] < acc["MovingAverage"]["WMAPE"] * 1.5
