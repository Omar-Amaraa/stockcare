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
