"""
Carte interactive des tournees optimisees — Sousse (donnees TomTom reelles).
Genere deux fichiers HTML autonomes :
  - carte_sousse.html : tournees sur fond de carte OpenStreetMap
  - graphe_sousse.html : graphe reseau en style sombre lumineux

Lancement (Windows) :  py carte_sousse_interactive.py
Prerequis            :  py -m pip install plotly networkx
"""

import json
import math
import os

import networkx as nx
import plotly.graph_objects as go

# =============================================================================
# CHARGEMENT DU PLAN RESOLU
# =============================================================================
ICI = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(ICI, "plan_sousse.json"), encoding="utf-8") as f:
    PLAN = json.load(f)

NOEUDS = {n["id"]: n for n in PLAN["noeuds"]}
TOURNEES = PLAN["tournees"]

# Palette lumineuse par vehicule (rendu sombre)
COULEURS = {0: "#00E5FF", 2: "#FF4D8D", 1: "#B388FF"}


def infobulle(n: dict) -> str:
    """Texte de survol detaille pour un noeud."""
    if n["id"] == 0:
        return f"<b>{n['nom']}</b><br>Depart des tournees a 0 min"
    retard = max(0.0, n["s_plan"] - n["T"])
    marge = n["T"] - n["s_plan"]
    return (
        f"<b>{n['nom']}</b><br>"
        f"<i>{n['lib']}</i><br>"
        f"─────────────<br>"
        f"Arrivee prevue : <b>{n['s_plan']:.1f} min</b><br>"
        f"Echeance SLA   : {n['T']:.1f} min<br>"
        f"Marge          : <b>{marge:+.1f} min</b><br>"
        f"Retard L[i]    : {retard:.1f} min<br>"
        f"Priorite pr[i] : {n['pr']:.2f}<br>"
        f"Chaine du froid: {'OUI' if n['cc'] else 'non'}<br>"
        f"Charge q[i]    : {n['q']:.1f} u<br>"
        f"Dechargement   : {n['sigma']:.1f} min"
    )


# =============================================================================
# 1. CARTE GEOGRAPHIQUE (fond OpenStreetMap)
# =============================================================================
def carte_geographique() -> go.Figure:
    fig = go.Figure()

    for t in TOURNEES:
        k = t["k"]
        couleur = COULEURS.get(k, "#FFFFFF")
        lons = [NOEUDS[i]["lon"] for i in t["ordre"]]
        lats = [NOEUDS[i]["lat"] for i in t["ordre"]]
        etiquette = f"Vehicule {k}" + (" — REFRIGERE" if t["refrigere"] else " — sec")
        fig.add_trace(go.Scattermap(
            lon=lons, lat=lats, mode="lines",
            line=dict(width=4, color=couleur),
            name=f"{etiquette} · {t['km']:.2f} km",
            hoverinfo="skip", opacity=0.9,
        ))

    # --- Pharmacies ---
    clients = [n for n in PLAN["noeuds"] if n["id"] != 0]
    fig.add_trace(go.Scattermap(
        lon=[n["lon"] for n in clients],
        lat=[n["lat"] for n in clients],
        mode="markers+text",
        marker=dict(
            size=[13 + 16 * n["pr"] for n in clients],
            color=["#00B8D4" if n["cc"] else "#FF7043" for n in clients],
            opacity=0.95,
        ),
        text=[str(n["id"]) for n in clients],
        textfont=dict(size=11, color="#FFFFFF"),
        hovertext=[infobulle(n) for n in clients],
        hoverinfo="text",
        name="Pharmacies (cyan = chaine du froid)",
    ))

    # --- Depot ---
    d = NOEUDS[0]
    fig.add_trace(go.Scattermap(
        lon=[d["lon"]], lat=[d["lat"]], mode="markers+text",
        marker=dict(size=22, color="#FFD600"),
        text=["DEPOT"], textfont=dict(size=13, color="#FFD600"),
        hovertext=[infobulle(d)], hoverinfo="text", name="Depot",
    ))

    km_total = sum(t["km"] for t in TOURNEES)
    fig.update_layout(
        title=dict(
            text=(
                "<b>Tournees optimisees — Sousse, Tunisie</b><br>"
                f"<span style='font-size:13px'>MILP exact ({PLAN['statut']}) · "
                f"objectif {PLAN['objectif']} · {km_total:.2f} km · "
                f"{len(TOURNEES)} vehicules · coordonnees TomTom reelles</span>"
            ),
            x=0.5, xanchor="center", font=dict(color="#ECEFF1"),
        ),
        map=dict(
            style="carto-darkmatter",
            center=dict(lon=(d["lon"] + 10.634) / 2, lat=(d["lat"] + 35.826) / 2),
            zoom=12.6,
        ),
        paper_bgcolor="#0B0E14",
        font=dict(color="#B0BEC5"),
        legend=dict(orientation="h", yanchor="bottom", y=-0.10, x=0,
                    bgcolor="rgba(11,14,20,0.75)"),
        margin=dict(l=0, r=0, t=80, b=60),
        height=760,
    )
    return fig


# =============================================================================
# 2. GRAPHE RESEAU EN STYLE SOMBRE LUMINEUX
# =============================================================================
def graphe_lumineux() -> go.Figure:
    """Projection locale des coordonnees GPS en plan metrique, style glow."""
    lat0 = NOEUDS[0]["lat"]
    kx = 111.320 * math.cos(math.radians(lat0))   # km par degre de longitude
    ky = 110.574                                   # km par degre de latitude
    pos = {i: ((n["lon"] - NOEUDS[0]["lon"]) * kx,
               (n["lat"] - NOEUDS[0]["lat"]) * ky)
           for i, n in NOEUDS.items()}

    G = nx.DiGraph()
    for i in NOEUDS:
        G.add_node(i, pos=pos[i])

    fig = go.Figure()

    # --- Arcs avec effet de halo (3 passes d'epaisseur decroissante) ---
    for t in TOURNEES:
        couleur = COULEURS.get(t["k"], "#FFFFFF")
        xs, ys = [], []
        for a, b in zip(t["ordre"][:-1], t["ordre"][1:]):
            G.add_edge(a, b)
            xs += [pos[a][0], pos[b][0], None]
            ys += [pos[a][1], pos[b][1], None]
        for largeur, opacite in ((11, 0.10), (6, 0.22), (2.6, 1.0)):
            fig.add_trace(go.Scatter(
                x=xs, y=ys, mode="lines",
                line=dict(width=largeur, color=couleur),
                opacity=opacite, hoverinfo="skip",
                showlegend=(largeur == 2.6),
                name=(f"Vehicule {t['k']}"
                      + (" — REFRIGERE" if t["refrigere"] else " — sec")
                      + f" · {t['km']:.2f} km"),
            ))

    # --- Halo des noeuds clients ---
    clients = [n for n in PLAN["noeuds"] if n["id"] != 0]
    for facteur, opacite in ((3.2, 0.07), (2.0, 0.13), (1.0, 1.0)):
        fig.add_trace(go.Scatter(
            x=[pos[n["id"]][0] for n in clients],
            y=[pos[n["id"]][1] for n in clients],
            mode="markers",
            marker=dict(
                size=[(13 + 16 * n["pr"]) * facteur for n in clients],
                color=["#00E5FF" if n["cc"] else "#FF7043" for n in clients],
                line=dict(width=0),
            ),
            opacity=opacite, hoverinfo="skip", showlegend=False,
        ))

    # --- Noeuds cliquables (couche d'interaction) ---
    fig.add_trace(go.Scatter(
        x=[pos[n["id"]][0] for n in clients],
        y=[pos[n["id"]][1] for n in clients],
        mode="markers+text",
        marker=dict(size=[13 + 16 * n["pr"] for n in clients],
                    color="rgba(0,0,0,0)"),
        text=[str(n["id"]) for n in clients],
        textposition="top center",
        textfont=dict(size=11, color="#ECEFF1"),
        hovertext=[infobulle(n) for n in clients],
        hoverinfo="text",
        name="Pharmacies (cyan = chaine du froid)",
    ))

    # --- Depot ---
    for facteur, opacite in ((3.4, 0.09), (2.0, 0.16), (1.0, 1.0)):
        fig.add_trace(go.Scatter(
            x=[0], y=[0], mode="markers",
            marker=dict(size=24 * facteur, color="#FFD600",
                        symbol="square", line=dict(width=0)),
            opacity=opacite, hoverinfo="skip", showlegend=False,
        ))
    fig.add_trace(go.Scatter(
        x=[0], y=[0], mode="markers+text",
        marker=dict(size=24, color="rgba(0,0,0,0)", symbol="square"),
        text=["DEPOT"], textposition="bottom center",
        textfont=dict(size=12, color="#FFD600"),
        hovertext=[infobulle(NOEUDS[0])], hoverinfo="text", name="Depot",
    ))

    km_total = sum(t["km"] for t in TOURNEES)
    retard = sum(max(0.0, n["s_plan"] - n["T"]) for n in clients)
    fig.update_layout(
        title=dict(
            text=(
                "<b>Reseau de distribution optimise — Sousse</b><br>"
                f"<span style='font-size:13px'>{PLAN['statut']} · objectif "
                f"{PLAN['objectif']} · {km_total:.2f} km · retard total "
                f"{retard:.1f} min · calibre sur trajets TomTom reels</span>"
            ),
            x=0.5, xanchor="center", font=dict(color="#ECEFF1", size=19),
        ),
        paper_bgcolor="#080A0F", plot_bgcolor="#080A0F",
        font=dict(color="#90A4AE"),
        xaxis=dict(title="Distance Est-Ouest (km)", showgrid=True,
                   gridcolor="#151A24", zeroline=False, scaleanchor="y"),
        yaxis=dict(title="Distance Nord-Sud (km)", showgrid=True,
                   gridcolor="#151A24", zeroline=False),
        legend=dict(orientation="h", yanchor="bottom", y=-0.16, x=0,
                    bgcolor="rgba(8,10,15,0.8)"),
        hoverlabel=dict(bgcolor="#111722", font=dict(color="#ECEFF1", size=12),
                        bordercolor="#37474F"),
        margin=dict(l=70, r=40, t=90, b=90),
        height=780,
    )
    return fig


# =============================================================================
if __name__ == "__main__":
    carte = carte_geographique()
    carte.write_html(os.path.join(ICI, "carte_sousse.html"), include_plotlyjs="cdn")
    print("Carte geographique -> carte_sousse.html")

    graphe = graphe_lumineux()
    graphe.write_html(os.path.join(ICI, "graphe_sousse.html"), include_plotlyjs="cdn")
    print("Graphe reseau      -> graphe_sousse.html")

    km = sum(t["km"] for t in TOURNEES)
    print(f"\n{PLAN['statut']} · {len(TOURNEES)} vehicules · {km:.2f} km · "
          f"objectif {PLAN['objectif']}")
