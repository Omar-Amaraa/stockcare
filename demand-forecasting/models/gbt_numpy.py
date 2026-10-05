"""
Self-contained histogram gradient-boosted regression trees (pure NumPy).

This is a *fallback* so the pipeline runs in any environment. The production
model is LightGBM (see train.py); this mirrors its core idea — histogram split
finding + gradient boosting — in a few hundred lines with no native deps.

Not meant to match LightGBM's speed or accuracy; meant to keep the end-to-end
loop runnable and honest when LightGBM/scipy are unavailable.
"""

import numpy as np


class HistGBT:
    def __init__(self, n_estimators=160, learning_rate=0.06, max_depth=6,
                 max_bins=64, min_samples_leaf=80, subsample=0.7, seed=42):
        self.n_estimators = n_estimators
        self.lr = learning_rate
        self.max_depth = max_depth
        self.max_bins = max_bins
        self.min_samples_leaf = min_samples_leaf
        self.subsample = subsample
        self.rng = np.random.default_rng(seed)
        self.edges_ = None
        self.trees_ = []
        self.base_ = 0.0

    # ---- binning -----------------------------------------------------------
    def _fit_bins(self, X):
        self.edges_ = []
        for j in range(X.shape[1]):
            col = X[:, j]
            qs = np.quantile(col, np.linspace(0, 1, self.max_bins + 1)[1:-1])
            self.edges_.append(np.unique(qs))

    def _bin(self, X):
        codes = np.empty(X.shape, dtype=np.int16)
        for j in range(X.shape[1]):
            codes[:, j] = np.searchsorted(self.edges_[j], X[:, j], side="right")
        return codes

    # ---- one regression tree over binned features --------------------------
    def _fit_tree(self, Xb, g):
        feat, thr, left, right, val, is_leaf = [], [], [], [], [], []

        def new_node():
            feat.append(-1); thr.append(0); left.append(-1)
            right.append(-1); val.append(0.0); is_leaf.append(True)
            return len(feat) - 1

        root = new_node()
        # stack of (node_id, sample_index_array, depth)
        stack = [(root, np.arange(Xb.shape[0]), 0)]
        nbins = self.max_bins + 1

        while stack:
            nid, idx, depth = stack.pop()
            g_node = g[idx]
            val[nid] = float(g_node.mean()) if len(g_node) else 0.0
            if depth >= self.max_depth or len(idx) < 2 * self.min_samples_leaf:
                continue

            total_sum, total_cnt = g_node.sum(), len(idx)
            best = (0.0, -1, -1)  # gain, feature, threshold-bin
            Xb_node = Xb[idx]
            for j in range(Xb.shape[1]):
                codes = Xb_node[:, j]
                sg = np.bincount(codes, weights=g_node, minlength=nbins)
                cn = np.bincount(codes, minlength=nbins).astype(float)
                left_sum = np.cumsum(sg)[:-1]
                left_cnt = np.cumsum(cn)[:-1]
                right_sum = total_sum - left_sum
                right_cnt = total_cnt - left_cnt
                ok = (left_cnt >= self.min_samples_leaf) & (right_cnt >= self.min_samples_leaf)
                with np.errstate(divide="ignore", invalid="ignore"):
                    gain = np.where(
                        ok,
                        left_sum ** 2 / left_cnt + right_sum ** 2 / right_cnt
                        - total_sum ** 2 / total_cnt,
                        -np.inf,
                    )
                b = int(np.argmax(gain))
                if gain[b] > best[0]:
                    best = (float(gain[b]), j, b)

            _, bj, bb = best
            if bj < 0:
                continue
            mask = Xb_node[:, bj] <= bb
            l_idx, r_idx = idx[mask], idx[~mask]
            if len(l_idx) < self.min_samples_leaf or len(r_idx) < self.min_samples_leaf:
                continue
            lnode, rnode = new_node(), new_node()
            feat[nid], thr[nid] = bj, bb
            left[nid], right[nid], is_leaf[nid] = lnode, rnode, False
            stack.append((lnode, l_idx, depth + 1))
            stack.append((rnode, r_idx, depth + 1))

        return {
            "feature": np.array(feat), "threshold": np.array(thr),
            "left": np.array(left), "right": np.array(right),
            "value": np.array(val), "is_leaf": np.array(is_leaf),
        }

    def _predict_tree(self, tree, Xb):
        n = Xb.shape[0]
        node = np.zeros(n, dtype=np.int64)
        feature, threshold = tree["feature"], tree["threshold"]
        left, right = tree["left"], tree["right"]
        for _ in range(self.max_depth + 1):
            f = feature[node]
            active = f >= 0
            if not active.any():
                break
            f_safe = np.where(active, f, 0)
            go_left = Xb[np.arange(n), f_safe] <= threshold[node]
            nxt = np.where(go_left, left[node], right[node])
            node = np.where(active, nxt, node)
        return tree["value"][node]

    # ---- boosting ----------------------------------------------------------
    def fit(self, X, y):
        X = np.asarray(X, dtype=float)
        y = np.asarray(y, dtype=float)
        self._fit_bins(X)
        Xb = self._bin(X)
        self.base_ = float(y.mean())
        pred = np.full(y.shape, self.base_)
        n = len(y)
        sub = max(1, int(self.subsample * n))
        for _ in range(self.n_estimators):
            resid = y - pred
            idx = self.rng.choice(n, size=sub, replace=False)
            tree = self._fit_tree(Xb[idx], resid[idx])
            pred += self.lr * self._predict_tree(tree, Xb)
            self.trees_.append(tree)
        return self

    def predict(self, X):
        X = np.asarray(X, dtype=float)
        Xb = self._bin(X)
        pred = np.full(X.shape[0], self.base_)
        for tree in self.trees_:
            pred += self.lr * self._predict_tree(tree, Xb)
        return np.clip(pred, 0, None)
