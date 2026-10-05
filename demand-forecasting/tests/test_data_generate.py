"""
Tests for data/generate.py — the synthetic "digital twin" generator.

These tests check the *internal consistency* of the simulator: it is not
being compared against any real-world dataset (there is none — see the
LaTeX report), so what matters is that the simulated physics hold:
sold <= demand, sold <= stock-available, stockout flag is coherent, output
schema is stable, and the generator is deterministic given a seed.
"""

import numpy as np
import pandas as pd

import config as C
from data.generate import generate, _flu_index, _is_exam, _heatwave_calendar


def test_generate_returns_expected_shape_and_columns(panel):
    expected_cols = {
        "date", "pharmacy_id", "region", "medicine_id", "category",
        "cold_chain", "demand", "sold", "stock", "stockout",
        "is_exam", "heatwave", "flu_index",
    }
    assert expected_cols.issubset(panel.columns)
    n_pharmacies = len(C.PHARMACIES)
    n_meds = len(C.MEDICINES)
    assert len(panel) == n_pharmacies * n_meds * C.HISTORY_DAYS


def test_generate_is_deterministic_given_seed(small_config):
    p1 = generate(seed=123)
    p2 = generate(seed=123)
    pd.testing.assert_frame_equal(p1, p2)


def test_generate_different_seeds_differ(small_config):
    p1 = generate(seed=1)
    p2 = generate(seed=2)
    assert not p1["demand"].equals(p2["demand"])


def test_no_negative_values(panel):
    for col in ["demand", "sold", "stock", "stockout"]:
        assert (panel[col] >= 0).all(), f"{col} has negative values"


def test_sold_never_exceeds_demand(panel):
    # a reactive pharmacy can never sell more than what was actually demanded
    assert (panel["sold"] <= panel["demand"]).all()


def test_stockout_flag_is_coherent_with_sold_vs_demand(panel):
    # stockout == 1  <=>  sold < demand (demand could not be fully served)
    implied = (panel["sold"] < panel["demand"]).astype(int)
    assert (panel["stockout"] == implied).all()


def test_flu_index_bounded_and_peaks_in_winter():
    doy = np.arange(1, 366)
    flu = _flu_index(doy)
    assert (flu >= 0).all() and (flu <= 1).all()
    # day 1 (~Jan 1) should have a much higher flu index than day ~182 (~July)
    assert flu[0] > flu[181]


def test_is_exam_matches_configured_periods():
    # first exam period starts 1/3 in config.py -> EXAM_PERIODS[0] = ((1,3),(1,25))
    assert _is_exam(1, 10) == 1        # inside january exam window
    assert _is_exam(3, 15) == 0        # clearly outside any window
    assert _is_exam(5, 20) == 1        # inside end-of-year exam window


def test_heatwave_only_flagged_in_summer_months():
    dates = pd.date_range("2024-01-01", periods=400, freq="D")
    rng = np.random.default_rng(0)
    flag = _heatwave_calendar(dates, rng)
    non_summer_mask = ~dates.month.isin([6, 7, 8])
    assert flag[non_summer_mask].sum() == 0


def test_medicine_and_pharmacy_ids_match_config(panel):
    assert set(panel["pharmacy_id"].unique()) == {p["pharmacy_id"] for p in C.PHARMACIES}
    assert set(panel["medicine_id"].unique()) == {m[0] for m in C.MEDICINES}
