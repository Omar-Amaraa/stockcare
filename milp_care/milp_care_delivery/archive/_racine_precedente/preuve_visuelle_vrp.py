"""
Preuve visuelle : MILP binaire vs relaxation continue (gradient) sur un VRP.
Lancement (Windows) :  py preuve_visuelle_vrp.py
Prerequis            :  py -m pip install matplotlib networkx numpy
"""

import math
import random

import matplotlib.pyplot as plt
import networkx as nx
import numpy as np
from matplotlib.lines import Line2D

random.seed(7)
np.random.seed(7)

# =============================================================================
# 1. GENERATION DES NOEUDS : 0 = depot central, 1..5 = pharmacies en couronne
# =============================================================================
N_PHARMA = 5
RAYON = 1.0

POS = {0: (0.0, 0.0)}
for i in range(1, N_PHARMA + 1):
    angle = 2 * math.pi * (i - 1) / N_PHARMA + math.pi / 2
    POS[i] = (RAYON * math.cos(angle), RAYON * math.sin(angle))

LABELS = {0: "DEPOT", **{i: f"P{i}" for i in range(1, N_PHARMA + 1)}}
COULEURS = ["#1F6FEB"] + ["#D7263D"] * N_PHARMA
TAILLES = [1400] + [1000] * N_PHARMA

# =============================================================================
# 2. SOLUTIONS COMPAREES
# =============================================================================
# --- MILP : tournee unique fermee, x[i,j] dans {0, 1} ---
TOURNEE = [0, 1, 2, 3, 4, 5, 0]
ARCS_MILP = list(zip(TOURNEE[:-1], TOURNEE[1:]))

# --- Gradient : relaxation continue, x[i,j] dans [0, 1] -> toile d'araignee ---
ARCS_FRAC = [
    (0, 1), (0, 2), (0, 3), (0, 4), (0, 5),
    (1, 2), (2, 3), (3, 4), (4, 5), (5, 1),
    (1, 3), (2, 4), (3, 5), (4, 1), (5, 2),
]
POIDS_FRAC = {arc: round(random.uniform(0.1, 0.8), 2) for arc in ARCS_FRAC}

# =============================================================================
# 3. FIGURE : 1 ligne x 2 colonnes
# =============================================================================
fig, (ax_milp, ax_grad) = plt.subplots(1, 2, figsize=(16, 8))
fig.suptitle(
    "VRP : Pourquoi le Machine Learning continu echoue face au MILP",
    fontsize=19, fontweight="bold", y=0.97,
)

# -----------------------------------------------------------------------------
# GRAPHIQUE 1 (GAUCHE) : SOLUTION MILP BINAIRE
# -----------------------------------------------------------------------------
G_milp = nx.DiGraph()
G_milp.add_nodes_from(POS)
G_milp.add_edges_from(ARCS_MILP)

nx.draw_networkx_edges(
    G_milp, POS, ax=ax_milp, edgelist=ARCS_MILP,
    width=2, edge_color="#0B6E4F", arrows=True,
    arrowstyle="-|>", arrowsize=22, min_source_margin=20, min_target_margin=20,
)
nx.draw_networkx_nodes(
    G_milp, POS, ax=ax_milp,
    node_color=COULEURS, node_size=TAILLES,
    edgecolors="black", linewidths=2,
)
nx.draw_networkx_labels(G_milp, POS, LABELS, ax=ax_milp,
                        font_size=11, font_color="white", font_weight="bold")

# Etiquette "1 camion" sur chaque arc de la tournee
nx.draw_networkx_edge_labels(
    G_milp, POS, ax=ax_milp,
    edge_labels={arc: "1 camion" for arc in ARCS_MILP},
    font_size=9, font_color="#0B6E4F", font_weight="bold",
    bbox=dict(boxstyle="round,pad=0.25", fc="white", ec="#0B6E4F", alpha=0.9),
)

ax_milp.set_title("Solution MILP (Binaire : 0 ou 1)", fontsize=15,
                  fontweight="bold", color="#0B6E4F", pad=14)
ax_milp.text(
    0.5, -0.01,
    "TRAJET PHYSIQUEMENT REALISABLE\n"
    "Tournee fermee unique : 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 0\n"
    "x[i,j] = 1 : le camion emprunte l'arc  |  x[i,j] = 0 : il ne l'emprunte pas\n"
    "Chaque pharmacie visitee exactement une fois, aucun sous-tour",
    transform=ax_milp.transAxes, ha="center", va="top", fontsize=10.5,
    bbox=dict(boxstyle="round,pad=0.6", fc="#E8F5E9", ec="#0B6E4F", lw=2),
)

# -----------------------------------------------------------------------------
# GRAPHIQUE 2 (DROITE) : RELAXATION CONTINUE (GRADIENT)
# -----------------------------------------------------------------------------
G_grad = nx.DiGraph()
G_grad.add_nodes_from(POS)
for (i, j), w in POIDS_FRAC.items():
    G_grad.add_edge(i, j, weight=w)

for (i, j), w in POIDS_FRAC.items():
    nx.draw_networkx_edges(
        G_grad, POS, ax=ax_grad, edgelist=[(i, j)],
        width=0.5 + 6.0 * w,          # epaisseur proportionnelle a la fraction
        alpha=0.15 + 0.85 * w,        # opacite proportionnelle a la fraction
        edge_color="#B3001B", arrows=True, arrowstyle="-|>",
        arrowsize=8 + 18 * w, min_source_margin=20, min_target_margin=20,
        connectionstyle="arc3,rad=0.12",
    )

nx.draw_networkx_nodes(
    G_grad, POS, ax=ax_grad,
    node_color=COULEURS, node_size=TAILLES,
    edgecolors="black", linewidths=2,
)
nx.draw_networkx_labels(G_grad, POS, LABELS, ax=ax_grad,
                        font_size=11, font_color="white", font_weight="bold")

# Annotation des fractions : arcs de la couronne exterieure uniquement,
# pour eviter tout chevauchement avec le depot central.
ARCS_COURONNE = [(1, 2), (2, 3), (3, 4), (4, 5), (5, 1)]
nx.draw_networkx_edge_labels(
    G_grad, POS, ax=ax_grad,
    edge_labels={a: f"{POIDS_FRAC[a]:.2f} camion" for a in ARCS_COURONNE},
    font_size=9.5, font_color="#B3001B", font_weight="bold", label_pos=0.5,
    rotate=False,
    bbox=dict(boxstyle="round,pad=0.25", fc="#FFF3F3", ec="#B3001B", alpha=0.95),
)

ax_grad.set_title("Solution Descente de Gradient (Fractions continues)",
                  fontsize=15, fontweight="bold", color="#B3001B", pad=14)

n_frac = sum(1 for w in POIDS_FRAC.values() if 0 < w < 1)
ax_grad.text(
    0.5, -0.01,
    "TRAJET PHYSIQUEMENT IMPOSSIBLE\n"
    f"{n_frac} arcs sur {len(POIDS_FRAC)} portent une valeur fractionnaire\n"
    "0.30 camion sur un arc et 0.45 sur un autre : un camion ne se divise pas\n"
    "Aucune tournee executable : la borne est bonne, la solution est fictive",
    transform=ax_grad.transAxes, ha="center", va="top", fontsize=10.5,
    bbox=dict(boxstyle="round,pad=0.6", fc="#FDECEC", ec="#B3001B", lw=2),
)

# -----------------------------------------------------------------------------
# LEGENDE COMMUNE ET MISE EN FORME
# -----------------------------------------------------------------------------
legende = [
    Line2D([0], [0], marker="o", color="w", markerfacecolor="#1F6FEB",
           markeredgecolor="black", markersize=15, label="Depot (noeud 0)"),
    Line2D([0], [0], marker="o", color="w", markerfacecolor="#D7263D",
           markeredgecolor="black", markersize=13, label="Pharmacies (P1..P5)"),
    Line2D([0], [0], color="#0B6E4F", lw=2, label="Arc MILP : x = 1 (camion entier)"),
    Line2D([0], [0], color="#B3001B", lw=4, alpha=0.7,
           label="Arc relache : 0 < x < 1 (epaisseur ~ fraction)"),
]
fig.legend(handles=legende, loc="lower center", ncol=4, fontsize=11,
           frameon=True, bbox_to_anchor=(0.5, 0.005))

for ax in (ax_milp, ax_grad):
    ax.set_axis_off()
    ax.set_xlim(-1.40, 1.40)
    ax.set_ylim(-1.40, 1.35)
    ax.set_aspect("equal")

plt.tight_layout(rect=[0, 0.10, 1, 0.94])
plt.savefig("preuve_visuelle_vrp.png", dpi=200, bbox_inches="tight")
plt.show()
