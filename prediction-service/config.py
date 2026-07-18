"""
Central configuration for the StockCare demand-forecasting engine.

Everything the synthetic generator, feature pipeline, model, and shortage logic
need is declared here so the rest of the code stays free of magic numbers.
"""

from pathlib import Path

# ----------------------------------------------------------------------------
# Paths
# ----------------------------------------------------------------------------
ROOT = Path(__file__).resolve().parent
ARTIFACTS = ROOT / "artifacts"
ARTIFACTS.mkdir(exist_ok=True)

# ----------------------------------------------------------------------------
# Forecasting horizon & simulation window
# ----------------------------------------------------------------------------
HORIZON_H = 14          # forecast demand over the next H days (rolling)
HISTORY_DAYS = 730      # 2 years of synthetic history
SEED = 42

# ----------------------------------------------------------------------------
# Shortage-gap logic
# ----------------------------------------------------------------------------
SAFETY_STOCK_DAYS = 5   # safety buffer S expressed in days of average demand
GAP_THRESHOLD_TAU = 3   # emit a restock query only if the H-day gap G > tau (units)

# ----------------------------------------------------------------------------
# Pharmacy network (a handful of officines across Tunisian governorates)
# `size` scales baseline footfall; regions differ in climate / epidemiology.
# ----------------------------------------------------------------------------
PHARMACIES = [
    {"pharmacy_id": "PH01", "region": "Sousse",     "size": 1.30},
    {"pharmacy_id": "PH02", "region": "Sousse",     "size": 0.90},
    {"pharmacy_id": "PH03", "region": "Tunis",      "size": 1.50},
    {"pharmacy_id": "PH04", "region": "Tunis",      "size": 1.00},
    {"pharmacy_id": "PH05", "region": "Sfax",       "size": 1.10},
    {"pharmacy_id": "PH06", "region": "Gafsa",      "size": 0.70},   # hotter, inland
    {"pharmacy_id": "PH07", "region": "Bizerte",    "size": 0.85},
    {"pharmacy_id": "PH08", "region": "Monastir",   "size": 0.95},
]

# Regional multipliers per signal: hot inland governorates react more to heat,
# northern/coastal ones carry a slightly stronger winter flu season.
REGION_SIGNAL = {
    #            heat   flu
    "Sousse":   (1.00, 1.00),
    "Tunis":    (0.95, 1.15),
    "Sfax":     (1.10, 0.95),
    "Gafsa":    (1.40, 0.85),
    "Bizerte":  (0.90, 1.20),
    "Monastir": (1.05, 1.00),
}

# ----------------------------------------------------------------------------
# Medicine catalogue (subset — pilot scope).
# category      : chronic/vital | essential | comfort   (drives priority later)
# base_demand   : mean units sold per day at a size=1.0 pharmacy
# exam/heat/flu : how strongly this medicine reacts to each contextual signal
# cold_chain    : refrigerated transport required (used by Layers 2/3 later)
# ----------------------------------------------------------------------------
MEDICINES = [
    # id      name                    category      base  exam  heat  flu  cold
    ("M001", "Insuline",              "chronic",    12.0, 1.00, 1.05, 1.00, 1),
    ("M002", "Metformine",            "chronic",    18.0, 1.00, 1.00, 1.00, 0),
    ("M003", "Amlodipine (HTA)",      "chronic",    15.0, 1.00, 1.10, 1.00, 0),
    ("M004", "Levothyrox",            "chronic",     9.0, 1.00, 1.00, 1.00, 0),
    ("M005", "Ventoline (asthme)",    "essential",  10.0, 1.05, 1.15, 1.20, 0),
    ("M006", "Amoxicilline",          "essential",  14.0, 1.00, 1.00, 1.35, 0),
    ("M007", "Paracetamol",           "essential",  40.0, 1.25, 1.10, 1.45, 0),
    ("M008", "Oseltamivir (grippe)",  "essential",   4.0, 1.00, 1.00, 2.20, 0),
    ("M009", "Vaccin grippe",         "essential",   3.0, 1.00, 1.00, 1.80, 1),
    ("M010", "SRO (rehydratation)",   "essential",   6.0, 1.00, 2.00, 1.00, 0),
    ("M011", "Antihistaminique",      "comfort",    12.0, 1.05, 1.70, 1.00, 0),
    ("M012", "Anxiolytique",          "comfort",     8.0, 1.60, 1.00, 1.00, 0),
    ("M013", "Vitamine C / tonus",    "comfort",    20.0, 1.55, 1.10, 1.10, 0),
    ("M014", "Anti-diarrheique (GI)", "comfort",     9.0, 1.05, 1.60, 1.00, 0),
    ("M015", "Creme solaire / SPF",   "comfort",     7.0, 1.00, 1.90, 1.00, 0),
]

MEDICINE_COLS = ["medicine_id", "name", "category",
                 "base_demand", "exam_mult", "heat_mult", "flu_mult", "cold_chain"]

# ----------------------------------------------------------------------------
# Contextual calendars (approximate, illustrative of Tunisian patterns)
# ----------------------------------------------------------------------------
# University exam periods (month, day) ranges -> stimulant / anxiolytic / vitamin spikes
EXAM_PERIODS = [
    ((1, 3), (1, 25)),    # partiels janvier
    ((5, 15), (6, 25)),   # examens fin d'annee
]

# Flu season is modelled as a smooth seasonal curve peaking in winter (Dec-Jan).
# Heatwaves are random multi-day spells injected into Jun-Aug (see generate.py).
