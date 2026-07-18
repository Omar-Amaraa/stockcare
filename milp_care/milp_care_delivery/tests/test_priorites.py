"""
Tests de la couche priorites dynamiques : catalogue, regles chaine du froid,
agregation des demandes et derivation SLA.

Lancement :  py -m pytest tests/ -v      (depuis la racine du projet)
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

RACINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RACINE / "src"))

from catalogue import Catalogue, Medicament, ReglesChaineFroid, normaliser  # noqa: E402
from demandes import (  # noqa: E402
    CommandePharmacie, LigneCommande, fenetre_sla, simuler_demandes, temps_service,
)


# =============================================================================
# FIXTURES
# =============================================================================


@pytest.fixture(scope="module")
def config() -> dict:
    with open(RACINE / "config" / "config.json", encoding="utf-8") as f:
        return json.load(f)


@pytest.fixture(scope="module")
def regles() -> ReglesChaineFroid:
    return ReglesChaineFroid.depuis_json(RACINE / "config" / "cold_chain_rules.json")


@pytest.fixture(scope="module")
def catalogue(regles) -> Catalogue:
    return Catalogue.charger(
        RACINE / "data" / "pharmacy_delivery_priorities.csv", regles
    )


def _med(nom: str, prio: float, froid: int = 0, amm: str = "1") -> Medicament:
    return Medicament(
        num_amm=amm, nom_specialite=nom, dosage="", forme="", dci=nom,
        priorite=prio, chaine_du_froid=froid,
    )


# =============================================================================
# NORMALISATION
# =============================================================================


def test_normalisation_supprime_accents_et_casse():
    assert normaliser("Comprimé Pelliculé") == "comprime pellicule"
    assert normaliser("  ESPACES   MULTIPLES ") == "espaces multiples"
    assert normaliser("") == ""


# =============================================================================
# CATALOGUE
# =============================================================================


def test_catalogue_charge_toutes_les_lignes(catalogue):
    assert len(catalogue) > 6000


def test_priorites_dans_intervalle_unitaire(catalogue):
    for m in catalogue:
        assert 0.0 <= m.priorite <= 1.0, f"{m.nom_specialite} hors [0,1]"


def test_priorites_reellement_dynamiques(catalogue):
    """Le coeur du changement : ce ne sont plus 6 valeurs figees."""
    distinctes = {m.priorite for m in catalogue}
    assert len(distinctes) > 100, (
        f"seulement {len(distinctes)} priorites distinctes — "
        f"le catalogue semble encore fige"
    )


def test_recherche_par_amm(catalogue):
    premier = catalogue.medicaments[0]
    assert catalogue.par_amm(premier.num_amm) is premier
    with pytest.raises(KeyError):
        catalogue.par_amm("AMM_QUI_N_EXISTE_PAS")


def test_insuline_prioritaire_sur_parapharmacie(catalogue):
    insulines = catalogue.chercher("insuline humaine")
    nicotines = catalogue.chercher("nicotine")
    assert insulines and nicotines
    assert max(m.priorite for m in insulines) > max(m.priorite for m in nicotines)


def test_rescale_etire_bien_les_bornes(regles):
    cat = Catalogue.charger(
        RACINE / "data" / "pharmacy_delivery_priorities.csv", regles,
        rescale=True, borne_min=0.05, borne_max=1.0,
    )
    prios = [m.priorite for m in cat]
    assert min(prios) == pytest.approx(0.05, abs=1e-9)
    assert max(prios) == pytest.approx(1.0, abs=1e-9)


def test_csv_absent_leve_une_erreur(regles):
    with pytest.raises(FileNotFoundError):
        Catalogue.charger(RACINE / "data" / "inexistant.csv", regles)


# =============================================================================
# CHAINE DU FROID
# =============================================================================


def test_regles_detectent_les_thermosensibles(regles):
    assert regles.evaluer("INSULINE HUMAINE", "Solution injectable") == 1
    assert regles.evaluer("VACCIN GRIPPAL", "Suspension injectable") == 1
    assert regles.evaluer("PARACETAMOL", "Comprime") == 0


def test_regles_insensibles_aux_accents_et_casse(regles):
    assert regles.evaluer("insuline humaine", "") == 1
    assert regles.evaluer("INSULINE HUMAINE", "") == 1


def test_toutes_les_insulines_sont_refrigerees(catalogue):
    insulines = [m for m in catalogue if "insulin" in normaliser(m.dci)]
    assert insulines, "aucune insuline trouvee dans le catalogue"
    assert all(m.chaine_du_froid == 1 for m in insulines)


def test_vaccins_a_dci_codifiee_sont_rattrapes(regles):
    """Regression : COMIRNATY a pour DCI 'raxtozinameran', mot qui n'evoque
    pas un vaccin. Sans balayage du nom commercial, il passait a froid=0."""
    assert regles.evaluer("RAXTOZINAMERAN", "Suspension injectable",
                          "COMIRNATY OMICRON XBB.1.5") == 1


def test_vaccins_du_catalogue_sont_refrigeres(catalogue):
    vaccins = [m for m in catalogue if "comirnaty" in normaliser(m.nom_specialite)]
    assert vaccins, "COMIRNATY absent du catalogue"
    assert all(m.chaine_du_froid == 1 for m in vaccins)


def test_part_chaine_froid_plausible(catalogue):
    """Un referentiel officinal reel : quelques pourcents, pas la moitie."""
    part = catalogue.resume()["part_chaine_froid"]
    assert 0.001 < part < 0.25, f"part chaine du froid suspecte : {part:.3f}"


# =============================================================================
# AGREGATION DES COMMANDES
# =============================================================================


def test_agregation_max_retient_l_article_le_plus_critique():
    cmd = CommandePharmacie("PH01", "Test", [
        LigneCommande("1", 2.0, _med("PARACETAMOL", 0.10)),
        LigneCommande("2", 1.0, _med("INSULINE", 0.66, froid=1)),
        LigneCommande("3", 5.0, _med("VITAMINE C", 0.08)),
    ])
    assert cmd.priorite("max") == pytest.approx(0.66)
    assert cmd.article_critique().nom_specialite == "INSULINE"


def test_une_seule_ligne_froide_refrigere_tout_le_colis():
    cmd = CommandePharmacie("PH01", "Test", [
        LigneCommande("1", 2.0, _med("PARACETAMOL", 0.10, froid=0)),
        LigneCommande("2", 1.0, _med("INSULINE", 0.66, froid=1)),
    ])
    assert cmd.chaine_du_froid == 1


def test_moyenne_ponderee_dilue_l_article_critique():
    cmd = CommandePharmacie("PH01", "Test", [
        LigneCommande("1", 90.0, _med("PARACETAMOL", 0.10)),
        LigneCommande("2", 1.0, _med("INSULINE", 0.66)),
    ])
    assert cmd.priorite("moyenne_ponderee") < cmd.priorite("max")
    assert cmd.priorite("moyenne") == pytest.approx(0.38)


def test_mode_agregation_invalide_leve_une_erreur():
    cmd = CommandePharmacie("PH01", "Test", [
        LigneCommande("1", 1.0, _med("X", 0.5)),
    ])
    with pytest.raises(ValueError):
        cmd.priorite("mediane_geometrique")


def test_charge_est_la_somme_des_quantites():
    cmd = CommandePharmacie("PH01", "Test", [
        LigneCommande("1", 2.5, _med("A", 0.1)),
        LigneCommande("2", 4.5, _med("B", 0.2)),
    ])
    assert cmd.charge == pytest.approx(7.0)


# =============================================================================
# SLA ET TEMPS DE SERVICE
# =============================================================================


def test_sla_decroit_quand_la_priorite_monte(config):
    cfg = config["sla"]
    urgent = fenetre_sla(0.66, 0, cfg)
    routine = fenetre_sla(0.06, 0, cfg)
    assert urgent < routine


def test_sla_resserre_par_la_chaine_du_froid(config):
    cfg = config["sla"]
    assert fenetre_sla(0.40, 1, cfg) < fenetre_sla(0.40, 0, cfg)


def test_sla_respecte_toujours_le_plancher(config):
    cfg = config["sla"]
    for p in (0.0, 0.3, 0.66, 1.0, 5.0):
        assert fenetre_sla(p, 1, cfg) >= cfg["plancher_min"]


def test_sla_ne_extrapole_pas_hors_bornes(config):
    cfg = config["sla"]
    assert fenetre_sla(0.99, 0, cfg) == fenetre_sla(cfg["priorite_haute"], 0, cfg)
    assert fenetre_sla(-1.0, 0, cfg) == fenetre_sla(cfg["priorite_basse"], 0, cfg)


def test_temps_service_croit_avec_le_nombre_de_lignes(config):
    cfg = config["dechargement"]
    assert temps_service(9, cfg) > temps_service(2, cfg)


def test_temps_service_plafonne(config):
    cfg = config["dechargement"]
    assert temps_service(10_000, cfg) == pytest.approx(cfg["plafond_min"])


# =============================================================================
# CHAINE COMPLETE
# =============================================================================


def test_scenario_complet_est_coherent(catalogue, config):
    from demandes import construire_scenario

    with open(RACINE / "data" / "reseau_sousse.json", encoding="utf-8") as f:
        reseau = json.load(f)

    commandes = simuler_demandes(catalogue, reseau["pharmacies"], config["simulation"])
    scenario, noms, cmds = construire_scenario(
        reseau["depot"], reseau["pharmacies"], commandes, config
    )

    n = scenario.n_nodes
    assert n == len(reseau["pharmacies"]) + 1
    assert all(len(v) == n for v in (scenario.q, scenario.cc, scenario.pr,
                                     scenario.sigma, scenario.T, noms, cmds))
    # le depot ne consomme rien
    assert (scenario.q[0], scenario.cc[0], scenario.pr[0]) == (0.0, 0, 0.0)
    # chaque arret porte une priorite strictement positive et un SLA borne
    for i in range(1, n):
        assert 0.0 < scenario.pr[i] <= 1.0
        assert scenario.T[i] >= config["sla"]["plancher_min"]
        assert scenario.q[i] > 0


def test_priorites_des_noeuds_ne_sont_pas_toutes_egales(catalogue, config):
    """Garde-fou anti-regression : si tout est egal, on est revenu au fige."""
    from demandes import construire_scenario

    with open(RACINE / "data" / "reseau_sousse.json", encoding="utf-8") as f:
        reseau = json.load(f)
    commandes = simuler_demandes(catalogue, reseau["pharmacies"], config["simulation"])
    scenario, _, _ = construire_scenario(
        reseau["depot"], reseau["pharmacies"], commandes, config
    )
    assert len(set(scenario.pr[1:])) > 1
