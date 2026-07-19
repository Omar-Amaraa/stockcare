"""Solving: the routes actually returned to the backend."""

from __future__ import annotations

import solver as S
from conftest import payload, stop


def test_single_vehicle_route_is_complete_and_consistent(cfg):
    stops = [
        stop("a", 35.8300, 10.6100, 62, 12, cold=True),
        stop("b", 35.8400, 10.6200, 30, 15),
        stop("c", 35.8200, 10.5900, 45, 9),
    ]
    result = S.solve(payload(stops, [{"vehicleId": "v1", "capacityUnits": 90,
                                      "refrigerated": True}]), cfg)

    assert result["optimized"] is True
    assert result["status"] == "OPTIMAL"
    assert result["unfulfilledRequestIds"] == []

    route = result["routes"][0]
    assert route["vehicleId"] == "v1"
    assert [s["sequence"] for s in route["stops"]] == [1, 2, 3]
    assert {s["requestId"] for s in route["stops"]} == {"a", "b", "c"}
    assert route["capacityUsedUnits"] == 36
    assert route["totalDistanceKm"] > 0
    # Arrival minutes must increase along the sequence.
    arrivals = [s["estimatedArrivalMinute"] for s in route["stops"]]
    assert arrivals == sorted(arrivals)
    # Duration covers the return leg, so it exceeds the last arrival.
    assert route["totalDurationMinutes"] > arrivals[-1]


def test_fleet_plan_splits_load_across_vehicles(cfg):
    stops = [stop(chr(97 + i), 35.82 + 0.01 * i, 10.60 + 0.01 * i, 20 + 5 * i, 40)
             for i in range(4)]
    result = S.solve(payload(stops, [
        {"vehicleId": "v1", "capacityUnits": 90, "refrigerated": True},
        {"vehicleId": "v2", "capacityUnits": 90, "refrigerated": False},
    ]), cfg)

    assert result["optimized"] is True
    assert len(result["routes"]) >= 2
    served = [s["requestId"] for r in result["routes"] for s in r["stops"]]
    assert sorted(served) == ["a", "b", "c", "d"]
    assert len(served) == len(set(served)), "a stop must be served exactly once"
    for route in result["routes"]:
        assert route["capacityUsedUnits"] <= route["capacityUnits"]


def test_cold_chain_orders_only_ride_refrigerated_vehicles(cfg):
    stops = [
        stop("cold1", 35.8300, 10.6100, 60, 30, cold=True),
        stop("cold2", 35.8350, 10.6150, 55, 30, cold=True),
        stop("warm1", 35.8250, 10.6050, 20, 30),
    ]
    result = S.solve(payload(stops, [
        {"vehicleId": "fridge", "capacityUnits": 80, "refrigerated": True},
        {"vehicleId": "dry", "capacityUnits": 80, "refrigerated": False},
    ]), cfg)

    assert result["optimized"] is True
    dry = {r["vehicleId"]: r for r in result["routes"]}.get("dry")
    if dry:
        assert all(not s["coldChain"] for s in dry["stops"])


def test_urgent_stop_is_not_left_until_last(cfg):
    """The objective penalises lateness weighted by priority, so the critical
    stop must not be scheduled after both low-priority ones."""
    stops = [
        stop("low1", 35.8280, 10.6070, 8, 10),
        stop("low2", 35.8290, 10.6080, 8, 10),
        stop("urgent", 35.8450, 10.6300, 66, 10, cold=True),
    ]
    result = S.solve(payload(stops, [{"vehicleId": "v1", "capacityUnits": 90,
                                      "refrigerated": True}]), cfg)
    assert result["optimized"] is True
    sequence = [s["requestId"] for s in result["routes"][0]["stops"]]
    assert sequence.index("urgent") < 2, f"urgent served last in {sequence}"


def test_distant_pharmacy_stays_routable_with_tiered_geometry(cfg):
    """A depot must be able to reach a pharmacy well outside its own city.

    Regression test for the flat national speed/sinuosity bug: a single
    20.9km/h urban factor applied to every pair made any stop farther than
    about 50km from the depot structurally infeasible within H_max=480min,
    and because every approved stop MUST be served (no partial solve), that
    one distant pharmacy made the WHOLE fleet-wide solve infeasible, not
    just for itself but for every other pharmacy in the same approval
    batch. Sfax is about 235km from a Tunis-area depot; this must now solve.
    """
    far_stop = stop("far", 34.7406, 10.7603, 60, 20)
    near_stop = stop("near", 35.8300, 10.6100, 40, 15)
    result = S.solve(payload([near_stop, far_stop],
                             [{"vehicleId": "v1", "capacityUnits": 100}]), cfg)
    assert result["optimized"] is True
    assert result["status"] in ("OPTIMAL", "FEASIBLE_TIME_LIMIT")
    assert result["unfulfilledRequestIds"] == []


def test_result_carries_sla_lateness_for_explainability(cfg):
    result = S.solve(payload(
        [stop("a", 35.83, 10.61, 62, 12, cold=True), stop("b", 35.84, 10.62, 30, 15)],
        [{"vehicleId": "v1", "capacityUnits": 90, "refrigerated": True}],
    ), cfg)
    assert "totalLatenessMinutes" in result
    for s in result["routes"][0]["stops"]:
        assert "slaDeadlineMinute" in s and "latenessMinutes" in s
        assert s["priorityCoefficient"] > 0
