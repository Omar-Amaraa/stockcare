"""Tests for eval/backtest.py — accuracy metrics and the stockouts-avoided business metric."""

import numpy as np
import pandas as pd
import pytest

from eval.backtest import wmape, mae, bias, accuracy_report, stockouts_avoided


def test_wmape_zero_for_perfect_prediction():
    y = np.array([10, 20, 30])
    assert wmape(y, y) == 0.0


def test_wmape_known_value():
    y = np.array([10, 10])
    yhat = np.array([12, 8])
    # |2|+|2| = 4, sum|y| = 20 -> wmape = 0.2
    assert wmape(y, yhat) == pytest.approx(0.2)


def test_mae_known_value():
    y = np.array([1, 2, 3])
    yhat = np.array([1, 4, 6])
    assert mae(y, yhat) == (0 + 2 + 3) / 3


def test_bias_positive_means_overforecast():
    y = np.array([10, 10])
    yhat = np.array([12, 12])
    assert bias(y, yhat) == 2.0


def test_bias_negative_means_underforecast():
    y = np.array([10, 10])
    yhat = np.array([8, 8])
    assert bias(y, yhat) == -2.0


def test_accuracy_report_has_one_entry_per_model():
    y = np.array([5, 5, 5])
    preds = {"A": np.array([5, 5, 5]), "B": np.array([0, 0, 0])}
    report = accuracy_report(y, preds)
    assert set(report.keys()) == {"A", "B"}
    assert report["A"]["WMAPE"] == 0.0
    assert report["B"]["WMAPE"] == 1.0


def test_stockouts_avoided_perfect_forecast_never_stocks_out():
    """
    If the forecast exactly equals future demand every step and the safety
    stock is > 0, the forecast-driven policy should never register fewer
    or equal stockouts than a smart forecast — here we build a toy series
    where a perfect forecast should yield zero forecast stockouts.
    """
    dates = pd.date_range("2024-01-01", periods=10, freq="D")
    valid = pd.DataFrame({
        "pharmacy_id": ["PH01"] * 10,
        "medicine_id": ["M001"] * 10,
        "date": dates,
        "demand": [5] * 10,
        "sold_roll7": [5.0] * 10,
        "stockout": [1, 1, 0, 0, 0, 0, 0, 0, 0, 0],  # reactive sim had 2 stockout days
    })
    demand_pred = np.array([5 * 14] * 10, dtype=float)  # perfect H-day forecast
    result = stockouts_avoided(valid, demand_pred, horizon=14, safety_days=5)
    assert result["reactive_stockout_days"] == 2
    assert result["forecast_stockout_days"] == 0
    assert result["reduction_pct"] == 100.0


def test_stockouts_avoided_zero_reactive_gives_zero_reduction_not_crash():
    dates = pd.date_range("2024-01-01", periods=5, freq="D")
    valid = pd.DataFrame({
        "pharmacy_id": ["PH01"] * 5, "medicine_id": ["M001"] * 5, "date": dates,
        "demand": [1] * 5, "sold_roll7": [5.0] * 5, "stockout": [0] * 5,
    })
    demand_pred = np.array([14.0] * 5)
    result = stockouts_avoided(valid, demand_pred, horizon=14, safety_days=5)
    assert result["reactive_stockout_days"] == 0
    assert result["reduction_pct"] == 0.0
