"""
=============================================================================
 VRP-MILP applique a des donnees REELLES : pharmacies de Sousse (Tunisie)
=============================================================================
 Sources des donnees :
   - Coordonnees GPS : TomTom Places API (categorie PHARMACY), 18/07/2026
   - Depot : coordonnees fournies par l'utilisateur, adresse verifiee par
     TomTom Reverse Geocoding (45 Rue Safia Farhat, Sousse 4054)
   - Calibration routiere : TomTom Routing API, 13 trajets reels echantillonnes
       -> facteur de sinuosite median = 1.60  (routier / vol d'oiseau)
       -> vitesse effective mediane   = 20.9 km/h (trafic live inclus)
     Validee sur deux regimes : trajets intra-cluster (1.60 / 20.9 km/h) et
     trajets longs depuis le depot (1.52 / 20.6 km/h) — ecart faible.

 NOTE METHODOLOGIQUE :
   Les distances sont calculees en haversine x 1.60 plutot que par 110 appels
   API (une matrice 11x11 complete). L'ecart type des ratios observes est
   eleve (1.25 a 2.62), donc cette approximation reste une approximation :
   les distances individuelles peuvent devier, mais l'ordre de grandeur des
   tournees est correct. Pour une exploitation reelle, remplacer
   `build_matrix_calibrated` par `build_matrix_from_tomtom`.

 Lancement (Windows) :  py vrp_sousse_reel.py
 Prerequis            :  py -m pip install pulp plotly networkx
=============================================================================
"""

from __future__ import annotations

import math
import random
from typing import Dict, List, Sequence, Tuple

from vrp_milp_pharma import Fleet, Scenario, build_and_solve_vrp, print_report

# =============================================================================
# 1. DONNEES REELLES — TomTom Places API, Sousse, 18/07/2026
# =============================================================================

DEPOT = {
    # Coordonnees fournies : 35 50'18.1"N 10 36'19.7"E
    # Adresse confirmee par TomTom Reverse Geocoding :
    "nom": "Depot — 45 Rue Safia Farhat, Sousse 4054 (Sousse Jawhara)",
    "lon": 10.6054722,
    "lat": 35.8383611,
}

# 10 pharmacies reellement geolocalisees par TomTom
PHARMACIES = [
    {"nom": "Pharmacie Hafedh Frigui",      "lon": 10.633500, "lat": 35.825800},
    {"nom": "Pharmacie Chehata Raoudha",    "lon": 10.636800, "lat": 35.829300},
    {"nom": "Pharmacie Zaoui",              "lon": 10.633784, "lat": 35.830978},
    {"nom": "Pharmacie Nouira Souha",       "lon": 10.629957, "lat": 35.829877},
    {"nom": "Pharmacie Chemli Foued",       "lon": 10.636900, "lat": 35.820200},
    {"nom": "Pharmacie de Nuit NEJI K.",    "lon": 10.632363, "lat": 35.831623},
    {"nom": "Pharmacie Insaf Skhiri Ajina", "lon": 10.638412, "lat": 35.832505},
    {"nom": "Pharmacie Health",             "lon": 10.641600, "lat": 35.831400},
    {"nom": "Pharmacie Mon Armoire",        "lon": 10.631100, "lat": 35.817700},
    {"nom": "Pharmacie Nenupharma",         "lon": 10.625700, "lat": 35.830300},
]

# --- Constantes calibrees sur des trajets TomTom reels (voir en-tete) ---
FACTEUR_SINUOSITE = 1.60
VITESSE_EFFECTIVE_KMH = 20.9

# =============================================================================
# 2. GEOMETRIE : HAVERSINE + CALIBRATION ROUTIERE
# =============================================================================


def haversine_km(a: Tuple[float, float], b: Tuple[float, float]) -> float:
    """Distance orthodromique en km entre deux points (lon, lat)."""
    R = 6371.0088
    lon1, lat1 = math.radians(a[0]), math.radians(a[1])
    lon2, lat2 = math.radians(b[0]), math.radians(b[1])
    h = (math.sin((lat2 - lat1) / 2) ** 2
         + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2)
    return 2 * R * math.asin(math.sqrt(h))


def build_matrix_calibrated(
    coords: Sequence[Tuple[float, float]],
    *,
    sinuosite: float = FACTEUR_SINUOSITE,
    vitesse_kmh: float = VITESSE_EFFECTIVE_KMH,
) -> Tuple[List[List[float]], List[List[float]]]:
    """Matrices d[i,j] (km) et t[i,j] (min) : haversine x facteur calibre."""
    n = len(coords)
    d = [[0.0] * n for _ in range(n)]
    t = [[0.0] * n for _ in range(n)]
    for i in range(n):
        for j in range(n):
            if i == j:
                continue
            d[i][j] = haversine_km(coords[i], coords[j]) * sinuosite
            t[i][j] = d[i][j] / vitesse_kmh * 60.0
    return d, t


def build_matrix_from_tomtom(coords, appeler_routing) -> Tuple[List[List[float]], List[List[float]]]:
    """Version exacte : une matrice complete via l'API TomTom Routing.

    `appeler_routing(origine, destination)` doit renvoyer (metres, secondes).
    Cout : n*(n-1) appels API. A utiliser en production, pas en demo.
    """
    n = len(coords)
    d = [[0.0] * n for _ in range(n)]
    t = [[0.0] * n for _ in range(n)]
    for i in range(n):
        for j in range(n):
            if i == j:
                continue
            metres, secondes = appeler_routing(coords[i], coords[j])
            d[i][j] = metres / 1000.0
            t[i][j] = secondes / 60.0
    return d, t


# =============================================================================
# 3. COMMANDES SIMULEES — point d'injection de la couche 2
# =============================================================================
# /!\ Ces valeurs sont SIMULEES sur des profils therapeutiques plausibles.
#     Pour brancher ta couche 2 : remplacer `simuler_commandes` par un
#     chargeur qui renvoie la meme structure (q, cc, pr, sigma, T).

PROFILS = [
    # (libelle, chaine_du_froid, priorite, charge_min, charge_max)
    ("Insuline / vaccins",        1, 0.95, 8, 20),
    ("Serums thermosensibles",    1, 0.85, 5, 15),
    ("Antibiotiques injectables", 0, 0.75, 10, 30),
    ("Traitements chroniques",    0, 0.55, 15, 40),
    ("Antalgiques courants",      0, 0.35, 20, 45),
    ("Parapharmacie / confort",   0, 0.15, 10, 35),
]


def simuler_commandes(n: int, seed: int | None = 2026) -> Dict[str, list]:
    """Genere des commandes credibles par profil therapeutique."""
    rng = random.Random(seed)
    q, cc, pr, sigma, T, libelles = [0.0], [0], [0.0], [0.0], [0.0], ["DEPOT"]
    for _ in range(n):
        lib, froid, prio, qmin, qmax = rng.choice(PROFILS)
        q.append(round(rng.uniform(qmin, qmax), 1))
        cc.append(froid)
        # bruit leger autour de la priorite nominale du profil
        pr.append(round(min(1.0, max(0.05, prio + rng.uniform(-0.08, 0.08))), 2))
        sigma.append(round(rng.uniform(4, 12), 1))   # dechargement (min)
        # SLA : plus la priorite est haute, plus la fenetre est serree
        T.append(round(rng.uniform(45, 90) if prio > 0.7 else rng.uniform(90, 210), 1))
        libelles.append(lib)
    return {"q": q, "cc": cc, "pr": pr, "sigma": sigma, "T": T, "libelles": libelles}


# =============================================================================
# 4. CONSTRUCTION DU SCENARIO REEL
# =============================================================================


def construire_scenario_sousse(seed: int | None = 2026):
    """Assemble coordonnees reelles + commandes simulees en un Scenario."""
    coords = [(DEPOT["lon"], DEPOT["lat"])]
    noms = [DEPOT["nom"]]
    for p in PHARMACIES:
        coords.append((p["lon"], p["lat"]))
        noms.append(p["nom"])

    cmd = simuler_commandes(len(PHARMACIES), seed=seed)
    scenario = Scenario(
        coords=coords, q=cmd["q"], cc=cmd["cc"],
        pr=cmd["pr"], sigma=cmd["sigma"], T=cmd["T"],
    )
    return scenario, noms, cmd["libelles"]


# =============================================================================
# 5. EXECUTION
# =============================================================================

if __name__ == "__main__":
    scenario, noms, libelles = construire_scenario_sousse()
    d, t = build_matrix_calibrated(scenario.coords)

    # Flotte : 2 refrigeres + 1 sec (couts en TND/km)
    fleet = Fleet(
        Q=[90.0, 70.0, 110.0],
        R=[1, 1, 0],
        c_f=[1.85, 1.60, 1.10],
    )

    print("=" * 74)
    print(" VRP PHARMACEUTIQUE — SOUSSE, TUNISIE (coordonnees TomTom reelles)")
    print("=" * 74)
    print(f" Depot     : {DEPOT['nom']}")
    print(f" Clients   : {len(PHARMACIES)} pharmacies geolocalisees")
    print(f" Geometrie : haversine x {FACTEUR_SINUOSITE} @ {VITESSE_EFFECTIVE_KMH} km/h")
    print(f"             (calibre sur 9 itineraires TomTom reels)")
    print("\n Commandes simulees :")
    for i in range(1, scenario.n_nodes):
        froid = "FROID" if scenario.cc[i] else "  -  "
        print(f"   {i:2d}. {noms[i][:32]:32s} | {libelles[i][:26]:26s}"
              f" | {froid} | q={scenario.q[i]:5.1f} | pr={scenario.pr[i]:.2f}"
              f" | SLA={scenario.T[i]:6.1f} min")

    solution = build_and_solve_vrp(
        scenario, fleet, d, t,
        H_max=480.0, w_p=5.0, gamma=0.10, w_v=50.0,
        time_limit=240, verbose=False,
    )

    print_report(scenario, fleet, solution, d)

    for k, tour in sorted(solution.routes.items()):
        print(f"Vehicule {k} : " + " -> ".join(
            noms[i].replace("Pharmacie ", "P. ")[:22] for i in tour))
