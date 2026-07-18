"""
Tests for features/build.py — supervised table construction.

Focus: no leakage (features at day t must only use information <= t-1,
the target must only use information > t), correct feature list, and
correct H-day forward-sum target.
"""

import numpy as np
import pandas as pd

import config as C
from features.build import build, LAGS, ROLL


def test_build_returns_expected_feature_columns(model_df_and_cols):
    model_df, feature_cols = model_df_and_cols
    for l in LAGS:
        assert f"sold_lag{l}" in feature_cols
    for w in ROLL:
        assert f"sold_roll{w}" in feature_cols
    for extra in ["stockout_roll14", "stock", "is_exam", "heatwave",
                  "flu_index", "dow", "month", "weekofyear", "season",
                  "cold_chain", "region", "category", "medicine_id"]:
        assert extra in feature_cols


def test_no_nan_left_in_model_rows(model_df_and_cols):
    model_df, feature_cols = model_df_and_cols
    assert not model_df["y_hdemand"].isna().any()
    assert not model_df[f"sold_lag{max(LAGS)}"].isna().any()


def test_target_is_forward_sum_not_backward(panel):
    """
    y_hdemand at day t must equal sum(demand[t+1 .. t+H]) for a single
    (pharmacy, medicine) series — verified by hand on one real series
    from the fixture panel rather than a synthetic toy series, so it
    catches off-by-one / direction bugs against the actual generator output.
    """
    model_df, feature_cols = build(panel, horizon=C.HORIZON_H)

    ph, med = panel.iloc[0][["pharmacy_id", "medicine_id"]]
    series = (panel[(panel.pharmacy_id == ph) & (panel.medicine_id == med)]
              .sort_values("date").reset_index(drop=True))
    built = (model_df[(model_df.pharmacy_id == ph) & (model_df.medicine_id == med)]
             .sort_values("date").reset_index(drop=True))

    # pick a row comfortably inside the series (not near either edge)
    mid_date = built["date"].iloc[len(built) // 2]
    t_idx = series.index[series["date"] == mid_date][0]

    expected = series["demand"].iloc[t_idx + 1: t_idx + 1 + C.HORIZON_H].sum()
    actual = built.loc[built["date"] == mid_date, "y_hdemand"].iloc[0]
    assert actual == expected


def test_lag_feature_uses_past_only(panel):
    """sold_lag1 at day t must equal `sold` at day t-1, never day t itself."""
    model_df, _ = build(panel, horizon=C.HORIZON_H)
    ph, med = panel.iloc[0][["pharmacy_id", "medicine_id"]]
    series = (panel[(panel.pharmacy_id == ph) & (panel.medicine_id == med)]
              .sort_values("date").reset_index(drop=True))
    built = (model_df[(model_df.pharmacy_id == ph) & (model_df.medicine_id == med)]
             .sort_values("date").reset_index(drop=True))

    mid_date = built["date"].iloc[len(built) // 2]
    t_idx = series.index[series["date"] == mid_date][0]
    expected_lag1 = series["sold"].iloc[t_idx - 1]
    actual_lag1 = built.loc[built["date"] == mid_date, "sold_lag1"].iloc[0]
    assert actual_lag1 == expected_lag1


def test_rows_dropped_only_at_series_edges(panel):
    """
    Row count per (pharmacy, medicine) must shrink by exactly
    max(LAGS) at the start (warm-up) and HORIZON_H at the end (no future
    target available) relative to the raw panel.
    """
    model_df, _ = build(panel, horizon=C.HORIZON_H)
    raw_counts = panel.groupby(["pharmacy_id", "medicine_id"]).size()
    built_counts = model_df.groupby(["pharmacy_id", "medicine_id"], observed=True).size()
    expected_drop = max(LAGS) + C.HORIZON_H
    for key in raw_counts.index:
        assert raw_counts[key] - built_counts[key] == expected_drop
