"""
Tests du moteur d'explicabilite.

La propriete critique : l'evaluateur doit reproduire EXACTEMENT la fonction
objectif du MILP. Sans cela, toutes les justifications produites seraient
fausses — on comparerait des tournees avec une regle differente de celle qui
a servi a les choisir.

Lancement :  py -m pytest tests/test_explication.py -v
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

RACINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RACINE / "src"))
sys.path.insert(0, str(RACINE / "scenarios"))

from catalogue import Catalogue, ReglesChaineFroid          # noqa: E402
from explication import (                                    # noqa: E402
    ParametresObjectif, analyser, evaluer_tournees, generer_alternatives,
    redaction_motif,
)
from vrp_milp_pharma import build_and_solve_vrp              # noqa: E402
from scenarios import SCENARIOS, construire                  # noqa: E402


# =============================================================================
# FIXTURES
# =============================================================================


@pytest.fixture(scope="module")
def cfg() -> dict:
    with open(RACINE / "config" / "config.json", encoding="utf-8") as f:
        return json.load(f)


@pytest.fixture(scope="module")
def catalogue() -> Catalogue:
    regles = ReglesChaineFroid.depuis_json(RACINE / "config" / "cold_chain_rules.json")
    return Catalogue.charger(
        RACINE / "data" / "pharmacy_delivery_priorities.csv", regles
    )


@pytest.fixture(scope="module")
def reseau() -> dict:
    with open(RACINE / "data" / "reseau_sousse.json", encoding="utf-8") as f:
        return json.load(f)


def _monter(code: str, cfg, catalogue, reseau):
    """Construit et resout un scenario, renvoie tout le contexte utile."""
    sc = next(s for s in SCENARIOS if s.code == code)
    cfg_local = json.loads(json.dumps(cfg))
    cfg_local["solveur"].update(sc.solveur)
    cfg_local["sla"].update(sc.sla)

    scenario, noms, cmds, fleet, d, t = construire(sc, cfg_local, catalogue, reseau)
    params = ParametresObjectif.depuis_config(cfg_local)

    sv = dict(cfg_local["solveur"])
    sv["time_limit"] = 30
    solution = build_and_solve_vrp(scenario, fleet, d, t, **sv)
    return sc, scenario, noms, fleet, d, t, params, solution


@pytest.fixture(scope="module")
def s1(cfg, catalogue, reseau):
    return _monter("S1", cfg, catalogue, reseau)


@pytest.fixture(scope="module")
def s4(cfg, catalogue, reseau):
    return _monter("S4", cfg, catalogue, reseau)


# =============================================================================
# PROPRIETE CRITIQUE : FIDELITE A L'OBJECTIF DU MILP
# =============================================================================


@pytest.mark.parametrize("code", ["S1", "S2", "S3", "S4"])
def test_evaluateur_reproduit_l_objectif_du_solveur(code, cfg, catalogue, reseau):
    """Sans cette egalite, toutes les explications seraient invalides."""
    _, scenario, _, fleet, d, t, params, solution = _monter(
        code, cfg, catalogue, reseau
    )
    assert solution.status == "Optimal", f"{code} non resolu"
    ev = evaluer_tournees(solution.routes, scenario, fleet, d, t, params)
    assert ev.total == pytest.approx(solution.objective, abs=0.01), (
        f"{code} : evaluateur {ev.total:.4f} vs solveur {solution.objective:.4f}"
    )


@pytest.mark.parametrize("code", ["S1", "S2", "S3", "S4"])
def test_heures_d_arrivee_coherentes_avec_le_solveur(code, cfg, catalogue, reseau):
    _, scenario, _, fleet, d, t, params, solution = _monter(
        code, cfg, catalogue, reseau
    )
    ev = evaluer_tournees(solution.routes, scenario, fleet, d, t, params)
    for i in range(1, scenario.n_nodes):
        assert ev.arrivees[i] == pytest.approx(solution.arrival[i], abs=0.05), (
            f"{code} noeud {i}"
        )


def test_solution_du_solveur_est_faisable(s1):
    _, scenario, _, fleet, d, t, params, solution = s1
    ev = evaluer_tournees(solution.routes, scenario, fleet, d, t, params)
    assert ev.faisable, ev.violations


# =============================================================================
# L'OPTIMUM EST BIEN UN OPTIMUM
# =============================================================================


@pytest.mark.parametrize("code", ["S1", "S2", "S4"])
def test_aucune_alternative_ne_bat_l_optimum(code, cfg, catalogue, reseau):
    """Garde-fou : si un voisin est meilleur, le solveur a ete sous-dimensionne."""
    _, scenario, noms, fleet, d, t, params, solution = _monter(
        code, cfg, catalogue, reseau
    )
    _, comparaisons = analyser(
        solution.routes, scenario, fleet, d, t, params, noms
    )
    faisables = [c for c in comparaisons if not c.rejetee_pour_infaisabilite]
    assert faisables, "aucune alternative faisable generee"
    meilleure = min(faisables, key=lambda c: c.delta_total)
    assert meilleure.delta_total >= -0.01, (
        f"{code} : '{meilleure.alternative.libelle}' bat l'optimum de "
        f"{-meilleure.delta_total:.3f}"
    )


# =============================================================================
# DETECTION DES VIOLATIONS
# =============================================================================


def test_violation_chaine_du_froid_detectee(s1):
    """Forcer un noeud refrigere sur un vehicule sec doit etre signale."""
    _, scenario, _, fleet, d, t, params, _ = s1
    froids = [i for i in range(1, scenario.n_nodes) if scenario.cc[i]]
    assert froids, "le scenario S1 doit contenir un noeud refrigere"

    from vrp_milp_pharma import Fleet
    flotte_seche = Fleet(Q=[500.0], R=[0], c_f=[1.0])
    routes = {0: [0] + list(range(1, scenario.n_nodes)) + [0]}
    ev = evaluer_tournees(routes, scenario, flotte_seche, d, t, params)

    assert not ev.faisable
    assert any("CHAINE DU FROID" in v for v in ev.violations)


def test_violation_capacite_detectee(s1):
    _, scenario, _, _, d, t, params, _ = s1
    from vrp_milp_pharma import Fleet
    minuscule = Fleet(Q=[1.0], R=[1], c_f=[1.0])
    routes = {0: [0] + list(range(1, scenario.n_nodes)) + [0]}
    ev = evaluer_tournees(routes, scenario, minuscule, d, t, params)

    assert not ev.faisable
    assert any("CAPACITE" in v for v in ev.violations)


def test_noeud_non_servi_detecte(s1):
    _, scenario, _, fleet, d, t, params, _ = s1
    routes = {0: [0, 1, 0]}   # on oublie volontairement les autres
    ev = evaluer_tournees(routes, scenario, fleet, d, t, params)
    assert not ev.faisable
    assert any("n'est servi par aucun" in v for v in ev.violations)


def test_noeud_servi_deux_fois_detecte(s1):
    _, scenario, _, fleet, d, t, params, _ = s1
    routes = {0: [0] + list(range(1, scenario.n_nodes)) + [1, 0]}
    ev = evaluer_tournees(routes, scenario, fleet, d, t, params)
    assert not ev.faisable
    assert any("servi 2 fois" in v for v in ev.violations)


# =============================================================================
# COHERENCE DE LA DECOMPOSITION
# =============================================================================


def test_total_est_la_somme_des_composantes(s4):
    _, scenario, _, fleet, d, t, params, solution = s4
    ev = evaluer_tournees(solution.routes, scenario, fleet, d, t, params)
    assert ev.total == pytest.approx(sum(ev.composantes().values()))


def test_scenario_s4_produit_bien_du_retard(s4):
    """S4 est construit pour que le retard soit inevitable."""
    _, scenario, _, fleet, d, t, params, solution = s4
    ev = evaluer_tournees(solution.routes, scenario, fleet, d, t, params)
    assert ev.retard > 0, "S4 doit exhiber un arbitrage de retard"


def test_s4_epargne_les_noeuds_prioritaires(s4):
    """Le retard doit peser sur les priorites basses, pas sur l'insuline."""
    _, scenario, _, fleet, d, t, params, solution = s4
    ev = evaluer_tournees(solution.routes, scenario, fleet, d, t, params)

    en_retard = {i: ev.retards[i] for i in ev.retards if ev.retards[i] > 1e-6}
    assert en_retard, "S4 doit produire au moins un retard"

    pire = max(en_retard, key=lambda i: en_retard[i])
    plus_prioritaire = max(range(1, scenario.n_nodes), key=lambda i: scenario.pr[i])
    assert pire != plus_prioritaire, (
        "le noeud le plus prioritaire ne doit pas porter le plus gros retard"
    )


def test_priorite_pondere_bien_la_penalite(s4):
    """Verifie la formule w_p * pr[i] * L[i] terme a terme."""
    _, scenario, _, fleet, d, t, params, solution = s4
    ev = evaluer_tournees(solution.routes, scenario, fleet, d, t, params)
    attendu = sum(
        params.w_p * scenario.pr[i] * ev.retards[i]
        for i in range(1, scenario.n_nodes)
    )
    assert ev.retard == pytest.approx(attendu)


# =============================================================================
# GENERATION D'ALTERNATIVES
# =============================================================================


@pytest.mark.parametrize("code", ["S2"])
def test_familles_d_alternatives_presentes(code, cfg, catalogue, reseau):
    _, scenario, noms, fleet, d, t, _, solution = _monter(
        code, cfg, catalogue, reseau
    )
    alts = generer_alternatives(solution.routes, scenario, fleet, noms)
    familles = {a.famille for a in alts}
    assert {"ordre", "affectation"} <= familles


def test_alternatives_preservent_le_nombre_de_clients(s1):
    _, scenario, noms, fleet, d, t, _, solution = s1
    attendu = set(range(1, scenario.n_nodes))
    for alt in generer_alternatives(solution.routes, scenario, fleet, noms):
        servis = [i for tour in alt.routes.values() for i in tour[1:-1]]
        assert set(servis) == attendu, alt.libelle
        assert len(servis) == len(attendu), alt.libelle


def test_redaction_produit_toujours_un_motif(s4):
    _, scenario, noms, fleet, d, t, params, solution = s4
    _, comparaisons = analyser(
        solution.routes, scenario, fleet, d, t, params, noms
    )
    for c in comparaisons:
        motif = redaction_motif(c, params)
        assert isinstance(motif, str) and motif.strip()
        if c.rejetee_pour_infaisabilite:
            assert motif.startswith("IMPOSSIBLE")
