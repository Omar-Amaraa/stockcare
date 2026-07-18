"""
Demand forecaster.

Primary model: LightGBM (gradient-boosted trees) as specified in the cahier
des charges. If LightGBM (or its scipy dependency) is unavailable, the pipeline
transparently falls back to a pure-NumPy histogram GBT so it still runs
end-to-end. Either way the interface is the same: train_model(...) / predict(...).

A strict time-based split is used so evaluation never sees the future.
"""

import numpy as np
import pandas as pd

import config as C

try:
    import lightgbm as lgb
    HAS_LGB = True
except Exception:
    HAS_LGB = False

from models.gbt_numpy import HistGBT

CATEGORICAL = ["region", "category", "medicine_id"]


def time_split(model_df: pd.DataFrame, valid_frac: float = 0.2):
    cutoff = model_df["date"].quantile(1 - valid_frac)
    train = model_df[model_df["date"] <= cutoff]
    valid = model_df[model_df["date"] > cutoff]
    return train, valid, cutoff


def _encode_categoricals(model_df: pd.DataFrame):
    maps = {}
    df = model_df.copy()
    for c in CATEGORICAL:
        cat = df[c].astype("category")
        maps[c] = list(cat.cat.categories)
        df[c] = cat.cat.codes
    return df, maps


def _apply_maps(df: pd.DataFrame, maps: dict):
    df = df.copy()
    for c, cats in maps.items():
        df[c] = pd.Categorical(df[c], categories=cats).codes
    return df


class Forecaster:
    """Thin wrapper exposing a uniform predict() over either backend."""

    def __init__(self, backend, model, feature_cols, maps, best_iteration=None):
        self.backend = backend
        self.model = model
        self.feature_cols = feature_cols
        self.maps = maps
        self.best_iteration = best_iteration

    def predict(self, df: pd.DataFrame) -> np.ndarray:
        d = _apply_maps(df, self.maps)
        X = d[self.feature_cols]
        if self.backend == "lightgbm":
            p = self.model.predict(X, num_iteration=self.best_iteration)
        else:
            p = self.model.predict(X.to_numpy(dtype=float))
        return np.clip(p, 0, None)

    def save(self, path):
        if self.backend == "lightgbm":
            self.model.save_model(str(path))


def train_model(model_df: pd.DataFrame, feature_cols, params: dict | None = None):
    enc, maps = _encode_categoricals(model_df)
    train, valid, cutoff = time_split(enc)
    cat_idx = [feature_cols.index(c) for c in CATEGORICAL]

    if HAS_LGB:
        params = params or dict(
            objective="poisson", metric="mae", learning_rate=0.05,
            num_leaves=63, min_data_in_leaf=100, feature_fraction=0.8,
            bagging_fraction=0.8, bagging_freq=1, verbose=-1,
        )
        dtrain = lgb.Dataset(train[feature_cols], label=train["y_hdemand"],
                             categorical_feature=cat_idx)
        dvalid = lgb.Dataset(valid[feature_cols], label=valid["y_hdemand"], reference=dtrain)
        booster = lgb.train(
            params, dtrain, num_boost_round=1500,
            valid_sets=[dtrain, dvalid], valid_names=["train", "valid"],
            callbacks=[lgb.early_stopping(60), lgb.log_evaluation(0)],
        )
        fc = Forecaster("lightgbm", booster, feature_cols, maps, booster.best_iteration)
    else:
        model = HistGBT(n_estimators=160, learning_rate=0.06, max_depth=6)
        model.fit(train[feature_cols].to_numpy(dtype=float),
                  train["y_hdemand"].to_numpy(dtype=float))
        fc = Forecaster("numpy_gbt", model, feature_cols, maps)

    tr = model_df.loc[train.index]
    va = model_df.loc[valid.index]
    return fc, tr, va, cutoff


def predict(fc: Forecaster, df, feature_cols) -> np.ndarray:
    return fc.predict(df)


if __name__ == "__main__":
    from features.build import build
    panel = pd.read_csv(C.ARTIFACTS / "panel.csv", parse_dates=["date"])
    model_df, cols = build(panel)
    fc, train, valid, cutoff = train_model(model_df, cols)
    print(f"backend={fc.backend} cutoff={cutoff.date()}")
