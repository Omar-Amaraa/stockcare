"""Scenario construction: priority scale, SLA windows, service time, fleet, pre-checks."""

from __future__ import annotations

import pytest

import solver as S
from conftest import payload, stop


# --- Priority scale ----------------------------------------------------------
# The backend emits coefficients on 0..100; the MILP objective and the SLA anchors live in the
# catalogue's 0..1 domain. Getting this conversion wrong silently clamps every stop to the
# tightest deadline, so it is asserted explicitly.


def test_backend_scale_is_converted_into_the_milp_domain(cfg):
    problem = S.build_problem(payload([stop("a", 35.83, 10.61, 62, 5)],
                                      [{"vehicleId": "v1", "capacityUnits": 50}]), cfg)
    assert problem.scenario.pr[1] == pytest.approx(0.62)


def test_priorities_stay_distinct_across_the_backend_range(cfg):
    problem = S.build_problem(payload(
        [stop(str(p), 35.83 + 0.001 * i, 10.61, p, 5) for i, p in enumerate((5, 25, 50, 75, 100))],
        [{"vehicleId": "v1", "capacityUnits": 200}],
    ), cfg)
    derived = problem.scenario.pr[1:]
    assert len(set(derived)) == 5, "the whole 0..100 range must not collapse to one value"
    assert derived == sorted(derived)
    assert max(derived) <= 1.0


def test_a_unit_scale_config_passes_coefficients_through(cfg):
    """A future priority model emitting 0..1 only needs echelle_entree=1.0."""
    unit_cfg = {**cfg, "priorite": {**cfg["priorite"], "echelle_entree": 1.0}}
    problem = S.build_problem(payload([stop("a", 35.83, 10.61, 0.62, 5)],
                                      [{"vehicleId": "v1", "capacityUnits": 50}]), unit_cfg)
    assert problem.scenario.pr[1] == pytest.approx(0.62)


# --- Scenario construction ---------------------------------------------------


def test_priority_drives_a_tighter_sla_window(cfg):
    problem = S.build_problem(payload(
        [stop("a", 35.83, 10.61, 66, 10), stop("b", 35.84, 10.62, 6, 10)],
        [{"vehicleId": "v1", "capacityUnits": 100, "refrigerated": True}],
    ), cfg)
    # Node 1 is the critical one: its deadline must be strictly earlier.
    assert problem.scenario.T[1] < problem.scenario.T[2]
    assert problem.scenario.pr[1] > problem.scenario.pr[2]


def test_cold_chain_tightens_the_window_further(cfg):
    warm = S.build_problem(payload([stop("a", 35.83, 10.61, 40, 5)],
                                   [{"vehicleId": "v1", "capacityUnits": 50}]), cfg)
    cold = S.build_problem(payload([stop("a", 35.83, 10.61, 40, 5, cold=True)],
                                   [{"vehicleId": "v1", "capacityUnits": 50}]), cfg)
    assert cold.scenario.T[1] < warm.scenario.T[1]
    assert cold.scenario.cc[1] == 1


def test_service_time_grows_with_order_lines(cfg):
    few = S.build_problem(payload([stop("a", 35.83, 10.61, 30, 5, lines=1)],
                                  [{"vehicleId": "v1", "capacityUnits": 50}]), cfg)
    many = S.build_problem(payload([stop("a", 35.83, 10.61, 30, 5, lines=9)],
                                   [{"vehicleId": "v1", "capacityUnits": 50}]), cfg)
    assert many.scenario.sigma[1] > few.scenario.sigma[1]


def test_explicit_time_window_overrides_the_derived_sla(cfg):
    s = stop("a", 35.83, 10.61, 66, 5)
    s["timeWindowEndMinute"] = 999.0
    problem = S.build_problem(payload([s], [{"vehicleId": "v1", "capacityUnits": 50}]), cfg)
    assert problem.scenario.T[1] == 999.0


def test_refrigerated_vehicles_carry_the_higher_km_cost(cfg):
    problem = S.build_problem(payload(
        [stop("a", 35.83, 10.61, 30, 5)],
        [{"vehicleId": "v1", "capacityUnits": 50, "refrigerated": True},
         {"vehicleId": "v2", "capacityUnits": 50, "refrigerated": False}],
    ), cfg)
    assert problem.fleet.R == [1, 0]
    assert problem.fleet.c_f[0] > problem.fleet.c_f[1]


# --- Pre-checks --------------------------------------------------------------


def test_capacity_shortfall_is_reported_without_solving(cfg):
    result = S.solve(payload(
        [stop("a", 35.83, 10.61, 50, 200)],
        [{"vehicleId": "v1", "capacityUnits": 50}],
    ), cfg)
    assert result["status"] == "INFEASIBLE"
    assert result["optimized"] is False
    assert "INSUFFICIENT_CAPACITY" in result["note"]
    assert result["unfulfilledRequestIds"] == ["a"]


def test_cold_chain_shortfall_is_reported(cfg):
    result = S.solve(payload(
        [stop("a", 35.83, 10.61, 50, 30, cold=True)],
        [{"vehicleId": "v1", "capacityUnits": 100, "refrigerated": False}],
    ), cfg)
    assert result["status"] == "INFEASIBLE"
    assert "COLD" in result["note"]


def test_empty_input_is_rejected_cleanly(cfg):
    with pytest.raises(ValueError):
        S.build_problem(payload([], [{"vehicleId": "v1", "capacityUnits": 50}]), cfg)
    with pytest.raises(ValueError):
        S.build_problem(payload([stop("a", 35.83, 10.61, 30, 5)], []), cfg)
