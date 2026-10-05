"""
Tests for shortage/gap.py — the ONLY contract exposed to Layer 2/3 (the depot).
Because everything downstream depends on this JSON shape and this formula,
it gets the most exhaustive coverage of the whole test suite.
"""

import numpy as np
import pandas as pd

import config as C
from shortage.gap import safety_stock, urgency, compute_gaps, emit_queries


def test_safety_stock_formula():
    daily_avg = np.array([0.0, 1.0, 2.4, 10.0])
    out = safety_stock(daily_avg, days=5)
    np.testing.assert_array_equal(out, np.ceil(daily_avg * 5))


def test_urgency_chronic_is_always_high():
    assert urgency(gap=0.1, demand=100, category="chronic") == "high"


def test_urgency_thresholds_for_non_chronic():
    # cover_ratio = gap / demand
    assert urgency(gap=80, demand=100, category="essential") == "high"    # 0.8 > 0.75
    assert urgency(gap=50, demand=100, category="essential") == "medium"  # 0.4 < 0.5 <= 0.75
    assert urgency(gap=10, demand=100, category="comfort") == "low"       # 0.1 <= 0.4


def test_compute_gaps_formula():
    snapshot = pd.DataFrame({
        "pharmacy_id": ["PH01", "PH02"],
        "medicine_id": ["M001", "M002"],
        "category": ["chronic", "comfort"],
        "stock": [10.0, 100.0],
        "sold_roll7": [4.0, 2.0],
    })
    demand_pred = np.array([50.0, 5.0])
    out = compute_gaps(snapshot, demand_pred, tau=C.GAP_THRESHOLD_TAU)

    expected_safety = np.ceil(snapshot["sold_roll7"].to_numpy() * C.SAFETY_STOCK_DAYS)
    expected_gap = np.maximum(0, demand_pred + expected_safety - snapshot["stock"].to_numpy())
    np.testing.assert_array_equal(out["gap"].to_numpy(), expected_gap)
    # row 0: 50 + ceil(4*5)=20 - 10 = 60 > tau -> restock needed
    assert out.loc[0, "needs_restock"] == True
    # row 1: 5 + ceil(2*5)=10 - 100 -> negative -> clipped to 0 -> no restock
    assert out.loc[1, "needs_restock"] == False


def test_compute_gaps_never_negative():
    snapshot = pd.DataFrame({
        "pharmacy_id": ["PH01"], "medicine_id": ["M001"],
        "category": ["essential"], "stock": [10_000.0], "sold_roll7": [0.0],
    })
    out = compute_gaps(snapshot, np.array([1.0]))
    assert (out["gap"] >= 0).all()


def test_emit_queries_only_includes_rows_needing_restock():
    gaps = pd.DataFrame({
        "pharmacy_id": ["PH01", "PH02"],
        "medicine_id": ["M001", "M002"],
        "category": ["chronic", "comfort"],
        "demand_h": [50.0, 5.0],
        "gap": [60.0, 0.0],
        "needs_restock": [True, False],
    })
    queries = emit_queries(gaps)
    assert len(queries) == 1
    q = queries[0]
    assert q["pharmacy_id"] == "PH01"
    assert q["medicine_id"] == "M001"
    assert q["quantity"] == 60
    assert q["urgency"] == "high"
    assert "timestamp" in q


def test_emit_queries_quantity_is_ceiled_int():
    gaps = pd.DataFrame({
        "pharmacy_id": ["PH01"], "medicine_id": ["M001"], "category": ["essential"],
        "demand_h": [10.0], "gap": [7.2], "needs_restock": [True],
    })
    q = emit_queries(gaps)[0]
    assert q["quantity"] == 8
    assert isinstance(q["quantity"], int)


def test_emit_queries_empty_when_nothing_needs_restock():
    gaps = pd.DataFrame({
        "pharmacy_id": ["PH01"], "medicine_id": ["M001"], "category": ["essential"],
        "demand_h": [1.0], "gap": [0.0], "needs_restock": [False],
    })
    assert emit_queries(gaps) == []
