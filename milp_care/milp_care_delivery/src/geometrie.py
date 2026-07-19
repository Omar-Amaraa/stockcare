"""
=============================================================================
 GEOMETRIE ROUTIERE — matrices d[i,j] et t[i,j]
=============================================================================
 Calibration : TomTom Routing API, 13 trajets reels a Sousse (18/07/2026)
   -> facteur de sinuosite median = 1.60  (routier / vol d'oiseau)
   -> vitesse effective mediane   = 20.9 km/h (trafic live inclus)

 NOTE : approximation haversine x facteur. Pour une exploitation reelle,
 utiliser build_matrix_from_tomtom() (n*(n-1) appels API).
=============================================================================
"""

from __future__ import annotations

import math
from typing import Callable, List, Sequence, Tuple


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
    sinuosite: float,
    vitesse_kmh: float,
) -> Tuple[List[List[float]], List[List[float]]]:
    """Matrices d[i,j] (km) et t[i,j] (min) : haversine x facteur calibre."""
    if vitesse_kmh <= 0:
        raise ValueError("vitesse_kmh doit etre strictement positive")
    if sinuosite <= 0:
        raise ValueError("sinuosite doit etre strictement positive")

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


def build_matrix_from_tomtom(
    coords: Sequence[Tuple[float, float]],
    appeler_routing: Callable[[Tuple[float, float], Tuple[float, float]], Tuple[float, float]],
) -> Tuple[List[List[float]], List[List[float]]]:
    """Version exacte : matrice complete via l'API TomTom Routing.

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


def build_matrix_tiered(
    coords: Sequence[Tuple[float, float]],
    *,
    paliers: Sequence[dict],
) -> Tuple[List[List[float]], List[List[float]]]:
    """Matrices d[i,j] / t[i,j] avec sinuosite et vitesse dependant de la distance.

    La calibration TomTom (1.6x, 20.9 km/h) vient de 13 trajets COURTS, intra-
    urbains, a Sousse. L'appliquer telle quelle a une paire depot-pharmacie
    distante (ex. Tunis -> Sfax, ~235 km a vol d'oiseau) donne un temps de
    trajet aberrant (~18h) qui rend la pharmacie infaisable dans un horizon
    H_max de 8h, alors qu'un trajet autoroutier reel prend 3-4h.

    Chaque `palier` est un dict {distance_max_km, sinuosite, vitesse_kmh} ;
    le dernier palier doit avoir distance_max_km=None (borne haute = infini).
    Les paliers doivent etre tries par distance_max_km croissante. Pour
    chaque paire (i, j), on choisit le premier palier dont la distance a vol
    d'oiseau est <= distance_max_km (le dernier palier sert de filet).

    Exemple par defaut (voir config.json -> geometrie.paliers) :
        <15 km  : urbain     (1.6x, 20.9 km/h)  -- calibration TomTom Sousse
        <60 km  : peri-urbain/regional (1.35x, 55 km/h)
        au-dela : route nationale/autoroute (1.2x, 85 km/h)

    Une seule paire (i, j) peut ainsi traverser plusieurs regimes de vitesse
    dans la vraie vie (ville -> autoroute -> ville), mais un unique palier
    par paire, choisi sur la distance totale, reste une approximation trois
    fois plus fidele qu'un facteur unique national -- suffisant tant que la
    matrice n'est pas remplacee par un vrai routeur (TomTom/OSRM, cf.
    build_matrix_from_tomtom ci-dessus).
    """
    if not paliers:
        raise ValueError("paliers ne peut pas etre vide")
    for i, p in enumerate(paliers):
        if p.get("vitesse_kmh", 0) <= 0:
            raise ValueError(f"palier {i} : vitesse_kmh doit etre strictement positive")
        if p.get("sinuosite", 0) <= 0:
            raise ValueError(f"palier {i} : sinuosite doit etre strictement positive")
    if paliers[-1].get("distance_max_km") is not None:
        raise ValueError("le dernier palier doit avoir distance_max_km=null (borne infinie)")

    n = len(coords)
    d = [[0.0] * n for _ in range(n)]
    t = [[0.0] * n for _ in range(n)]
    for i in range(n):
        for j in range(n):
            if i == j:
                continue
            hv = haversine_km(coords[i], coords[j])
            palier = next(
                (p for p in paliers
                 if p.get("distance_max_km") is None or hv <= p["distance_max_km"]),
                paliers[-1],
            )
            d[i][j] = hv * palier["sinuosite"]
            t[i][j] = d[i][j] / palier["vitesse_kmh"] * 60.0
    return d, t
