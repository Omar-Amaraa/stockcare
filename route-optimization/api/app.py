"""
StockCare Routing Service (Layer 3) — HTTP wrapper around the MILP CARE optimizer.

Exposes the VRP-MILP behind a small REST API so the Spring backend can request an
optimal delivery trajectory in `external` mode. Priority coefficients arrive already
computed by the priority model (Layer 2); this service consumes them as objective
weights and returns per-vehicle ordered routes with arrival minutes and SLA lateness.

The MILP formulation itself is unchanged; this only adds a serving surface.
"""

from __future__ import annotations

from typing import Any, Optional

from fastapi import FastAPI
from pydantic import BaseModel, Field

import solver as S

app = FastAPI(title="StockCare Routing Service", version="1.0.0")

_config: dict[str, Any] = {}


class Stop(BaseModel):
    requestId: str
    pharmacyId: str
    latitude: float
    longitude: float
    priorityCoefficient: float = Field(0.0, description="From the priority model; rescaled per config.priorite")
    totalUnits: float = 0.0
    coldChain: bool = False
    lineCount: Optional[int] = Field(None, description="Order lines, drives the service time")
    timeWindowStartMinute: Optional[float] = None
    timeWindowEndMinute: Optional[float] = Field(None, description="Overrides the priority-derived SLA")


class Vehicle(BaseModel):
    vehicleId: str
    capacityUnits: float = 0.0
    refrigerated: bool = False


class OptimizeRequest(BaseModel):
    depotId: Optional[str] = None
    depotLatitude: float
    depotLongitude: float
    stops: list[Stop] = []
    vehicles: list[Vehicle] = []
    maxRouteMinutes: Optional[float] = None
    timeLimitSeconds: Optional[int] = None


@app.on_event("startup")
def _startup() -> None:
    global _config
    _config = S.load_config()


@app.get("/health")
def health() -> dict:
    return {
        "status": "UP" if _config else "STARTING",
        "solver": "CBC/PuLP",
        "modelVersion": S.SOLVER_VERSION,
    }


@app.get("/info")
def info() -> dict:
    """Exposes the tuning parameters actually in force, for audit and support."""
    def clean(block: str) -> dict:
        return {k: v for k, v in _config.get(block, {}).items() if not k.startswith("_")}

    return {
        "modelVersion": S.SOLVER_VERSION,
        "milpSource": str(S.milp_path.MILP_SRC),
        "priority": clean("priorite"),
        "sla": clean("sla"),
        "service": clean("dechargement"),
        "geometry": clean("geometrie"),
        "solver": clean("solveur"),
    }


@app.post("/optimize")
def optimize(request: OptimizeRequest) -> dict:
    """Solves the VRP-MILP and returns the optimal trajectory per vehicle.

    Never raises on a modelling problem: an infeasible or unsolvable instance comes
    back as a structured result with `optimized=false` and an explanatory note, so
    the backend can surface the reason to the depot instead of a stack trace.
    """
    payload = request.model_dump()
    try:
        return S.solve(payload, _config or S.load_config())
    except ValueError as exc:
        return {
            "status": "INVALID_INPUT",
            "optimized": False,
            "objectiveValue": None,
            "routes": [],
            "unfulfilledRequestIds": [s.requestId for s in request.stops],
            "note": str(exc),
            "solverVersion": S.SOLVER_VERSION,
        }
