"""
=============================================================================
 VRP-MILP dynamique pour la distribution pharmaceutique (chaîne du froid)
=============================================================================
 Variante du Vehicle Routing Problem resolue en Programmation Lineaire
 Mixte en Nombres Entiers (MILP) avec PuLP.

 Caracteristiques :
   - Flotte heterogene (capacites, couts/km, vehicules refrigeres)
   - Contraintes de chaine du froid
   - Fenetres SLA avec penalisation du retard ponderee par la priorite
   - Elimination des sous-tours par formulation MTZ-temporelle (Big-M)
   - Geometrie ENTIEREMENT DYNAMIQUE : les matrices d[i,j] et t[i,j] sont
     recalculees a chaque execution a partir des coordonnees de l'instant t.

 Auteur : Adam - Projet MILP CARE
=============================================================================
"""

from __future__ import annotations

import math
import random
from dataclasses import dataclass, field
from typing import Dict, List, Sequence, Tuple

import networkx as nx
import plotly.graph_objects as go
import pulp

# =============================================================================
# 1. STRUCTURES DE DONNEES
# =============================================================================


@dataclass
class Scenario:
    """Photographie du reseau a l'instant t (donnees d'entree du MILP)."""

    coords: List[Tuple[float, float]]      # (X, Y) index 0 = depot
    q: List[float]                         # q[i]  : charge requise
    cc: List[int]                          # cc[i] : 1 si chaine du froid
    pr: List[float]                        # pr[i] : score de priorite [0,1]
    sigma: List[float]                     # sigma[i] : temps de dechargement
    T: List[float]                         # T[i]  : deadline SLA

    @property
    def n_nodes(self) -> int:
        return len(self.coords)


@dataclass
class Fleet:
    """Flotte heterogene de vehicules."""

    Q: List[float]        # Q[k]   : capacite
    R: List[int]          # R[k]   : 1 si refrigere
    c_f: List[float]      # c_f[k] : cout par km

    @property
    def n_vehicles(self) -> int:
        return len(self.Q)


@dataclass
class Solution:
    """Resultat de la resolution du MILP."""

    status: str
    objective: float
    routes: Dict[int, List[int]] = field(default_factory=dict)  # k -> [0, i, j, ..., 0]
    arrival: Dict[int, float] = field(default_factory=dict)     # s[i]
    lateness: Dict[int, float] = field(default_factory=dict)    # L[i]


# =============================================================================
# 2. GENERATION DU SCENARIO DYNAMIQUE (instant t)
# =============================================================================


def generate_scenario(
    n_pharmacies: int,
    *,
    area_km: float = 20.0,
    depot_at_center: bool = True,
    cold_chain_ratio: float = 0.35,
    seed: int | None = None,
) -> Scenario:
    """Genere un instantane aleatoire du reseau de commandes.

    Les localisations changent a chaque appel : rien n'est code en dur.

    Args:
        n_pharmacies: nombre de pharmacies a servir (noeuds 1..n).
        area_km: cote de la zone urbaine carree (km).
        depot_at_center: place le depot au centre plutot qu'aleatoirement.
        cold_chain_ratio: proportion attendue de commandes refrigerees.
        seed: graine pour la reproductibilite (None = vraiment aleatoire).

    Returns:
        Scenario pret a etre injecte dans le modele.
    """
    rng = random.Random(seed)

    # --- Depot (noeud 0) : localisation fixe pour l'instant t ---
    depot = (area_km / 2.0, area_km / 2.0) if depot_at_center else (
        rng.uniform(0, area_km),
        rng.uniform(0, area_km),
    )
    coords: List[Tuple[float, float]] = [depot]

    q: List[float] = [0.0]        # le depot ne consomme aucune capacite
    cc: List[int] = [0]
    pr: List[float] = [0.0]
    sigma: List[float] = [0.0]    # pas de dechargement au depot
    T: List[float] = [0.0]

    for _ in range(n_pharmacies):
        coords.append((rng.uniform(0, area_km), rng.uniform(0, area_km)))
        q.append(round(rng.uniform(5, 40), 1))                 # unites (bacs)
        cc.append(1 if rng.random() < cold_chain_ratio else 0)
        pr.append(round(rng.uniform(0.1, 1.0), 2))             # priorite [0,1]
        sigma.append(round(rng.uniform(5, 15), 1))             # minutes
        T.append(round(rng.uniform(60, 180), 1))               # SLA en minutes

    return Scenario(coords=coords, q=q, cc=cc, pr=pr, sigma=sigma, T=T)


# =============================================================================
# 3. MATRICES DYNAMIQUES DE DISTANCE ET DE TEMPS
# =============================================================================


def compute_distance_matrix(
    coords: Sequence[Tuple[float, float]],
    *,
    metric: str = "euclidean",
    avg_speed_kmh: float = 30.0,
    detour_factor: float = 1.25,
) -> Tuple[List[List[float]], List[List[float]]]:
    """Calcule d[i,j] (km) et t[i,j] (minutes) a partir des coordonnees.

    Aucune valeur n'est codee en dur : tout decoule de la geometrie courante.

    Args:
        coords: liste de (X, Y) en km, index 0 = depot.
        metric: "euclidean" (vol d'oiseau) ou "manhattan" (grille urbaine).
        avg_speed_kmh: vitesse moyenne commerciale du vehicule.
        detour_factor: coefficient de sinuosite reseau routier / vol d'oiseau.

    Returns:
        (d, t) : deux matrices n x n.
    """
    if metric not in ("euclidean", "manhattan"):
        raise ValueError("metric doit valoir 'euclidean' ou 'manhattan'")
    if avg_speed_kmh <= 0:
        raise ValueError("avg_speed_kmh doit etre strictement positif")

    n = len(coords)
    d = [[0.0] * n for _ in range(n)]
    t = [[0.0] * n for _ in range(n)]

    for i in range(n):
        xi, yi = coords[i]
        for j in range(n):
            if i == j:
                continue
            xj, yj = coords[j]
            if metric == "euclidean":
                raw = math.hypot(xj - xi, yj - yi)
            else:  # manhattan
                raw = abs(xj - xi) + abs(yj - yi)

            d[i][j] = raw * detour_factor
            # temps de trajet en MINUTES = distance / vitesse * 60
            t[i][j] = d[i][j] / avg_speed_kmh * 60.0

    return d, t


# =============================================================================
# 4. MODELE MILP
# =============================================================================


def build_and_solve_vrp(
    scenario: Scenario,
    fleet: Fleet,
    d: List[List[float]],
    t: List[List[float]],
    *,
    H_max: float = 480.0,   # horizon de planification (minutes)
    w_p: float = 5.0,       # poids du retard SLA
    gamma: float = 0.10,    # poids de l'heure d'arrivee (reactivite)
    w_v: float = 50.0,      # cout fixe d'activation d'un vehicule
    time_limit: int = 120,
    verbose: bool = False,
) -> Solution:
    """Construit et resout le MILP de routage.

    Returns:
        Solution contenant les tournees, les heures d'arrivee et les retards.
    """
    N = list(range(scenario.n_nodes))     # 0 = depot, 1..n = pharmacies
    C = N[1:]                             # clients
    K = list(range(fleet.n_vehicles))

    # --- Big-M dependant de l'arc : M[i,j] = H_max + sigma[i] + t[i,j] ---
    M = {(i, j): H_max + scenario.sigma[i] + t[i][j] for i in N for j in N if i != j}

    prob = pulp.LpProblem("Pharma_VRP_ColdChain", pulp.LpMinimize)

    # ---------------- VARIABLES DE DECISION ----------------
    arcs = [(i, j, k) for i in N for j in N for k in K if i != j]
    x = pulp.LpVariable.dicts("x", arcs, cat="Binary")
    z = pulp.LpVariable.dicts("z", K, cat="Binary")
    s = pulp.LpVariable.dicts("s", N, lowBound=0, upBound=H_max, cat="Continuous")
    L = pulp.LpVariable.dicts("L", N, lowBound=0, cat="Continuous")

    # ---------------- FONCTION OBJECTIF ----------------
    cost_transport = pulp.lpSum(
        fleet.c_f[k] * d[i][j] * x[(i, j, k)] for (i, j, k) in arcs
    )
    cost_lateness = pulp.lpSum(w_p * scenario.pr[i] * L[i] for i in C)
    cost_earliness = pulp.lpSum(gamma * scenario.pr[i] * s[i] for i in C)
    cost_fleet = pulp.lpSum(w_v * z[k] for k in K)

    prob += cost_transport + cost_lateness + cost_earliness + cost_fleet, "Cout_total"

    # ---------------- CONTRAINTES ----------------

    # (1) Single service : chaque client est visite exactement une fois
    for j in C:
        prob += (
            pulp.lpSum(x[(i, j, k)] for i in N if i != j for k in K) == 1,
            f"single_service_{j}",
        )

    # (2) Flow conservation : ce qui entre en i avec k doit en ressortir
    for k in K:
        for i in C:
            prob += (
                pulp.lpSum(x[(j, i, k)] for j in N if j != i)
                - pulp.lpSum(x[(i, j, k)] for j in N if j != i)
                == 0,
                f"flow_{i}_{k}",
            )

    # (3) Fleet activation : depart et retour au depot lies a z[k]
    for k in K:
        prob += (
            pulp.lpSum(x[(0, j, k)] for j in C) == z[k],
            f"depot_out_{k}",
        )
        prob += (
            pulp.lpSum(x[(j, 0, k)] for j in C) == z[k],
            f"depot_in_{k}",
        )

    # (4) Capacity : charge embarquee <= capacite du vehicule active
    for k in K:
        prob += (
            pulp.lpSum(
                scenario.q[j] * x[(i, j, k)] for i in N for j in C if i != j
            )
            <= fleet.Q[k] * z[k],
            f"capacity_{k}",
        )

    # (5) Cold chain : un client cc=1 ne peut etre servi que par un vehicule R=1
    for k in K:
        if fleet.R[k] == 0:
            for j in C:
                if scenario.cc[j] == 1:
                    for i in N:
                        if i != j:
                            prob += (
                                x[(i, j, k)] == 0,
                                f"coldchain_{i}_{j}_{k}",
                            )

    # (6) Time propagation & elimination des sous-tours (Big-M)
    for k in K:
        for i in N:
            for j in C:
                if i == j:
                    continue
                prob += (
                    s[j]
                    >= s[i] + scenario.sigma[i] + t[i][j] - M[(i, j)] * (1 - x[(i, j, k)]),
                    f"time_{i}_{j}_{k}",
                )

    # (7) SLA overrun : retard positif par rapport a la deadline
    for i in C:
        prob += (L[i] >= s[i] - scenario.T[i], f"sla_{i}")

    # (8) Depot clock : l'horloge demarre a 0
    prob += (s[0] == 0, "depot_clock")

    # ---------------- RESOLUTION ----------------
    solver = pulp.PULP_CBC_CMD(msg=1 if verbose else 0, timeLimit=time_limit)
    prob.solve(solver)

    status = pulp.LpStatus[prob.status]
    if status not in ("Optimal", "Not Solved"):
        return Solution(status=status, objective=float("nan"))

    # ---------------- EXTRACTION DES TOURNEES ----------------
    routes: Dict[int, List[int]] = {}
    for k in K:
        if pulp.value(z[k]) is None or pulp.value(z[k]) < 0.5:
            continue
        succ = {
            i: j
            for i in N
            for j in N
            if i != j and pulp.value(x[(i, j, k)]) is not None
            and pulp.value(x[(i, j, k)]) > 0.5
        }
        if 0 not in succ:
            continue
        tour, current = [0], succ[0]
        while current != 0 and len(tour) <= len(N):
            tour.append(current)
            current = succ.get(current, 0)
        tour.append(0)
        routes[k] = tour

    arrival = {i: float(pulp.value(s[i]) or 0.0) for i in N}
    lateness = {i: float(pulp.value(L[i]) or 0.0) for i in N}

    return Solution(
        status=status,
        objective=float(pulp.value(prob.objective)),
        routes=routes,
        arrival=arrival,
        lateness=lateness,
    )


# =============================================================================
# 5. VISUALISATION INTERACTIVE (plotly + networkx)
# =============================================================================

PALETTE = [
    "#E4572E", "#17BEBB", "#FFC914", "#2E282A",
    "#76B041", "#8367C7", "#E36414", "#0081A7",
]


def plot_solution(
    scenario: Scenario,
    fleet: Fleet,
    solution: Solution,
    *,
    title: str = "Reseau de distribution pharmaceutique optimise",
) -> go.Figure:
    """Construit un graphe interactif Plotly des tournees optimales."""
    G = nx.DiGraph()
    for i, (xc, yc) in enumerate(scenario.coords):
        G.add_node(i, pos=(xc, yc))

    fig = go.Figure()

    # ---------- ARCS : une couleur distincte par vehicule ----------
    for idx, (k, tour) in enumerate(sorted(solution.routes.items())):
        color = PALETTE[idx % len(PALETTE)]
        edge_x, edge_y = [], []
        for a, b in zip(tour[:-1], tour[1:]):
            G.add_edge(a, b, vehicle=k)
            xa, ya = scenario.coords[a]
            xb, yb = scenario.coords[b]
            edge_x += [xa, xb, None]
            edge_y += [ya, yb, None]

            # Fleche directionnelle discrete au milieu de l'arc
            fig.add_annotation(
                x=xb, y=yb, ax=xa, ay=ya,
                xref="x", yref="y", axref="x", ayref="y",
                showarrow=True, arrowhead=2, arrowsize=1.1,
                arrowwidth=1.6, arrowcolor=color, opacity=0.85,
            )

        label = f"Vehicule {k}" + (" [FROID]" if fleet.R[k] else "")
        fig.add_trace(
            go.Scatter(
                x=edge_x, y=edge_y, mode="lines",
                line=dict(width=2.4, color=color),
                name=f"{label} - {fleet.Q[k]:.0f}u - {fleet.c_f[k]:.2f} EUR/km",
                hoverinfo="skip",
            )
        )

    # ---------- NOEUDS CLIENTS ----------
    cx, cy, ctext, csize, ccolor, cline = [], [], [], [], [], []
    for i in range(1, scenario.n_nodes):
        xc, yc = scenario.coords[i]
        s_i = solution.arrival.get(i, 0.0)
        l_i = solution.lateness.get(i, 0.0)
        cx.append(xc)
        cy.append(yc)
        ctext.append(
            f"<b>Pharmacie {i}</b><br>"
            f"Arrivee prevue s[i] : <b>{s_i:.1f} min</b><br>"
            f"Deadline SLA T[i]  : {scenario.T[i]:.1f} min<br>"
            f"Retard L[i]        : <b>{l_i:.1f} min</b><br>"
            f"Priorite pr[i]     : {scenario.pr[i]:.2f}<br>"
            f"Chaine du froid    : {'OUI' if scenario.cc[i] else 'non'}<br>"
            f"Charge q[i]        : {scenario.q[i]:.1f} u<br>"
            f"Dechargement       : {scenario.sigma[i]:.1f} min"
        )
        csize.append(14 + 16 * scenario.pr[i])          # taille ~ priorite
        ccolor.append("#1F6FEB" if scenario.cc[i] else "#B0BEC5")
        cline.append("#D7263D" if l_i > 1e-4 else "#37474F")  # rouge si retard

    fig.add_trace(
        go.Scatter(
            x=cx, y=cy, mode="markers+text",
            text=[str(i) for i in range(1, scenario.n_nodes)],
            textposition="top center",
            textfont=dict(size=10, color="#263238"),
            marker=dict(
                size=csize, color=ccolor,
                line=dict(width=2.5, color=cline),
                symbol="circle",
            ),
            hovertext=ctext, hoverinfo="text",
            name="Pharmacies (bleu = froid, contour rouge = retard SLA)",
        )
    )

    # ---------- DEPOT ----------
    dx, dy = scenario.coords[0]
    fig.add_trace(
        go.Scatter(
            x=[dx], y=[dy], mode="markers+text",
            text=["DEPOT"], textposition="bottom center",
            textfont=dict(size=12, color="#000000"),
            marker=dict(size=22, color="#111111", symbol="square"),
            hovertext=[f"<b>Depot central</b><br>Depart s[0] = 0.0 min"],
            hoverinfo="text", name="Depot",
        )
    )

    total_late = sum(v for i, v in solution.lateness.items() if i != 0)
    fig.update_layout(
        title=dict(
            text=(
                f"<b>{title}</b><br>"
                f"<span style='font-size:13px'>Statut : {solution.status} | "
                f"Cout objectif : {solution.objective:.2f} | "
                f"Vehicules utilises : {len(solution.routes)}/{fleet.n_vehicles} | "
                f"Retard total : {total_late:.1f} min</span>"
            ),
            x=0.5, xanchor="center",
        ),
        template="plotly_white",
        hovermode="closest",
        xaxis=dict(title="Coordonnee X (km)", zeroline=False, scaleanchor="y"),
        yaxis=dict(title="Coordonnee Y (km)", zeroline=False),
        legend=dict(orientation="h", yanchor="bottom", y=-0.22, x=0),
        margin=dict(l=50, r=50, t=95, b=70),
        height=760,
    )
    return fig


# =============================================================================
# 6. RAPPORT CONSOLE
# =============================================================================


def print_report(scenario: Scenario, fleet: Fleet, solution: Solution, d) -> None:
    """Affiche un resume textuel de la solution."""
    print("\n" + "=" * 72)
    print(f" STATUT : {solution.status}   |   OBJECTIF : {solution.objective:.2f}")
    print("=" * 72)
    for k, tour in sorted(solution.routes.items()):
        dist = sum(d[a][b] for a, b in zip(tour[:-1], tour[1:]))
        load = sum(scenario.q[i] for i in tour if i != 0)
        cold = " [REFRIGERE]" if fleet.R[k] else ""
        print(f"\n Vehicule {k}{cold}  cap={fleet.Q[k]:.0f}  cout={fleet.c_f[k]:.2f} EUR/km")
        print(f"   Tournee   : {' -> '.join(map(str, tour))}")
        print(f"   Distance  : {dist:.2f} km   |   Charge : {load:.1f}/{fleet.Q[k]:.0f}")
        for i in tour[1:-1]:
            flag = "RETARD" if solution.lateness[i] > 1e-4 else "OK"
            print(
                f"     - Pharmacie {i:>2} | arrivee {solution.arrival[i]:7.1f} min"
                f" | SLA {scenario.T[i]:6.1f} | retard {solution.lateness[i]:6.1f}"
                f" | pr={scenario.pr[i]:.2f} | froid={scenario.cc[i]} | {flag}"
            )
    print("\n" + "=" * 72 + "\n")


# =============================================================================
# 7. POINT D'ENTREE
# =============================================================================

if __name__ == "__main__":
    # --- Instant t : le scenario change a chaque execution (seed=None) ---
    N_PHARMACIES = 8
    scenario = generate_scenario(N_PHARMACIES, area_km=20.0, seed=42)

    # --- Flotte heterogene ---
    fleet = Fleet(
        Q=[120.0, 100.0, 90.0],     # capacites
        R=[1, 0, 1],                # vehicules 0 et 2 refrigeres
        c_f=[1.40, 0.95, 1.25],     # EUR / km
    )

    # --- Matrices calculees dynamiquement depuis les coordonnees ---
    d, t = compute_distance_matrix(
        scenario.coords, metric="euclidean", avg_speed_kmh=30.0, detour_factor=1.25
    )

    # --- Resolution du MILP ---
    solution = build_and_solve_vrp(
        scenario, fleet, d, t,
        H_max=480.0, w_p=5.0, gamma=0.10, w_v=50.0,
        time_limit=180, verbose=False,
    )

    print_report(scenario, fleet, solution, d)

    # --- Visualisation interactive ---
    fig = plot_solution(scenario, fleet, solution)
    fig.write_html("vrp_solution.html", include_plotlyjs="cdn")
    print("Graphe interactif exporte -> vrp_solution.html")
    fig.show()
