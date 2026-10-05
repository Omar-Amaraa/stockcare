"""
Tests for models/train.py — the training orchestration (time split,
categorical encoding, LightGBM-or-NumPy-fallback dispatch).
"""

import numpy as np
import pandas as pd

from models.train import time_split, _encode_categoricals, _apply_maps, train_model, predict


def test_time_split_is_strictly_chronological(model_df_and_cols):
    model_df, _ = model_df_and_cols
    train, valid, cutoff = time_split(model_df, valid_frac=0.2)
    assert train["date"].max() <= cutoff
    assert valid["date"].min() > cutoff
    assert len(train) + len(valid) == len(model_df)


def test_encode_categoricals_roundtrip(model_df_and_cols):
    model_df, feature_cols = model_df_and_cols
    enc, maps = _encode_categoricals(model_df)
    for c in ["region", "category", "medicine_id"]:
        assert pd.api.types.is_integer_dtype(enc[c])
    # applying the same maps to the original frame must reproduce the codes
    reapplied = _apply_maps(model_df, maps)
    for c in maps:
        np.testing.assert_array_equal(enc[c].to_numpy(), reapplied[c].to_numpy())


def test_train_model_runs_end_to_end_and_predicts_reasonably(model_df_and_cols):
    model_df, feature_cols = model_df_and_cols
    fc, train, valid, cutoff = train_model(model_df, feature_cols)

    assert fc.backend in ("lightgbm", "numpy_gbt")
    preds = predict(fc, valid, feature_cols)
    assert len(preds) == len(valid)
    assert (preds >= 0).all()  # demand forecasts cannot be negative

    # sanity: predictions should correlate with the actual target, i.e. the
    # model learned *something* rather than just predicting noise
    y = valid["y_hdemand"].to_numpy()
    corr = np.corrcoef(preds, y)[0, 1]
    assert corr > 0.3


def test_train_model_beats_predicting_the_mean(model_df_and_cols):
    """
    A trained model that can't even beat a constant mean prediction would be
    useless — this is the minimum bar before comparing against the smarter
    MovingAverage / SeasonalNaive baselines in eval/backtest.py.
    """
    model_df, feature_cols = model_df_and_cols
    fc, train, valid, cutoff = train_model(model_df, feature_cols)
    preds = predict(fc, valid, feature_cols)
    y = valid["y_hdemand"].to_numpy()

    mean_baseline = np.full_like(y, train["y_hdemand"].mean(), dtype=float)
    model_mae = np.mean(np.abs(preds - y))
    baseline_mae = np.mean(np.abs(mean_baseline - y))
    assert model_mae < baseline_mae
