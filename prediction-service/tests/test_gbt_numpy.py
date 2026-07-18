"""
Tests for models/gbt_numpy.py — the pure-NumPy fallback gradient-boosted
trees used whenever LightGBM is not installed (which is the case in this
environment right now — see the report for details).
"""

import numpy as np

from models.gbt_numpy import HistGBT


def test_fits_a_simple_linear_relationship():
    rng = np.random.default_rng(0)
    X = rng.uniform(0, 10, size=(2000, 3))
    y = 3 * X[:, 0] - 2 * X[:, 1] + 0.1 * rng.standard_normal(2000) + 5
    model = HistGBT(n_estimators=120, learning_rate=0.1, max_depth=4, seed=0)
    model.fit(X, y)
    preds = model.predict(X)
    mae = np.mean(np.abs(preds - y))
    # a fitted model should track a near-linear signal far better than
    # just predicting the mean everywhere
    baseline_mae = np.mean(np.abs(y.mean() - y))
    assert mae < 0.3 * baseline_mae


def test_predictions_are_non_negative_after_clipping():
    rng = np.random.default_rng(1)
    X = rng.uniform(-5, 5, size=(500, 2))
    y = X[:, 0] - 10  # mostly negative target
    model = HistGBT(n_estimators=50, seed=1)
    model.fit(X, y)
    preds = model.predict(X)
    assert (preds >= 0).all()


def test_more_estimators_do_not_increase_training_error():
    rng = np.random.default_rng(2)
    X = rng.uniform(0, 10, size=(800, 4))
    y = X.sum(axis=1) + rng.standard_normal(800)

    err = []
    for n in (10, 100):
        m = HistGBT(n_estimators=n, learning_rate=0.1, seed=2)
        m.fit(X, y)
        err.append(np.mean(np.abs(m.predict(X) - y)))
    assert err[1] <= err[0]


def test_predict_output_shape_matches_input_rows():
    rng = np.random.default_rng(3)
    X = rng.uniform(0, 1, size=(37, 5))
    y = rng.uniform(0, 1, size=37)
    model = HistGBT(n_estimators=10, seed=3)
    model.fit(X, y)
    assert model.predict(X).shape == (37,)
