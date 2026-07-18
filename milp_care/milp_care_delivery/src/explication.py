"""
=============================================================================
 EXPLICABILITE — POURQUOI CETTE TOURNEE PLUTOT QU'UNE AUTRE ?
=============================================================================
 Un MILP renvoie une tournee optimale sans jamais dire POURQUOI les autres
 ont ete ecartees. Ce module reconstruit cette justification.

 Principe :
   1. On reimplemente EXACTEMENT la fonction objectif du MILP, de facon a
      pouvoir chiffrer n'importe quelle tournee candidate (pas seulement
      celle qu'a trouvee le solveur).
   2. On genere des ALTERNATIVES credibles autour de l'optimum : echanger
      deux arrets, deplacer un arret vers un autre vehicule, inverser un
      segment, supprimer un vehicule.
   3. On evalue chaque alternative et on decompose l'ecart de cout terme a
      terme. La reponse a « pourquoi pas cette route ? » devient alors un
      nombre, pas une opinion.

 Rappel de l'objectif (identique a build_and_solve_vrp) :

     min   SUM c_f[k] * d[i][j] * x[i,j,k]      <- transport
         + SUM w_p * pr[i] * L[i]               <- depassement SLA
         + SUM gamma * pr[i] * s[i]             <- reactivite
         + SUM w_v * z[k]                       <- activation vehicule

 Le terme de reactivite est celui qui fait le plus souvent basculer une
 decision : meme sans aucun retard, servir TARD une pharmacie prioritaire
 coute plus cher que servir tard une pharmacie de confort.

 Auteur : Adam — Projet MILP CARE
=============================================================================
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Sequence, Tuple


# =============================================================================
# 1. PARAMETRES ET RESULTAT D'EVALUATION
# =============================================================================


@dataclass(frozen=True)
class ParametresObjectif:
    """Poids de la fonction objectif — a synchroniser avec config.json."""

    H_max: float = 480.0
    w_p: float = 5.0       # poids du depassement SLA
    gamma: float = 0.10    # poids de l'heure d'arrivee (reactivite)
    w_v: float = 50.0      # cout fixe d'activation d'un vehicule

    @classmethod
    def depuis_config(cls, cfg: Dict) -> "ParametresObjectif":
        sv = cfg["solveur"]
        return cls(H_max=sv["H_max"], w_p=sv["w_p"],
                   gamma=sv["gamma"], w_v=sv["w_v"])


@dataclass
class Evaluation:
    """Cout decompose d'un jeu de tournees, faisable ou non."""

    faisable: bool
    violations: List[str] = field(default_factory=list)

    transport: float = 0.0
    retard: float = 0.0
    reactivite: float = 0.0
    flotte: float = 0.0

    arrivees: Dict[int, float] = field(default_factory=dict)
    retards: Dict[int, float] = field(default_factory=dict)
    distance_km: float = 0.0
    vehicules_utilises: int = 0

    @property
    def total(self) -> float:
        return self.transport + self.retard + self.reactivite + self.flotte

    def composantes(self) -> Dict[str, float]:
        return {
            "transport": self.transport,
            "retard": self.retard,
            "reactivite": self.reactivite,
            "flotte": self.flotte,
        }


# =============================================================================
# 2. EVALUATEUR EXACT
# =============================================================================


def evaluer_tournees(
    routes: Dict[int, Sequence[int]],
    scenario,
    fleet,
    d: List[List[float]],
    t: List[List[float]],
    params: ParametresObjectif,
) -> Evaluation:
    """Chiffre un jeu de tournees avec la fonction objectif exacte du MILP.

    Les heures d'arrivee sont obtenues par propagation le long de chaque
    tournee : s[j] = s[i] + sigma[i] + t[i][j], avec s[depot] = 0. C'est la
    borne que le solveur atteint necessairement, puisqu'il minimise s[i].

    Args:
        routes: k -> [0, i, j, ..., 0]. Les vehicules absents sont inactifs.

    Returns:
        Evaluation : faisabilite, violations explicites et cout decompose.
    """
    ev = Evaluation(faisable=True)
    n = scenario.n_nodes

    # --- 2.1 Couverture : chaque client servi exactement une fois ----------
    visites: Dict[int, int] = {}
    for k, tour in routes.items():
        for i in tour[1:-1]:
            visites[i] = visites.get(i, 0) + 1

    for i in range(1, n):
        c = visites.get(i, 0)
        if c == 0:
            ev.faisable = False
            ev.violations.append(f"le noeud {i} n'est servi par aucun vehicule")
        elif c > 1:
            ev.faisable = False
            ev.violations.append(f"le noeud {i} est servi {c} fois")

    # --- 2.2 Contraintes par vehicule -------------------------------------
    for k, tour in routes.items():
        if len(tour) <= 2:
            continue  # vehicule non active
        ev.vehicules_utilises += 1

        if tour[0] != 0 or tour[-1] != 0:
            ev.faisable = False
            ev.violations.append(f"la tournee du vehicule {k} ne boucle pas au depot")

        charge = sum(scenario.q[i] for i in tour[1:-1])
        if charge > fleet.Q[k] + 1e-6:
            ev.faisable = False
            ev.violations.append(
                f"CAPACITE : vehicule {k} charge a {charge:.1f} pour "
                f"{fleet.Q[k]:.1f} de capacite (depassement {charge - fleet.Q[k]:.1f})"
            )

        if fleet.R[k] == 0:
            froids = [i for i in tour[1:-1] if scenario.cc[i] == 1]
            if froids:
                ev.faisable = False
                ev.violations.append(
                    f"CHAINE DU FROID : vehicule {k} n'est pas refrigere mais "
                    f"dessert le(s) noeud(s) thermosensible(s) {froids}"
                )

    # --- 2.3 Propagation temporelle ---------------------------------------
    ev.arrivees[0] = 0.0
    for k, tour in routes.items():
        if len(tour) <= 2:
            continue
        horloge = 0.0
        precedent = 0
        for i in tour[1:-1]:
            horloge += scenario.sigma[precedent] + t[precedent][i]
            ev.arrivees[i] = horloge
            precedent = i

    for i in range(1, n):
        s_i = ev.arrivees.get(i)
        if s_i is None:
            continue
        ev.retards[i] = max(0.0, s_i - scenario.T[i])
        if s_i > params.H_max + 1e-6:
            ev.faisable = False
            ev.violations.append(
                f"HORIZON : le noeud {i} est atteint a {s_i:.1f} min, "
                f"au-dela de H_max = {params.H_max:.0f} min"
            )

    # --- 2.4 Cout decompose -----------------------------------------------
    for k, tour in routes.items():
        if len(tour) <= 2:
            continue
        for a, b in zip(tour[:-1], tour[1:]):
            ev.transport += fleet.c_f[k] * d[a][b]
            ev.distance_km += d[a][b]

    for i in range(1, n):
        if i not in ev.arrivees:
            continue
        ev.retard += params.w_p * scenario.pr[i] * ev.retards[i]
        ev.reactivite += params.gamma * scenario.pr[i] * ev.arrivees[i]

    ev.flotte = params.w_v * ev.vehicules_utilises

    return ev


# =============================================================================
# 3. GENERATION D'ALTERNATIVES
# =============================================================================


@dataclass
class Alternative:
    """Une tournee candidate, sa nature et son evaluation."""

    libelle: str
    famille: str                    # "ordre" | "affectation" | "flotte"
    routes: Dict[int, List[int]]
    evaluation: Evaluation | None = None


def _copier(routes: Dict[int, Sequence[int]]) -> Dict[int, List[int]]:
    return {k: list(v) for k, v in routes.items()}


def generer_alternatives(
    routes: Dict[int, Sequence[int]],
    scenario,
    fleet,
    noms: Sequence[str],
) -> List[Alternative]:
    """Construit les tournees voisines credibles autour de la solution.

    Trois familles, qui repondent a trois questions distinctes :
      - ordre       : « pourquoi servir A avant B ? »
      - affectation : « pourquoi ce vehicule et pas l'autre ? »
      - flotte      : « pourquoi mobiliser autant de vehicules ? »
    """
    alts: List[Alternative] = []

    def court(i: int) -> str:
        return noms[i].replace("Pharmacie ", "P. ")[:20]

    # --- 3.1 ORDRE : echanger deux arrets d'une meme tournee --------------
    for k, tour in routes.items():
        arrets = list(tour[1:-1])
        for a in range(len(arrets)):
            for b in range(a + 1, len(arrets)):
                cand = _copier(routes)
                nouveaux = list(arrets)
                nouveaux[a], nouveaux[b] = nouveaux[b], nouveaux[a]
                cand[k] = [0] + nouveaux + [0]
                alts.append(Alternative(
                    libelle=(f"V{k} : servir {court(arrets[b])} avant "
                             f"{court(arrets[a])}"),
                    famille="ordre",
                    routes=cand,
                ))

    # --- 3.2 ORDRE : inverser le sens de la tournee (2-opt global) --------
    for k, tour in routes.items():
        arrets = list(tour[1:-1])
        if len(arrets) >= 3:
            cand = _copier(routes)
            cand[k] = [0] + arrets[::-1] + [0]
            alts.append(Alternative(
                libelle=f"V{k} : parcourir la boucle en sens inverse",
                famille="ordre",
                routes=cand,
            ))

    # --- 3.3 AFFECTATION : deplacer un arret vers un autre vehicule -------
    for k, tour in routes.items():
        for i in list(tour[1:-1]):
            for k2 in range(fleet.n_vehicles):
                if k2 == k:
                    continue
                cand = _copier(routes)
                cand[k] = [n for n in cand[k] if n != i]
                autre = cand.get(k2, [0, 0])
                arrets2 = [n for n in autre[1:-1]]
                cand[k2] = [0] + arrets2 + [i] + [0]
                alts.append(Alternative(
                    libelle=f"confier {court(i)} au vehicule {k2} au lieu de {k}",
                    famille="affectation",
                    routes=cand,
                ))

    # --- 3.4 FLOTTE : rapatrier toute une tournee sur un autre vehicule ---
    actifs = [k for k, tr in routes.items() if len(tr) > 2]
    for k in actifs:
        for k2 in actifs:
            if k == k2:
                continue
            cand = _copier(routes)
            arrets_k = list(cand[k][1:-1])
            cand[k] = [0, 0]
            cand[k2] = [0] + list(cand[k2][1:-1]) + arrets_k + [0]
            alts.append(Alternative(
                libelle=f"supprimer le vehicule {k} et reporter sa tournee sur {k2}",
                famille="flotte",
                routes=cand,
            ))

    return alts


# =============================================================================
# 4. ANALYSE COMPARATIVE
# =============================================================================


@dataclass
class Comparaison:
    """Ecart chiffre entre une alternative et la solution retenue."""

    alternative: Alternative
    delta_total: float
    deltas: Dict[str, float]

    @property
    def rejetee_pour_infaisabilite(self) -> bool:
        return not self.alternative.evaluation.faisable


def analyser(
    routes_optimales: Dict[int, Sequence[int]],
    scenario,
    fleet,
    d,
    t,
    params: ParametresObjectif,
    noms: Sequence[str],
) -> Tuple[Evaluation, List[Comparaison]]:
    """Evalue l'optimum puis toutes ses alternatives, et classe les ecarts.

    Returns:
        (evaluation_optimum, comparaisons triees du plus proche au plus loin).
    """
    ref = evaluer_tournees(routes_optimales, scenario, fleet, d, t, params)

    comparaisons: List[Comparaison] = []
    for alt in generer_alternatives(routes_optimales, scenario, fleet, noms):
        alt.evaluation = evaluer_tournees(alt.routes, scenario, fleet, d, t, params)
        deltas = {
            cle: alt.evaluation.composantes()[cle] - valeur
            for cle, valeur in ref.composantes().items()
        }
        comparaisons.append(Comparaison(
            alternative=alt,
            delta_total=alt.evaluation.total - ref.total,
            deltas=deltas,
        ))

    # Les faisables d'abord, par ecart croissant : les plus « tentantes » en tete.
    comparaisons.sort(key=lambda c: (c.rejetee_pour_infaisabilite, c.delta_total))
    return ref, comparaisons


def redaction_motif(comp: Comparaison, params: ParametresObjectif) -> str:
    """Traduit un ecart chiffre en une phrase de justification."""
    if comp.rejetee_pour_infaisabilite:
        return "IMPOSSIBLE — " + " ; ".join(comp.alternative.evaluation.violations)

    if comp.delta_total < -1e-6:
        return (f"MEILLEURE de {abs(comp.delta_total):.2f} — l'optimum trouve "
                f"n'est donc pas optimal, verifier le time_limit du solveur")

    if comp.delta_total < 1e-6:
        return "EQUIVALENTE — les deux tournees coutent le meme prix"

    # Terme dominant dans la degradation
    penalisants = {c: v for c, v in comp.deltas.items() if v > 1e-9}
    if not penalisants:
        return f"PLUS CHERE de {comp.delta_total:.2f}"
    dominant = max(penalisants, key=lambda c: penalisants[c])
    ecart = penalisants[dominant]

    gabarits = {
        "transport": (f"elle allonge le trajet : +{ecart:.2f} de cout "
                      f"kilometrique"),
        "retard": (f"elle degrade le SLA : +{ecart:.2f} de penalite de retard, "
                   f"ponderee par la priorite des noeuds concernes"),
        "reactivite": (f"elle fait attendre les pharmacies les plus prioritaires : "
                       f"+{ecart:.2f} sur le terme de reactivite "
                       f"(gamma x priorite x heure d'arrivee)"),
        "flotte": f"elle mobilise un vehicule de plus : +{ecart:.2f}",
    }

    motif = gabarits.get(dominant, f"+{ecart:.2f} sur le terme '{dominant}'")

    # Un gain sur un autre poste rend l'arbitrage plus lisible.
    gains = {c: v for c, v in comp.deltas.items() if v < -1e-9}
    if gains:
        meilleur = min(gains, key=lambda c: gains[c])
        motif += (f", alors qu'elle n'economise que {abs(gains[meilleur]):.2f} "
                  f"sur le poste '{meilleur}'")

    return f"PLUS CHERE de {comp.delta_total:.2f} — {motif}"
