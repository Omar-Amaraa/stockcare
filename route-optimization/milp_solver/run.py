"""
=============================================================================
 MILP CARE — POINT D'ENTREE UNIQUE
=============================================================================
 Calcule la tournee optimale de livraison pharmaceutique a partir de :
   - la geolocalisation reelle des pharmacies      (data/reseau_sousse.json)
   - les demandes emises par les pharmacies        (data/demandes.csv)
   - le coefficient de priorite PAR MEDICAMENT     (data/pharmacy_delivery_priorities.csv)

 CHANGEMENT MAJEUR vs version precedente :
   Les priorites ne sont plus 6 valeurs figees par profil therapeutique.
   Chaque medicament porte son propre coefficient dynamique, agrege au
   niveau de la pharmacie et injecte directement dans l'objectif du MILP.

 LANCEMENT
   Windows :  py run.py
   Linux    :  python3 run.py

 OPTIONS
   --config CHEMIN     fichier de configuration (defaut: config/config.json)
   --simuler           regenere data/demandes.csv par tirage sur le catalogue
   --time-limit N      plafond solveur en secondes (surcharge la config)
   --no-carte          n'ecrit pas la carte HTML interactive
=============================================================================
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

RACINE = Path(__file__).resolve().parent
sys.path.insert(0, str(RACINE / "src"))

from catalogue import Catalogue, ReglesChaineFroid          # noqa: E402
from demandes import (                                       # noqa: E402
    charger_demandes, construire_scenario, exporter_demandes, simuler_demandes,
)
from geometrie import build_matrix_calibrated                # noqa: E402
from vrp_milp_pharma import Fleet, build_and_solve_vrp, plot_solution, print_report  # noqa: E402


# =============================================================================
# 1. CONFIGURATION
# =============================================================================


def charger_config(chemin: Path) -> dict:
    if not chemin.exists():
        raise FileNotFoundError(
            f"Configuration introuvable : {chemin}\n"
            f"Le fichier config/config.json doit accompagner run.py."
        )
    with open(chemin, "r", encoding="utf-8") as f:
        return json.load(f)


def resoudre(chemin_relatif: str) -> Path:
    """Resout un chemin de la config par rapport a la racine du projet."""
    return (RACINE / chemin_relatif).resolve()


# =============================================================================
# 2. AFFICHAGE
# =============================================================================


def titre(texte: str, largeur: int = 78) -> None:
    print("=" * largeur)
    print(f" {texte}")
    print("=" * largeur)


def controle_faisabilite(scenario, fleet) -> list[str]:
    """Detecte en amont les causes structurelles d'infaisabilite.

    Un solveur qui repond 'Infeasible' n'explique rien a l'exploitant. Ces
    controles disent PRECISEMENT ce qui bloque et de combien.
    """
    alertes: list[str] = []

    charge_totale = sum(scenario.q[1:])
    capacite_totale = sum(fleet.Q)
    if charge_totale > capacite_totale:
        alertes.append(
            f"CAPACITE INSUFFISANTE : {charge_totale:.1f} unites a livrer pour "
            f"{capacite_totale:.1f} de capacite flotte "
            f"(deficit {charge_totale - capacite_totale:.1f}). "
            f"-> ajouter un vehicule, augmenter Q dans config.json, "
            f"ou scinder la livraison en deux cycles."
        )

    charge_froide = sum(scenario.q[i] for i in range(1, scenario.n_nodes)
                        if scenario.cc[i])
    capacite_froide = sum(q for q, r in zip(fleet.Q, fleet.R) if r)
    if charge_froide > capacite_froide:
        alertes.append(
            f"CAPACITE REFRIGEREE INSUFFISANTE : {charge_froide:.1f} unites "
            f"thermosensibles pour {capacite_froide:.1f} de capacite refrigeree "
            f"(deficit {charge_froide - capacite_froide:.1f}). "
            f"-> passer un vehicule en R=1 dans config.json."
        )

    plus_grosse = max(scenario.q[1:])
    if plus_grosse > max(fleet.Q):
        i = scenario.q.index(plus_grosse)
        alertes.append(
            f"COMMANDE INDIVISIBLE TROP VOLUMINEUSE : noeud {i} demande "
            f"{plus_grosse:.1f} unites, le plus gros vehicule en porte "
            f"{max(fleet.Q):.1f}."
        )

    for i in range(1, scenario.n_nodes):
        if scenario.cc[i] and scenario.q[i] > capacite_froide:
            alertes.append(
                f"NOEUD {i} : commande refrigeree de {scenario.q[i]:.1f} unites "
                f"superieure a toute capacite refrigeree disponible."
            )

    return alertes


def afficher_commandes(scenario, noms, cmds, mode: str) -> None:
    print(f"\n COMMANDES AGREGEES  (priorite du noeud = {mode} des lignes)\n")
    entete = (f"  {'#':>2}  {'PHARMACIE':32s} {'LIG':>3} {'CHARGE':>7} "
              f"{'FROID':>6} {'PRIORITE':>9} {'SLA(min)':>9}  ARTICLE CRITIQUE")
    print(entete)
    print("  " + "-" * (len(entete) + 12))
    for i in range(1, scenario.n_nodes):
        cmd = cmds[i]
        crit = cmd.article_critique()
        libelle = f"{crit.nom_specialite} ({crit.dci})"[:38] if crit else "-"
        print(f"  {i:2d}  {noms[i][:32]:32s} {cmd.nb_lignes:3d} "
              f"{scenario.q[i]:7.1f} {'OUI' if scenario.cc[i] else '  -':>6} "
              f"{scenario.pr[i]:9.4f} {scenario.T[i]:9.1f}  {libelle}")


# =============================================================================
# 3. PROGRAMME PRINCIPAL
# =============================================================================


def main() -> int:
    ap = argparse.ArgumentParser(description="MILP CARE — routage pharmaceutique")
    ap.add_argument("--config", default="config/config.json")
    ap.add_argument("--simuler", action="store_true",
                    help="regenere data/demandes.csv depuis le catalogue")
    ap.add_argument("--time-limit", type=int, default=None)
    ap.add_argument("--no-carte", action="store_true")
    args = ap.parse_args()

    cfg = charger_config(resoudre(args.config))
    ch = cfg["chemins"]

    titre("MILP CARE — ROUTAGE PHARMACEUTIQUE A PRIORITES DYNAMIQUES")
    print(f" Execution : {datetime.now():%Y-%m-%d %H:%M:%S}")

    # --- 3.1 Catalogue -----------------------------------------------------
    regles = ReglesChaineFroid.depuis_json(resoudre(ch["regles_froid"]))
    catalogue = Catalogue.charger(
        resoudre(ch["catalogue_csv"]),
        regles,
        rescale=cfg["priorite"]["rescale"],
        borne_min=cfg["priorite"]["borne_min"],
        borne_max=cfg["priorite"]["borne_max"],
    )
    r = catalogue.resume()
    print(f"\n CATALOGUE")
    print(f"   Medicaments referencies  : {r['nb_medicaments']:,}".replace(",", " "))
    print(f"   Priorites distinctes     : {r['nb_priorites_distinctes']}")
    print(f"   Plage de priorite        : {r['priorite_min']:.4f} -> {r['priorite_max']:.4f}"
          f"   (moyenne {r['priorite_moyenne']:.4f})")
    print(f"   Chaine du froid          : {r['nb_chaine_froid']} references "
          f"({r['part_chaine_froid']*100:.1f} %)")
    print(f"   Rescale applique         : {'oui' if cfg['priorite']['rescale'] else 'non (valeurs brutes)'}")

    # --- 3.2 Reseau --------------------------------------------------------
    with open(resoudre(ch["reseau_json"]), "r", encoding="utf-8") as f:
        reseau = json.load(f)
    depot = reseau["depot"]
    pharmacies = reseau["pharmacies"]
    noms_par_id = {p["id"]: p["nom"] for p in pharmacies}

    # --- 3.3 Demandes ------------------------------------------------------
    chemin_demandes = resoudre(ch["demandes_csv"])
    if args.simuler or not chemin_demandes.exists():
        if not args.simuler:
            print(f"\n [!] {chemin_demandes.name} absent — generation d'un jeu simule.")
        commandes = simuler_demandes(catalogue, pharmacies, cfg["simulation"])
        exporter_demandes(commandes, chemin_demandes)
        print(f"\n DEMANDES : {sum(c.nb_lignes for c in commandes.values())} lignes "
              f"simulees -> {chemin_demandes.name}")
    else:
        commandes = charger_demandes(
            chemin_demandes, catalogue, noms_par_id, ignorer_amm_inconnus=True
        )
        print(f"\n DEMANDES : {sum(c.nb_lignes for c in commandes.values())} lignes "
              f"reelles lues depuis {chemin_demandes.name}")

    # --- 3.4 Scenario ------------------------------------------------------
    scenario, noms, cmds = construire_scenario(depot, pharmacies, commandes, cfg)
    afficher_commandes(scenario, noms, cmds, cfg["priorite"]["agregation"])

    # --- 3.5 Geometrie -----------------------------------------------------
    g = cfg["geometrie"]
    d, t = build_matrix_calibrated(
        scenario.coords,
        sinuosite=g["facteur_sinuosite"],
        vitesse_kmh=g["vitesse_effective_kmh"],
    )
    print(f"\n GEOMETRIE : haversine x {g['facteur_sinuosite']} "
          f"@ {g['vitesse_effective_kmh']} km/h (calibration TomTom)")

    # --- 3.6 Resolution ----------------------------------------------------
    fl = cfg["flotte"]
    fleet = Fleet(Q=fl["Q"], R=fl["R"], c_f=fl["c_f"])
    sv = dict(cfg["solveur"])
    if args.time_limit is not None:
        sv["time_limit"] = args.time_limit

    print(f" FLOTTE    : {len(fl['Q'])} vehicules "
          f"({sum(fl['R'])} refrigeres) — capacites {fl['Q']}")

    charge = sum(scenario.q[1:])
    print(f" CHARGE    : {charge:.1f} unites a livrer "
          f"pour {sum(fl['Q']):.1f} de capacite "
          f"(taux d'occupation {charge / sum(fl['Q']) * 100:.0f} %)")

    alertes = controle_faisabilite(scenario, fleet)
    if alertes:
        print("\n" + "!" * 78)
        print(" CONTROLE DE FAISABILITE — LE MILP N'ADMET AUCUNE SOLUTION")
        print("!" * 78)
        for a in alertes:
            print(f"\n  * {a}")
        print("\n Resolution abandonnee : corriger la configuration ci-dessus.\n")
        return 2

    print(f"\n Resolution du MILP (plafond {sv['time_limit']} s)...\n")

    solution = build_and_solve_vrp(scenario, fleet, d, t, **sv)
    print_report(scenario, fleet, solution, d)

    # --- 3.7 Restitution ---------------------------------------------------
    print("\n TOURNEES RETENUES\n")
    for k, tour in sorted(solution.routes.items()):
        etapes = " -> ".join(
            noms[i].replace("Pharmacie ", "P. ")[:22] for i in tour
        )
        print(f"   Vehicule {k} : {etapes}")

    dossier = resoudre(ch["dossier_sortie"])
    dossier.mkdir(parents=True, exist_ok=True)
    horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")

    chemin_json = dossier / f"tournees_{horodatage}.json"
    with open(chemin_json, "w", encoding="utf-8") as f:
        json.dump(
            {
                "horodatage": horodatage,
                "statut": solution.status,
                "objectif": solution.objective,
                "noeuds": [
                    {
                        "index": i,
                        "nom": noms[i],
                        "priorite": scenario.pr[i],
                        "chaine_du_froid": scenario.cc[i],
                        "charge": scenario.q[i],
                        "sla_min": scenario.T[i],
                        "arrivee_min": solution.arrival.get(i),
                        "retard_min": solution.lateness.get(i),
                        "nb_lignes": cmds[i].nb_lignes if cmds[i] else 0,
                    }
                    for i in range(1, scenario.n_nodes)
                ],
                "tournees": {str(k): v for k, v in solution.routes.items()},
            },
            f, ensure_ascii=False, indent=2,
        )
    print(f"\n Resultat structure : outputs/{chemin_json.name}")

    if not args.no_carte:
        try:
            chemin_carte = dossier / f"carte_{horodatage}.html"
            fig = plot_solution(
                scenario, fleet, solution,
                title="MILP CARE — tournees a priorites dynamiques",
            )
            fig.write_html(str(chemin_carte))
            print(f" Carte interactive  : outputs/{chemin_carte.name}")
        except Exception as exc:  # plotly optionnel : ne doit jamais bloquer
            print(f" [!] Carte non generee ({type(exc).__name__}: {exc})")

    print()
    return 0 if solution.status == "Optimal" else 1


if __name__ == "__main__":
    sys.exit(main())
