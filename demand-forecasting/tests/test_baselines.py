"""Tests for models/baselines.py — the two naive forecasts the real model must beat."""

import numpy as np
import pandas as pd

import config as C
from models.baselines import moving_average, seasonal_naive


def test_moving_average_equals_roll14_times_horizon(model_df_and_cols):
    model_df, _ = model_df_and_cols
    pred = moving_average(model_df, window=14, horizon=C.HORIZON_H)
    expected = model_df["sold_roll14"].to_numpy() * C.HORIZON_H
    np.testing.assert_allclose(pred, expected)


def test_moving_average_is_non_negative(model_df_and_cols):
    model_df, _ = model_df_and_cols
    pred = moving_average(model_df)
    assert (pred >= 0).all()


def test_seasonal_naive_falls_back_when_no_last_year_data(small_config):
    """
    Build a deliberately short panel (< 364 days) so NO row can have a full
    364-day lookback -> seasonal_naive must fall back to moving_average
    everywhere. Uses its own short-lived generate() call rather than the
    shared session `panel` fixture, which is intentionally > 365 days so
    other tests can exercise heatwave/exam/flu seasonality.
    """
    from data.generate import generate
    from features.build import build

    orig = C.HISTORY_DAYS
    try:
        C.HISTORY_DAYS = 200  # well under 364 -> no seasonal lookback possible
        short_panel = generate(seed=C.SEED)
        model_df, _ = build(short_panel)
    finally:
        C.HISTORY_DAYS = orig

    pred = seasonal_naive(short_panel, model_df)
    fallback = moving_average(model_df)
    np.testing.assert_allclose(pred, fallback)


def test_seasonal_naive_output_length_matches_input(panel, model_df_and_cols):
    model_df, _ = model_df_and_cols
    pred = seasonal_naive(panel, model_df)
    assert len(pred) == len(model_df)
