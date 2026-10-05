"""
Bridge between the backend's generic routing payload and the MILP CARE solver.

Responsibilities, in order:
  1. Turn the stops sent by the backend into a MILP `Scenario` — the priority
     coefficient arrives already computed by the priority model, so it is injected
     straight into the objective, and the SLA deadline T[i] is interpolated from it.
  2. Turn the depot's vehicle table into a `Fleet` (capacity, refrigeration, cost/km).
  3. Recompute the calibrated distance/time matrices for the coordinates of this run.
  4. Solve, then reconstruct per-vehicle ordered routes with arrival minutes.

The MILP itself (`vrp_milp_pharma`) is used unmodified.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

import milp_path  # noqa: F401  — puts the MILP sources on sys.path

from demandes import fenetre_sla, temps_service          # noqa: E402
from geometrie import build_matrix_calibrated, build_matrix_tiered  # noqa: E402
from vrp_milp_pharma import (                            # noqa: E402
    Fleet, Scenario, build_and_solve_vrp,
)

ROOT = Path(__file__).resolve().parent
CONFIG_PATH = ROOT / "config.json"

SOLVER_VERSION = "milp-care-v1"


def load_config(path: Path | str = CONFIG_PATH) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


# =============================================================================
# Feasibility pre-checks
# =============================================================================


def feasibility_alerts(scenario: Scenario, fleet: Fleet) -> List[str]:
    """Structural causes of infeasibility, stated in operational terms.

    A solver that answers 'Infeasible' explains nothing to the depot. These checks
    say precisely what blocks and by how much, before spending the time limit.
    """
    alerts: List[str] = []

    load = sum(scenario.q[1:])
    capacity = sum(fleet.Q)
    if load > capacity:
        alerts.append(
            f"INSUFFICIENT_CAPACITY: {load:.1f} units to deliver for {capacity:.1f} "
            f"of fleet capacity (deficit {load - capacity:.1f}). Add a vehicle or "
            f"split the plan into two cycles."
        )

    cold_load = sum(scenario.q[i] for i in range(1, scenario.n_nodes) if scenario.cc[i])
    cold_capacity = sum(q for q, r in zip(fleet.Q, fleet.R) if r)
    if cold_load > cold_capacity:
        alerts.append(
            f"INSUFFICIENT_COLD_CAPACITY: {cold_load:.1f} temperature-sensitive units "
            f"for {cold_capacity:.1f} of refrigerated capacity "
            f"(deficit {cold_load - cold_capacity:.1f}). Assign a refrigerated vehicle."
        )

    if scenario.n_nodes > 1:
        biggest = max(scenario.q[1:])
        if biggest > max(fleet.Q):
            i = scenario.q.index(biggest)
            alerts.append(
                f"ORDER_TOO_LARGE: stop {i} requires {biggest:.1f} units, the largest "
                f"vehicle carries {max(fleet.Q):.1f}. Orders are not split across vehicles."
            )

    return alerts


# =============================================================================
# Payload -> MILP
# =============================================================================


@dataclass
class BuiltProblem:
    scenario: Scenario
    fleet: Fleet
    d: List[List[float]]
    t: List[List[float]]
    stop_refs: List[Dict[str, Any]]     # index 1..n -> original stop payload
    vehicle_refs: List[Dict[str, Any]]  # index 0..k-1 -> original vehicle payload


def normaliser_priorite(brut: float, cfg_pr: Dict[str, Any]) -> float:
    """Rescales the incoming coefficient into the MILP's priority domain.

    The priority model owns the ranking; this only converts units. The backend emits 0..100 while
    the objective and the SLA anchors are expressed in the catalogue's 0..1 domain, so sending the
    raw value would clamp every stop to the tightest deadline and drown the distance term.
    """
    echelle = float(cfg_pr.get("echelle_entree", 1.0)) or 1.0
    p = brut / echelle
    return min(max(p, float(cfg_pr.get("borne_min", 0.0))), float(cfg_pr.get("borne_max", 1.0)))


def build_problem(payload: Dict[str, Any], cfg: Dict[str, Any]) -> BuiltProblem:
    cfg_pr = cfg.get("priorite", {})
    cfg_sla = cfg["sla"]
    cfg_serv = cfg["dechargement"]
    cfg_geo = cfg["geometrie"]
    cfg_fleet = cfg["flotte"]

    stops = payload.get("stops") or []
    if not stops:
        raise ValueError("No stops supplied: nothing to route.")

    coords = [(float(payload["depotLongitude"]), float(payload["depotLatitude"]))]
    q: List[float] = [0.0]
    cc: List[int] = [0]
    pr: List[float] = [0.0]
    sigma: List[float] = [0.0]
    T: List[float] = [0.0]
    stop_refs: List[Dict[str, Any]] = [{}]

    default_lines = int(cfg_serv.get("lignes_par_defaut", 3))

    for stop in stops:
        priority = normaliser_priorite(float(stop.get("priorityCoefficient") or 0.0), cfg_pr)
        cold = 1 if stop.get("coldChain") else 0
        lines = int(stop.get("lineCount") or default_lines)

        coords.append((float(stop["longitude"]), float(stop["latitude"])))
        q.append(float(stop.get("totalUnits") or 0.0))
        cc.append(cold)
        pr.append(round(priority, 4))
        sigma.append(temps_service(lines, cfg_serv))

        # An explicit time window from the backend wins over the priority-derived SLA.
        window_end = stop.get("timeWindowEndMinute")
        T.append(float(window_end) if window_end is not None
                 else fenetre_sla(priority, cold, cfg_sla))
        stop_refs.append(stop)

    scenario = Scenario(coords=coords, q=q, cc=cc, pr=pr, sigma=sigma, T=T)

    vehicles = payload.get("vehicles") or []
    if not vehicles:
        raise ValueError("No vehicles supplied: nothing to route with.")

    Q: List[float] = []
    R: List[int] = []
    c_f: List[float] = []
    vehicle_refs: List[Dict[str, Any]] = []
    for vehicle in vehicles:
        refrigerated = 1 if vehicle.get("refrigerated") else 0
        Q.append(float(vehicle.get("capacityUnits") or 0.0))
        R.append(refrigerated)
        c_f.append(float(cfg_fleet["cout_km_refrigere"] if refrigerated
                         else cfg_fleet["cout_km_defaut"]))
        vehicle_refs.append(vehicle)

    fleet = Fleet(Q=Q, R=R, c_f=c_f)

    if "paliers" in cfg_geo:
        # Distance-tiered speed/sinuosity: keeps the real TomTom urban calibration for
        # nearby stops, but no longer applies it to depot<->pharmacy pairs hundreds of
        # km apart (see geometrie.build_matrix_tiered for why the flat factor broke
        # nationwide routing).
        d, t = build_matrix_tiered(coords, paliers=cfg_geo["paliers"])
    else:
        # Legacy path: a single flat factor everywhere (fine only for a tight local
        # cluster, e.g. the milp_care_delivery Sousse demo).
        d, t = build_matrix_calibrated(
            coords,
            sinuosite=cfg_geo["facteur_sinuosite"],
            vitesse_kmh=cfg_geo["vitesse_effective_kmh"],
        )
    return BuiltProblem(scenario, fleet, d, t, stop_refs, vehicle_refs)


# =============================================================================
# MILP -> response
# =============================================================================


def _unserved(problem: BuiltProblem) -> List[Any]:
    return [s.get("requestId") for s in problem.stop_refs[1:]]


def solve(payload: Dict[str, Any], cfg: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    cfg = cfg or load_config()
    problem = build_problem(payload, cfg)
    scenario, fleet = problem.scenario, problem.fleet

    alerts = feasibility_alerts(scenario, fleet)
    if alerts:
        return {
            "status": "INFEASIBLE",
            "optimized": False,
            "objectiveValue": None,
            "routes": [],
            "unfulfilledRequestIds": _unserved(problem),
            "note": "Solve aborted by pre-check. " + " | ".join(alerts),
            "solverVersion": SOLVER_VERSION,
        }

    solver_cfg = dict(cfg["solveur"])
    if payload.get("maxRouteMinutes"):
        solver_cfg["H_max"] = float(payload["maxRouteMinutes"])
    if payload.get("timeLimitSeconds"):
        solver_cfg["time_limit"] = int(payload["timeLimitSeconds"])

    solution = build_and_solve_vrp(scenario, fleet, problem.d, problem.t, **solver_cfg)

    if solution.status not in ("Optimal", "Not Solved") or not solution.routes:
        return {
            "status": solution.status.upper().replace(" ", "_"),
            "optimized": False,
            "objectiveValue": None,
            "routes": [],
            "unfulfilledRequestIds": _unserved(problem),
            "note": f"Solver returned status '{solution.status}' with no usable route.",
            "solverVersion": SOLVER_VERSION,
        }

    routes: List[Dict[str, Any]] = []
    served: set[int] = set()

    for k, tour in sorted(solution.routes.items()):
        interior = [i for i in tour if i != 0]
        if not interior:
            continue
        served.update(interior)

        # tour is [0, i, j, ..., 0]; the closing leg back to the depot is included.
        distance_km = sum(problem.d[a][b] for a, b in zip(tour, tour[1:]))

        ordered: List[Dict[str, Any]] = []
        for seq, node in enumerate(interior, start=1):
            ref = problem.stop_refs[node]
            ordered.append({
                "sequence": seq,
                "requestId": ref.get("requestId"),
                "pharmacyId": ref.get("pharmacyId"),
                "estimatedArrivalMinute": round(solution.arrival.get(node, 0.0), 1),
                "slaDeadlineMinute": scenario.T[node],
                "latenessMinutes": round(solution.lateness.get(node, 0.0), 1),
                "priorityCoefficient": scenario.pr[node],
                "coldChain": bool(scenario.cc[node]),
                "units": scenario.q[node],
            })

        last = interior[-1]
        duration_min = (solution.arrival.get(last, 0.0)
                        + scenario.sigma[last]
                        + problem.t[last][0])

        vehicle = problem.vehicle_refs[k]
        routes.append({
            "vehicleId": vehicle.get("vehicleId"),
            "stops": ordered,
            "totalDistanceKm": round(distance_km, 2),
            "totalDurationMinutes": round(duration_min, 1),
            "capacityUsedUnits": int(round(sum(scenario.q[i] for i in interior))),
            "capacityUnits": int(fleet.Q[k]),
            "refrigerated": bool(fleet.R[k]),
        })

    unfulfilled = [problem.stop_refs[i].get("requestId")
                   for i in range(1, scenario.n_nodes) if i not in served]

    total_lateness = sum(solution.lateness.get(i, 0.0) for i in range(1, scenario.n_nodes))
    proven = solution.status == "Optimal"
    quality = "MILP optimum" if proven else "best MILP solution found within the time limit"
    note = (f"{quality} over {len(routes)} vehicle(s) and {len(served)} stop(s); "
            f"total SLA lateness {total_lateness:.1f} min.")
    if unfulfilled:
        note += f" {len(unfulfilled)} request(s) left unassigned (capacity)."

    return {
        "status": "OPTIMAL" if proven else "FEASIBLE_TIME_LIMIT",
        "optimized": True,
        "objectiveValue": round(solution.objective, 4),
        "routes": routes,
        "unfulfilledRequestIds": unfulfilled,
        "note": note,
        "solverVersion": SOLVER_VERSION,
        "totalLatenessMinutes": round(total_lateness, 1),
    }
