# StockCare Routing Service (Layer 3)

HTTP serving layer for **MILP CARE**, the VRP-MILP that computes the optimal delivery
trajectory. It wraps `milp_care/milp_care_delivery/src` unmodified and exposes it to the
Spring backend, the same way `prediction-service` wraps the forecasting model.

## Where it sits

```
inventory  →  prediction (Layer 1)  →  request  →  priority (Layer 2)  →  approval
                                                                            │
                                                       priority coefficients │
                                                                            ▼
                                                            routing-service (Layer 3)
                                                                            │
                                                                    optimal routes
                                                                            ▼
                                                                      deliveries
```

Priority coefficients arrive **already computed**. This service does not re-rank anything: it
consumes the coefficients as objective weights, derives each stop's SLA deadline from them, and
returns the cost-optimal vehicle assignment and visiting order.

## What the MILP optimizes

Minimise transport cost + priority-weighted SLA lateness + arrival earliness + vehicle
activation cost, subject to:

- vehicle capacity (orders are never split across vehicles),
- cold chain (thermosensitive orders only ride refrigerated vehicles),
- SLA windows `T[i]`, interpolated from the priority coefficient — higher priority, tighter window,
- MTZ-temporal subtour elimination over a planning horizon `H_max`.

## API

### `POST /optimize`

```jsonc
{
  "depotId": "…",
  "depotLatitude": 35.8256,
  "depotLongitude": 10.6084,
  "stops": [
    {
      "requestId": "…",
      "pharmacyId": "…",
      "latitude": 35.83, "longitude": 10.61,
      "priorityCoefficient": 0.62,   // from the priority model
      "totalUnits": 12,
      "coldChain": true,
      "lineCount": 4,                // drives the unloading time
      "timeWindowEndMinute": null    // set to override the derived SLA
    }
  ],
  "vehicles": [{ "vehicleId": "…", "capacityUnits": 90, "refrigerated": true }],
  "maxRouteMinutes": null,
  "timeLimitSeconds": null
}
```

Response:

```jsonc
{
  "status": "OPTIMAL",              // or FEASIBLE_TIME_LIMIT | INFEASIBLE | INVALID_INPUT
  "optimized": true,
  "objectiveValue": 126.86,
  "routes": [{
    "vehicleId": "…",
    "stops": [{
      "sequence": 1, "requestId": "…", "pharmacyId": "…",
      "estimatedArrivalMinute": 2.3,
      "slaDeadlineMinute": 41.0, "latenessMinutes": 0.0,
      "priorityCoefficient": 0.62, "coldChain": true, "units": 12.0
    }],
    "totalDistanceKm": 10.74, "totalDurationMinutes": 48.6,
    "capacityUsedUnits": 72, "capacityUnits": 90, "refrigerated": true
  }],
  "unfulfilledRequestIds": [],
  "note": "MILP optimum over 2 vehicle(s) and 4 stop(s); total SLA lateness 0.0 min.",
  "totalLatenessMinutes": 0.0
}
```

A modelling problem never raises: an instance that cannot be served comes back with
`optimized: false` and a `note` naming the exact shortfall (missing capacity, missing
refrigerated capacity, an indivisible order larger than any vehicle), so the depot sees a
reason rather than a stack trace. These pre-checks run *before* the solver, so an impossible
plan costs milliseconds instead of the full time limit.

### `GET /health` · `GET /info`

`/info` returns the SLA, service-time, geometry and solver parameters actually in force —
useful when a route looks surprising and you need to know how it was tuned.

## Configuration

All tuning lives in `config.json` in this service, so the parameters can change without
redeploying the backend:

| Block | What it controls |
|---|---|
| `sla` | Priority → deadline interpolation, cold-chain bonus, floor |
| `dechargement` | Service time per stop (flat rate + per order line) |
| `geometrie` | Sinuosity factor and effective speed (TomTom-calibrated for Sousse) |
| `flotte` | Cost per km, standard and refrigerated |
| `solveur` | `H_max`, objective weights `w_p` / `gamma` / `w_v`, time limit |

## Running

```bash
pip install -r requirements.txt
uvicorn app:app --port 8000            # from this directory
pytest tests/ -q
```

With Docker, the build context is the **repository root** (the image needs both this service
and the MILP sources):

```bash
docker build -f routing-service/Dockerfile -t stockcare-routing .
# or simply
docker compose up routing-service
```

## Backend wiring

```
STOCKCARE_MODEL_ROUTE_MODE=external
STOCKCARE_ROUTING_URL=http://routing-service:8000
STOCKCARE_ROUTING_TIMEOUT_MS=90000     # keep above the solver time limit
```

Set `STOCKCARE_MODEL_ROUTE_MODE=mock` to fall back to the backend's built-in greedy router
(useful for tests and for running the stack without this service).
