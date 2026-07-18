"""Shared fixtures and payload builders for the routing-service tests.

Priorities are written on the backend's 0..100 scale, exactly as the Spring service sends them.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import solver as S  # noqa: E402

DEPOT = {"depotLatitude": 35.8256, "depotLongitude": 10.6084}


def stop(rid: str, lat: float, lon: float, priority: float, units: float,
         cold: bool = False, lines: int = 3) -> dict:
    return {
        "requestId": rid,
        "pharmacyId": f"pharm-{rid}",
        "latitude": lat,
        "longitude": lon,
        "priorityCoefficient": priority,
        "totalUnits": units,
        "coldChain": cold,
        "lineCount": lines,
    }


def payload(stops: list[dict], vehicles: list[dict]) -> dict:
    return {**DEPOT, "stops": stops, "vehicles": vehicles, "timeLimitSeconds": 30}


@pytest.fixture(scope="session")
def cfg() -> dict:
    return S.load_config()
