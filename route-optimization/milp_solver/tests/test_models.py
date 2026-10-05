"""
Suite de tests : MILP exact vs relaxation continue par descente de gradient.
Lancement (Windows) :  py -m pytest test_models.py -v
Prerequis            :  py -m pip install pytest pulp numpy
"""

import numpy as np
import pulp
import pytest

# --------------------------------------------------------------------------- #
# DONNEES ET MODELES (identiques a ceux du notebook, auto-portants)
# --------------------------------------------------------------------------- #

VALUES = np.array([78, 35, 89, 36, 94, 75, 74, 79, 80, 16, 61, 22], dtype=float)
WEIGHTS = np.array([18, 9, 23, 20, 59, 61, 70, 75, 76, 30, 84, 40], dtype=float)
CAPACITY = 250.0
TOL = 1e-6


def solve_milp(values, weights, capacity):
    """Maximise sum(v_i x_i) s.c. sum(w_i x_i) <= C, x_i dans {0, 1}."""
    prob = pulp.LpProblem("Knapsack_MILP", pulp.LpMaximize)
    x = [pulp.LpVariable(f"x_{i}", cat="Binary") for i in range(len(values))]
    prob += pulp.lpSum(values[i] * x[i] for i in range(len(values)))
    prob += pulp.lpSum(weights[i] * x[i] for i in range(len(values))) <= capacity
    prob.solve(pulp.PULP_CBC_CMD(msg=0))
    sol = np.array([float(v.value()) for v in x])
    return sol, float(pulp.value(prob.objective)), pulp.LpStatus[prob.status]


def gradient_descent(values, weights, capacity, lr0=1e-4, rho=5.0,
                     n_iter=6000, decay=0.05, x0=None):
    """Descente de gradient projetee sur la relaxation continue [0, 1]^n.

    Pas decroissant lr_t = lr0 / (1 + decay * t) : amortit les oscillations
    provoquees par la penalite exterieure au voisinage de la contrainte.
    """
    n = len(values)
    x = np.full(n, 0.5) if x0 is None else np.array(x0, dtype=float)
    history = []
    for it in range(n_iter):
        violation = max(0.0, weights @ x - capacity)
        history.append(-(values @ x) + rho * violation ** 2)
        grad = -values + 2.0 * rho * violation * weights
        x = np.clip(x - (lr0 / (1.0 + decay * it)) * grad, 0.0, 1.0)
    return x, np.array(history)


# --------------------------------------------------------------------------- #
# FIXTURES
# --------------------------------------------------------------------------- #

@pytest.fixture(scope="module")
def milp_result():
    return solve_milp(VALUES, WEIGHTS, CAPACITY)


@pytest.fixture(scope="module")
def grad_result():
    return gradient_descent(VALUES, WEIGHTS, CAPACITY)


# --------------------------------------------------------------------------- #
# TEST 1 : integrite binaire et faisabilite de la solution MILP
# --------------------------------------------------------------------------- #

def test_milp_solution_est_binaire(milp_result):
    x_milp, obj_milp, status = milp_result

    assert status == "Optimal", f"Solveur non optimal : {status}"

    # Chaque variable vaut exactement 0 ou 1 (aucune valeur fractionnaire)
    for i, xi in enumerate(x_milp):
        assert abs(xi - round(xi)) < TOL, f"x_{i} = {xi} n'est pas entier"
        assert round(xi) in (0, 1), f"x_{i} = {xi} hors de {{0, 1}}"

    # La contrainte de capacite est respectee
    assert WEIGHTS @ x_milp <= CAPACITY + TOL

    # L'objectif reconstruit correspond a la valeur rapportee par le solveur
    assert VALUES @ x_milp == pytest.approx(obj_milp, abs=1e-6)


# --------------------------------------------------------------------------- #
# TEST 2 : la descente de gradient fait strictement decroitre la loss
# --------------------------------------------------------------------------- #

def test_loss_gradient_decroit_strictement(grad_result):
    _, history = grad_result

    assert len(history) > 1, "Historique de loss vide"
    assert history[-1] < history[0], (
        f"La loss n'a pas diminue : debut={history[0]:.4f}, fin={history[-1]:.4f}"
    )

    # Decroissance monotone (aucune remontee au-dela de la tolerance numerique)
    diffs = np.diff(history)
    assert np.all(diffs <= 1e-9), (
        f"Remontee de la loss detectee (max delta = {diffs.max():.3e}) : "
        "learning rate probablement trop grand"
    )

    # La solution relachee reste dans le domaine [0, 1]
    x_grad, _ = grad_result
    assert np.all(x_grad >= -TOL) and np.all(x_grad <= 1 + TOL)


# --------------------------------------------------------------------------- #
# TEST 3 : borne de relaxation — objectif MILP >= minimum continu du gradient
# --------------------------------------------------------------------------- #

def test_borne_relaxation(milp_result, grad_result):
    _, obj_milp, _ = milp_result
    _, history = grad_result
    loss_min = float(history.min())

    # L'objectif MILP (valeur maximisee) domine le minimum de la loss continue
    assert obj_milp >= loss_min, (
        f"Borne violee : objectif MILP={obj_milp:.4f} < loss min={loss_min:.4f}"
    )

    # Corollaire : la relaxation continue majore l'optimum entier
    x_grad, _ = grad_result
    valeur_relachee = float(VALUES @ x_grad)
    lp = pulp.LpProblem("Knapsack_LP", pulp.LpMaximize)
    xr = [pulp.LpVariable(f"xr_{i}", lowBound=0, upBound=1, cat="Continuous")
          for i in range(len(VALUES))]
    lp += pulp.lpSum(VALUES[i] * xr[i] for i in range(len(VALUES)))
    lp += pulp.lpSum(WEIGHTS[i] * xr[i] for i in range(len(VALUES))) <= CAPACITY
    lp.solve(pulp.PULP_CBC_CMD(msg=0))
    borne_lp = float(pulp.value(lp.objective))

    assert borne_lp >= obj_milp - 1e-6, (
        f"La relaxation LP ({borne_lp:.4f}) doit majorer le MILP ({obj_milp:.4f})"
    )
    assert valeur_relachee <= borne_lp + 1e-6
