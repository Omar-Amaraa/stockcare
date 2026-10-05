"""
Pytest configuration shared by all test modules.

Makes `services/forecasting/` importable as top-level packages (config, data,
features, models, shortage, eval) exactly like run.py does, and exposes a few
reusable fixtures so individual test files stay short.
"""

import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import config as C  # noqa: E402


@pytest.fixture(scope="session")
def small_config():
    """
    Shrink the simulation window for the whole test session so tests run in
    seconds instead of the ~90k-row production panel. Restored afterwards.
    """
    orig_history = C.HISTORY_DAYS
    C.HISTORY_DAYS = 400  # > 1 year: still exercises exam/heatwave/flu seasonality
    yield C
    C.HISTORY_DAYS = orig_history


@pytest.fixture(scope="session")
def panel(small_config):
    from data.generate import generate
    return generate(seed=C.SEED)


@pytest.fixture(scope="session")
def model_df_and_cols(panel):
    from features.build import build
    return build(panel)
