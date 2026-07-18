"""
Synthetic data generator — the "digital twin" of the pharmacy network.

Produces a daily panel of (pharmacy, medicine, date) rows with:
  - latent `demand`   : the true underlying need (uncensored)
  - `stock`           : simulated on-hand stock under a naive reactive (s,S) policy
  - `sold`            : min(demand, stock) — what a reactive pharmacy actually sells
  - `stockout`        : 1 if demand could not be fully served that day
  - exogenous signals : is_exam, heatwave, flu_index (also derivable from the date)

The point of the reactive stock policy is to reproduce the *problem* StockCare
solves: shelves go empty because ordering is reactive. The forecasting model
trains on `sold` (realistic, occasionally censored), and the business metric
later compares reactive stockouts against a forecast-driven policy.
"""

import numpy as np
import pandas as pd

import config as C


def _flu_index(doy: np.ndarray) -> np.ndarray:
    """Smooth winter-peaking seasonal curve in [0, 1], peak ~ early January."""
    # cosine peaking at day-of-year 0/365 (Jan), trough in summer
    return 0.5 * (1 + np.cos(2 * np.pi * doy / 365.25))


def _is_exam(month: int, day: int) -> int:
    for (m0, d0), (m1, d1) in C.EXAM_PERIODS:
        if (month, day) >= (m0, d0) and (month, day) <= (m1, d1):
            return 1
    return 0


def _heatwave_calendar(dates: pd.DatetimeIndex, rng: np.random.Generator) -> np.ndarray:
    """Random multi-day heat spells injected into Jun-Aug; returns 0/1 per date."""
    flag = np.zeros(len(dates), dtype=int)
    summer = np.where(dates.month.isin([6, 7, 8]))[0]
    # sprinkle ~8 heatwaves/year, each 3-6 days long
    n_waves = int(round(len(dates) / 365.25 * 8))
    if len(summer):
        starts = rng.choice(summer, size=n_waves, replace=True)
        for s in starts:
            length = rng.integers(3, 7)
            flag[s:s + length] = 1
    return flag


def generate(seed: int = C.SEED) -> pd.DataFrame:
    rng = np.random.default_rng(seed)

    dates = pd.date_range("2024-01-01", periods=C.HISTORY_DAYS, freq="D")
    doy = dates.dayofyear.to_numpy()
    dow = dates.dayofweek.to_numpy()          # 0=Mon ... 6=Sun
    month = dates.month.to_numpy()
    day = dates.day.to_numpy()

    flu = _flu_index(doy)
    exam = np.array([_is_exam(m, d) for m, d in zip(month, day)])
    heat = _heatwave_calendar(dates, rng)

    # weekly pattern: quieter Sundays, slightly busier Mon/Sat
    weekly = np.array([1.05, 1.00, 1.00, 1.00, 1.02, 1.08, 0.75])[dow]
    # mild upward yearly trend (network growth)
    trend = 1.0 + 0.10 * (np.arange(len(dates)) / len(dates))

    meds = pd.DataFrame(C.MEDICINES, columns=C.MEDICINE_COLS)
    rows = []

    for ph in C.PHARMACIES:
        heat_reg, flu_reg = C.REGION_SIGNAL[ph["region"]]
        for _, med in meds.iterrows():
            # ---- latent demand (Poisson) -------------------------------------
            lam = (
                med.base_demand
                * ph["size"]
                * weekly
                * trend
                * (1 + (med.exam_mult - 1) * exam)
                * (1 + (med.heat_mult - 1) * heat * heat_reg)
                * (1 + (med.flu_mult - 1) * flu * flu_reg)
            )
            # per-(pharmacy,medicine) random level shift for realism
            lam = lam * rng.uniform(0.85, 1.15)
            demand = rng.poisson(np.clip(lam, 0.05, None))

            # ---- reactive (s,S) stock simulation -----------------------------
            avg = max(med.base_demand * ph["size"], 1.0)
            reorder_point = int(round(avg * 7))     # ~1 week cover
            order_up_to = int(round(avg * 21))      # ~3 weeks cover
            lead_time = 3
            stock = order_up_to
            pipeline = {}                           # day_index -> qty arriving
            stock_series, sold_series, so_series = [], [], []
            for t in range(len(dates)):
                stock += pipeline.pop(t, 0)
                d = demand[t]
                sold = min(d, stock)
                stock -= sold
                stockout = int(d > sold)
                if stock <= reorder_point and not any(a > t for a in pipeline):
                    pipeline[t + lead_time] = order_up_to - stock
                stock_series.append(stock)
                sold_series.append(sold)
                so_series.append(stockout)

            df = pd.DataFrame({
                "date": dates,
                "pharmacy_id": ph["pharmacy_id"],
                "region": ph["region"],
                "medicine_id": med.medicine_id,
                "category": med.category,
                "cold_chain": int(med.cold_chain),
                "demand": demand,
                "sold": sold_series,
                "stock": stock_series,
                "stockout": so_series,
                "is_exam": exam,
                "heatwave": heat,
                "flu_index": np.round(flu, 4),
            })
            rows.append(df)

    panel = pd.concat(rows, ignore_index=True)
    return panel


if __name__ == "__main__":
    panel = generate()
    out = C.ARTIFACTS / "panel.csv"
    panel.to_csv(out, index=False)
    print(f"Generated {len(panel):,} rows -> {out}")
    print(panel.groupby("category")[["demand", "sold", "stockout"]].mean().round(2))
