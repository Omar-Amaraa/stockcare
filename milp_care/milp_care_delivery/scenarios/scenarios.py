"""
=============================================================================
 SCENARIOS PEDAGOGIQUES — LECTURE DES DECISIONS DU MILP
=============================================================================
 Chaque scenario isole UN levier de decision du modele et pose une question
 dont la reponse n'est pas evidente a l'oeil nu :

   S1  PRIORITE vs PROXIMITE   la pharmacie la plus proche n'est pas servie
                               en premier — pourquoi ?
   S2  CHAINE DU FROID         deux vehicules refrigeres sur trois : comment
                               le froid partitionne-t-il la flotte ?
   S3  COUT D'UN VEHICULE      quand mobiliser un camion de plus devient-il
                               rentable ?
   S4  ARBITRAGE SLA           quand tout le monde ne peut pas etre servi a
                               l'heure, qui le modele sacrifie-t-il ?

 Pour chaque scenario on resout le MILP, puis on genere des tournees
 ALTERNATIVES qu'on chiffre avec la meme fonction objectif. La justification
 devient alors verifiable : « cette route coute X de plus, dont Y sur le
 terme de retard ».

 LANCEMENT
   py scenarios/scenarios.py                 tous les scenarios
   py scenarios/scenarios.py --scenario S1   un seul
   py scenarios/scenarios.py --rapport       ecrit aussi un rapport Markdown
   py scenarios/scenarios.py --top 5         nb d'alternatives detaillees
=============================================================================
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Callable, Dict, List, Sequence

RACINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RACINE / "src"))

from catalogue import Catalogue, ReglesChaineFroid          # noqa: E402
from demandes import CommandePharmacie, LigneCommande, construire_scenario  # noqa: E402
from explication import (                                    # noqa: E402
    ParametresObjectif, analyser, evaluer_tournees, redaction_motif,
)
from geometrie import build_matrix_calibrated                # noqa: E402
from vrp_milp_pharma import Fleet, build_and_solve_vrp       # noqa: E402


# =============================================================================
# 1. REFERENCES MEDICAMENTEUSES UTILISEES PAR LES SCENARIOS
# =============================================================================
# Numeros AMM reels, extraits de pharmacy_delivery_priorities.csv.
# Les priorites indiquees sont celles du referentiel, pas des valeurs choisies.

AMM = {
    "insuline":    "5993016",     # ACTRAPID              pr=0.662  FROID
    "enoxaparine": "9233491",     # ENOXA 2000 UI         pr=0.616  FROID
    "vaccin":      "24893023EP",  # COMIRNATY XBB.1.5     pr=0.428  FROID
    "clopidogrel": "10243101",    # AGREGEX               pr=0.475
    "amoxicilline": "9163901",    # ACAMANTINE 625        pr=0.344
    "paracetamol": "9103471",     # DOLIRHUME             pr=0.341
    "vitamineC":   "9000451H",    # VITAMINE C            pr=0.310
    "nicotine":    "6063314",     # NICOPASS              pr=0.058
}


# =============================================================================
# 2. DEFINITION D'UN SCENARIO
# =============================================================================


@dataclass
class ScenarioTest:
    """Un cas d'etude : des commandes choisies + une question a trancher."""

    code: str
    titre: str
    question: str
    enseignement: str
    commandes_brutes: Dict[str, List[tuple]]   # pharmacie_id -> [(cle_amm, qte)]
    flotte: Dict[str, list] | None = None      # None = flotte de config.json
    solveur: Dict[str, float] = field(default_factory=dict)  # surcharge w_p/w_v/gamma
    sla: Dict[str, float] = field(default_factory=dict)      # surcharge des ancrages SLA
    focus: List[str] = field(default_factory=list)
    balayage: tuple[str, List[float]] | None = None  # (parametre, valeurs) a sweeper


# =============================================================================
# 3. CATALOGUE DES SCENARIOS
# =============================================================================


SCENARIOS: List[ScenarioTest] = [

    ScenarioTest(
        code="S1",
        titre="Priorite contre proximite",
        question=(
            "PH10 (Nenupharma) est la pharmacie la plus proche du depot, mais "
            "elle ne commande que de la nicotine (pr=0.058). PH01 (Hafedh "
            "Frigui) est la plus eloignee et attend de l'insuline (pr=0.662). "
            "Laquelle le modele sert-il en premier ?"
        ),
        enseignement=(
            "Le terme de reactivite (gamma x pr[i] x s[i]) fait payer l'attente "
            "PROPORTIONNELLEMENT a la priorite. Faire patienter l'insuline coute "
            "11 fois plus cher que faire patienter la nicotine, a heure egale. "
            "Le detour kilometrique est donc rentable."
        ),
        commandes_brutes={
            "PH01": [("insuline", 12)],
            "PH10": [("nicotine", 10)],
            "PH05": [("paracetamol", 8)],
        },
        flotte={"Q": [120.0], "R": [1], "c_f": [1.50]},
        focus=["ordre"],
    ),

    ScenarioTest(
        code="S2",
        titre="La chaine du froid partitionne la flotte",
        question=(
            "Quatre pharmacies commandent du thermosensible, deux non. La flotte "
            "compte deux vehicules refrigeres et un vehicule sec, moins cher au "
            "kilometre. Comment le froid contraint-il l'affectation ?"
        ),
        enseignement=(
            "La contrainte (5) du MILP interdit tout arc vers un noeud cc=1 pour "
            "un vehicule R=0. Cette contrainte ne se lit PAS dans les couts : "
            "elle se lit dans les alternatives declarees infaisables. Le "
            "vehicule sec a beau etre le moins cher au kilometre (1.10 contre "
            "1.85), aucun euro d'economie ne peut lui faire transporter un "
            "vaccin. La demande refrigeree (118 unites) depassant la capacite "
            "du premier camion frigorifique (90), le modele est contraint d'en "
            "activer un second — meme si un seul aurait suffi en volume total."
        ),
        commandes_brutes={
            "PH01": [("insuline", 35)],
            "PH02": [("vaccin", 30)],
            "PH03": [("enoxaparine", 28)],
            "PH04": [("vaccin", 25)],
            "PH08": [("paracetamol", 15)],
            "PH09": [("vitamineC", 18)],
        },
        focus=["affectation"],
    ),

    ScenarioTest(
        code="S3",
        titre="Pourquoi le modele refuse un second vehicule",
        question=(
            "Sept pharmacies sous SLA resserre : la tournee optimale accuse deux "
            "retards. Un second vehicule les supprimerait. Jusqu'ou faut-il "
            "baisser w_v (le cout d'activation) pour que le modele accepte de "
            "fractionner ?"
        ),
        enseignement=(
            "Reponse contre-intuitive : AUCUNE valeur de w_v ne suffit, meme "
            "gratuit (w_v=0). La raison est geometrique — les dix pharmacies de "
            "Sousse forment une grappe a 4-5 km du depot, alors que les sauts "
            "intra-grappe font moins d'1 km. Un second vehicule paie donc un "
            "aller-retour au depot (~9 km, soit ~10 de transport) pour "
            "economiser au mieux ~7 de retard : l'operation n'est jamais "
            "rentable. CONSEQUENCE OPERATIONNELLE : sur ce reseau, w_v n'est pas "
            "le levier qui pilote la taille de flotte. Seules la CAPACITE et la "
            "CHAINE DU FROID forcent reellement un second vehicule (cf. S2). "
            "Pour ameliorer le SLA, il faut rapprocher le depot ou relacher les "
            "fenetres, pas ajouter des camions."
        ),
        commandes_brutes={
            "PH01": [("clopidogrel", 14)],
            "PH02": [("amoxicilline", 16)],
            "PH05": [("paracetamol", 13)],
            "PH06": [("vitamineC", 15)],
            "PH07": [("amoxicilline", 12)],
            "PH08": [("clopidogrel", 11)],
            "PH09": [("paracetamol", 14)],
        },
        # Aucune commande refrigeree ici : deux vehicules secs suffisent a poser
        # la question « faut-il fractionner ? » sans alourdir le modele.
        flotte={"Q": [110.0, 90.0], "R": [0, 0], "c_f": [1.10, 1.30]},
        # SLA volontairement resserre : c'est la pression temporelle qui rend
        # le second vehicule interessant, pas le kilometrage.
        sla={"priorite_haute": 0.48, "fenetre_haute_min": 25.0,
             "priorite_basse": 0.30, "fenetre_basse_min": 55.0},
        solveur={"w_v": 5.0},
        balayage=("w_v", [0.0, 25.0, 50.0]),
        focus=["flotte"],
    ),

    ScenarioTest(
        code="S4",
        titre="Qui sacrifier quand le SLA est intenable ?",
        question=(
            "Un seul vehicule pour six pharmacies dont trois urgentes aux quatre "
            "coins de la ville. Le retard est mathematiquement inevitable. Sur "
            "qui le modele choisit-il de le faire porter ?"
        ),
        enseignement=(
            "La penalite de retard vaut w_p x pr[i] x L[i] : une minute de retard "
            "sur l'insuline (pr=0.662) coute 11 fois une minute de retard sur la "
            "nicotine (pr=0.058). Le modele concentre donc le retard sur les "
            "noeuds les moins prioritaires — comportement voulu, mais qu'il faut "
            "assumer explicitement en exploitation."
        ),
        commandes_brutes={
            "PH01": [("insuline", 8)],
            "PH03": [("enoxaparine", 7)],
            "PH05": [("vaccin", 9)],
            "PH07": [("nicotine", 6)],
            "PH08": [("vitamineC", 7)],
            "PH09": [("paracetamol", 8)],
        },
        flotte={"Q": [90.0], "R": [1], "c_f": [1.60]},
        # Fenetres deliberement intenables : la tournee complete dure ~48 min
        # alors que la fenetre la plus large en vaut 38. Quelqu'un SERA en retard.
        sla={"priorite_haute": 0.66, "fenetre_haute_min": 16.0,
             "priorite_basse": 0.06, "fenetre_basse_min": 38.0,
             "bonus_chaine_froid_min": 0.0, "plancher_min": 10.0},
        focus=["ordre"],
    ),
]


# =============================================================================
# 4. MOTEUR D'EXECUTION
# =============================================================================


def construire(sc: ScenarioTest, cfg: dict, catalogue: Catalogue, reseau: dict):
    """Traduit un ScenarioTest en (scenario MILP, noms, flotte, matrices)."""
    pharmacies = [p for p in reseau["pharmacies"] if p["id"] in sc.commandes_brutes]
    manquantes = set(sc.commandes_brutes) - {p["id"] for p in pharmacies}
    if manquantes:
        raise ValueError(
            f"{sc.code} : pharmacie(s) {sorted(manquantes)} absente(s) de "
            f"reseau_sousse.json"
        )

    commandes: Dict[str, CommandePharmacie] = {}
    for pharma in pharmacies:
        pid = pharma["id"]
        cmd = CommandePharmacie(pharmacie_id=pid, nom=pharma["nom"])
        for cle, qte in sc.commandes_brutes[pid]:
            med = catalogue.par_amm(AMM[cle])
            cmd.lignes.append(LigneCommande(AMM[cle], float(qte), med))
        commandes[pid] = cmd

    scenario, noms, cmds = construire_scenario(
        reseau["depot"], pharmacies, commandes, cfg
    )

    fl = sc.flotte or cfg["flotte"]
    fleet = Fleet(Q=list(fl["Q"]), R=list(fl["R"]), c_f=list(fl["c_f"]))

    g = cfg["geometrie"]
    d, t = build_matrix_calibrated(
        scenario.coords,
        sinuosite=g["facteur_sinuosite"],
        vitesse_kmh=g["vitesse_effective_kmh"],
    )
    return scenario, noms, cmds, fleet, d, t


def court(noms: Sequence[str], i: int) -> str:
    if i == 0:
        return "DEPOT"
    return noms[i].replace("Pharmacie ", "P. ")[:22]


def executer(
    sc: ScenarioTest,
    cfg: dict,
    catalogue: Catalogue,
    reseau: dict,
    *,
    top: int = 4,
    time_limit: int = 60,
    ecrire: Callable[[str], None] = print,
) -> dict:
    """Resout un scenario et redige l'explication de la decision."""
    # Surcharge locale : chaque scenario isole SON levier sans polluer la config.
    cfg_local = json.loads(json.dumps(cfg))
    cfg_local["solveur"].update(sc.solveur)
    cfg_local["sla"].update(sc.sla)

    scenario, noms, cmds, fleet, d, t = construire(sc, cfg_local, catalogue, reseau)
    params = ParametresObjectif.depuis_config(cfg_local)

    ecrire("")
    ecrire("=" * 78)
    ecrire(f" {sc.code} — {sc.titre.upper()}")
    ecrire("=" * 78)
    ecrire("")
    ecrire(" QUESTION")
    for ligne in _envelopper(sc.question, 74):
        ecrire(f"   {ligne}")

    # --- Donnees d'entree --------------------------------------------------
    ecrire("")
    ecrire(" COMMANDES")
    ecrire(f"   {'#':>2}  {'PHARMACIE':24s} {'CHARGE':>7} {'FROID':>6} "
           f"{'PRIORITE':>9} {'SLA':>7}   ARTICLE")
    for i in range(1, scenario.n_nodes):
        crit = cmds[i].article_critique()
        ecrire(f"   {i:2d}  {court(noms, i):24s} {scenario.q[i]:7.1f} "
               f"{'OUI' if scenario.cc[i] else '  -':>6} {scenario.pr[i]:9.4f} "
               f"{scenario.T[i]:7.1f}   {crit.nom_specialite[:26] if crit else '-'}")

    ecrire("")
    ecrire(f" FLOTTE   : {fleet.n_vehicles} vehicule(s) — "
           f"capacites {fleet.Q}, refrigeres {fleet.R}, couts {fleet.c_f}")
    ecrire(f" DISTANCE depot -> arrets (km) : " + ", ".join(
        f"{court(noms, i)}={d[0][i]:.2f}" for i in range(1, scenario.n_nodes)))

    # --- Resolution --------------------------------------------------------
    sv = dict(cfg_local["solveur"])
    sv["time_limit"] = time_limit
    if sc.solveur:
        ecrire(" POIDS    : " + ", ".join(f"{k}={v}" for k, v in sc.solveur.items())
               + "  (surcharge propre au scenario)")
    solution = build_and_solve_vrp(scenario, fleet, d, t, **sv)

    if solution.status != "Optimal":
        ecrire("")
        ecrire(f" [!] STATUT SOLVEUR : {solution.status} — scenario non exploitable.")
        return {"code": sc.code, "statut": solution.status}

    ref = evaluer_tournees(solution.routes, scenario, fleet, d, t, params)

    ecrire("")
    ecrire(" DECISION DU MODELE")
    for k, tour in sorted(solution.routes.items()):
        if len(tour) <= 2:
            continue
        froid = " [REFRIGERE]" if fleet.R[k] else " [SEC]"
        ecrire(f"   Vehicule {k}{froid}")
        ecrire(f"     {' -> '.join(court(noms, i) for i in tour)}")
        for i in tour[1:-1]:
            marge = scenario.T[i] - ref.arrivees[i]
            etat = f"marge {marge:+.1f} min" if marge >= 0 else f"RETARD {-marge:.1f} min"
            ecrire(f"       {court(noms, i):24s} arrivee {ref.arrivees[i]:6.1f} min"
                   f" | SLA {scenario.T[i]:6.1f} | {etat}")

    ecrire("")
    ecrire(f" COUT TOTAL : {ref.total:8.2f}")
    ecrire(f"    transport   {ref.transport:8.2f}   ({ref.distance_km:.2f} km)")
    ecrire(f"    retard SLA  {ref.retard:8.2f}")
    ecrire(f"    reactivite  {ref.reactivite:8.2f}   (attente ponderee par priorite)")
    ecrire(f"    flotte      {ref.flotte:8.2f}   ({ref.vehicules_utilises} vehicule(s))")

    # --- Coherence evaluateur / solveur -----------------------------------
    ecart = abs(ref.total - solution.objective)
    marqueur = "OK" if ecart < 0.01 else f"ECART {ecart:.4f}"
    ecrire(f"    [controle] objectif solveur = {solution.objective:.2f}  -> {marqueur}")

    # --- Alternatives ------------------------------------------------------
    _, comparaisons = analyser(solution.routes, scenario, fleet, d, t, params, noms)
    retenues = [c for c in comparaisons
                if not sc.focus or c.alternative.famille in sc.focus]
    if not retenues:
        retenues = comparaisons

    ecrire("")
    ecrire(" POURQUOI PAS AUTREMENT ?")
    ecrire(f"   {len(comparaisons)} tournees alternatives evaluees avec la meme "
           f"fonction objectif.")
    ecrire("")

    for c in retenues[:top]:
        ecrire(f"   > {c.alternative.libelle}")
        for ligne in _envelopper(redaction_motif(c, params), 70):
            ecrire(f"     {ligne}")
        if not c.rejetee_pour_infaisabilite:
            detail = "  ".join(
                f"{cle}={val:+.2f}" for cle, val in c.deltas.items()
                if abs(val) > 1e-9
            )
            if detail:
                ecrire(f"     detail : {detail}")
        ecrire("")

    infaisables = [c for c in comparaisons if c.rejetee_pour_infaisabilite]
    if infaisables:
        ecrire(f"   {len(infaisables)} alternative(s) ecartee(s) d'office "
               f"(contrainte violee, pas un arbitrage de cout). Exemple :")
        ex = infaisables[0]
        ecrire(f"     > {ex.alternative.libelle}")
        for v in ex.alternative.evaluation.violations[:2]:
            for ligne in _envelopper(v, 68):
                ecrire(f"       {ligne}")
        ecrire("")

    # --- Balayage parametrique ---------------------------------------------
    balayage_resultats = []
    if sc.balayage:
        nom_param, valeurs = sc.balayage
        ecrire(f" BALAYAGE DE {nom_param}")
        ecrire(f"   Meme instance resolue a differentes valeurs de {nom_param} :")
        ecrire("")
        ecrire(f"   {nom_param:>10}  {'VEHIC.':>7}  {'COUT':>9}  {'KM':>7}  "
               f"{'RETARD':>8}  TOURNEES")
        precedent = None
        for val in valeurs:
            sv_b = dict(cfg_local["solveur"])
            sv_b[nom_param] = val
            sv_b["time_limit"] = max(5, time_limit // 3)
            sol_b = build_and_solve_vrp(scenario, fleet, d, t, **sv_b)
            if sol_b.status != "Optimal":
                ecrire(f"   {val:10.1f}  {sol_b.status}")
                continue
            p_b = ParametresObjectif(
                H_max=sv_b["H_max"], w_p=sv_b["w_p"],
                gamma=sv_b["gamma"], w_v=sv_b["w_v"],
            )
            ev_b = evaluer_tournees(sol_b.routes, scenario, fleet, d, t, p_b)
            actifs = sorted(k for k, tr in sol_b.routes.items() if len(tr) > 2)
            bascule = "  <-- BASCULE" if precedent is not None and \
                ev_b.vehicules_utilises != precedent else ""
            ecrire(f"   {val:10.1f}  {ev_b.vehicules_utilises:7d}  "
                   f"{ev_b.total:9.2f}  {ev_b.distance_km:7.2f}  "
                   f"{ev_b.retard:8.2f}  V{actifs}{bascule}")
            precedent = ev_b.vehicules_utilises
            balayage_resultats.append({
                "valeur": val,
                "vehicules": ev_b.vehicules_utilises,
                "cout": round(ev_b.total, 2),
                "km": round(ev_b.distance_km, 2),
            })
        ecrire("")

    ecrire(" CE QU'IL FAUT RETENIR")
    for ligne in _envelopper(sc.enseignement, 74):
        ecrire(f"   {ligne}")
    ecrire("")

    return {
        "code": sc.code,
        "titre": sc.titre,
        "statut": solution.status,
        "cout_total": round(ref.total, 2),
        "composantes": {k: round(v, 2) for k, v in ref.composantes().items()},
        "distance_km": round(ref.distance_km, 2),
        "tournees": {str(k): v for k, v in solution.routes.items()
                     if len(v) > 2},
        "alternatives_evaluees": len(comparaisons),
        "alternatives_infaisables": len(infaisables),
        "balayage": balayage_resultats,
    }


def _envelopper(texte: str, largeur: int) -> List[str]:
    """Decoupe un paragraphe en lignes sans couper les mots."""
    mots, lignes, courante = texte.split(), [], ""
    for mot in mots:
        if len(courante) + len(mot) + 1 > largeur:
            lignes.append(courante)
            courante = mot
        else:
            courante = f"{courante} {mot}".strip()
    if courante:
        lignes.append(courante)
    return lignes


# =============================================================================
# 5. POINT D'ENTREE
# =============================================================================


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Scenarios pedagogiques — explication des choix du MILP"
    )
    ap.add_argument("--scenario", default=None,
                    help="code d'un scenario (S1, S2, S3, S4)")
    ap.add_argument("--top", type=int, default=4,
                    help="nombre d'alternatives detaillees par scenario")
    ap.add_argument("--time-limit", type=int, default=60)
    ap.add_argument("--rapport", action="store_true",
                    help="ecrit aussi outputs/scenarios_<horodatage>.md")
    args = ap.parse_args()

    with open(RACINE / "config" / "config.json", encoding="utf-8") as f:
        cfg = json.load(f)
    regles = ReglesChaineFroid.depuis_json(RACINE / "config" / "cold_chain_rules.json")
    catalogue = Catalogue.charger(
        RACINE / "data" / "pharmacy_delivery_priorities.csv", regles,
        rescale=cfg["priorite"]["rescale"],
    )
    with open(RACINE / "data" / "reseau_sousse.json", encoding="utf-8") as f:
        reseau = json.load(f)

    choisis = SCENARIOS
    if args.scenario:
        choisis = [s for s in SCENARIOS if s.code.upper() == args.scenario.upper()]
        if not choisis:
            print(f"Scenario inconnu : {args.scenario}. "
                  f"Disponibles : {', '.join(s.code for s in SCENARIOS)}")
            return 1

    tampon: List[str] = []

    def ecrire(ligne: str = "") -> None:
        print(ligne)
        tampon.append(ligne)

    ecrire("=" * 78)
    ecrire(" MILP CARE — SCENARIOS D'EXPLICATION DES DECISIONS DE ROUTAGE")
    ecrire("=" * 78)
    ecrire(f" Execution : {datetime.now():%Y-%m-%d %H:%M:%S}")
    ecrire(f" Catalogue : {len(catalogue)} medicaments, "
           f"{catalogue.resume()['nb_priorites_distinctes']} priorites distinctes")
    ecrire(" Methode   : chaque alternative est chiffree avec la fonction "
           "objectif exacte")
    ecrire("             du MILP, puis l'ecart est decompose terme a terme.")

    resultats = []
    for sc in choisis:
        resultats.append(executer(
            sc, cfg, catalogue, reseau,
            top=args.top, time_limit=args.time_limit, ecrire=ecrire,
        ))

    ecrire("=" * 78)
    ecrire(" SYNTHESE")
    ecrire("=" * 78)
    ecrire(f"   {'CODE':5s} {'TITRE':38s} {'COUT':>9} {'KM':>7} {'ALT.':>6}")
    for r in resultats:
        if r.get("statut") != "Optimal":
            ecrire(f"   {r['code']:5s} {'(non resolu)':38s} {r.get('statut','?')}")
            continue
        ecrire(f"   {r['code']:5s} {r['titre'][:38]:38s} {r['cout_total']:9.2f} "
               f"{r['distance_km']:7.2f} {r['alternatives_evaluees']:6d}")
    ecrire("")

    if args.rapport:
        dossier = RACINE / "outputs"
        dossier.mkdir(exist_ok=True)
        horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
        chemin = dossier / f"scenarios_{horodatage}.md"
        with open(chemin, "w", encoding="utf-8") as f:
            f.write("# MILP CARE — Explication des decisions de routage\n\n")
            f.write("```\n" + "\n".join(tampon) + "\n```\n")
        with open(dossier / f"scenarios_{horodatage}.json", "w", encoding="utf-8") as f:
            json.dump(resultats, f, ensure_ascii=False, indent=2)
        print(f" Rapport : outputs/{chemin.name}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
