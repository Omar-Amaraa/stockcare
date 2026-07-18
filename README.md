# StockCare — MVP

**Predictive Demand & Priority-Aware Distribution network for pharmacies** (Team Chaneb+).

StockCare links pharmacy shortage forecasting, depot-side medicine prioritization and optimized
last-mile delivery into one closed loop. This repository is the working MVP: a Dockerized web
application with a Spring Boot backend, PostgreSQL database and (in the next batch) an Angular
frontend. Prediction, priority-scoring and route-optimization are implemented as **clearly labelled
mock / rule-based services behind interfaces**, ready to be replaced by the real LightGBM demand
model, priority model and MILP optimizer without rewriting the business workflow.

> **Build status — full MVP.** Backend (auth, inventory, simulated time, prediction, requests,
> depot management, priority, MILP fleet routing, deliveries + simulated live tracking over SSE,
> notifications, audit), an Angular + Tailwind CSS frontend (branded pharmacy + depot dashboards,
> Leaflet tracking map), a Python/LightGBM prediction service, PostgreSQL + Flyway, Docker Compose,
> seed data, OpenAPI docs and unit tests are all included.

---

## 1. Required software

| Tool | Version | Notes |
|------|---------|-------|
| Docker + Docker Compose | Docker 24+, Compose v2 | Recommended way to run everything |
| Java (JDK) | 21 (LTS) | Only needed for local (non-Docker) backend builds |
| Maven | 3.9+ | Only needed for local (non-Docker) backend builds |
| Node.js / npm | 20 LTS / 10+ | Frontend (batch 2) |
| PostgreSQL | 16 | Provided by Docker Compose |

Running via Docker requires **only Docker** — Java, Maven and PostgreSQL are all provided by the
containers.

## 2. Installation & quick start

```bash
# 1. Copy environment defaults
cp .env.example .env

# 2. Build and start the full stack (PostgreSQL + backend + frontend)
docker compose up --build

# Optional: include the Adminer DB console (http://localhost:8081)
docker compose --profile tools up --build
```

- **Frontend (UI):** http://localhost:4200
- **Backend API:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Prediction service (Python/LightGBM):** http://localhost:8000 (`/info`, `/health`, `/predict`)

On first start, Flyway creates the schema and the development seeder populates demo data (one depot,
five pharmacies, users, 24 medications, inventory with shortage cases, predictions, vehicles,
drivers and sample requests). Health check: `curl http://localhost:8080/actuator/health`.

**Try it:** open http://localhost:4200, log in as `ph.tunis@stockcare.tn` (pharmacy) or
`depot@stockcare.tn` (depot) with `Password123!`. As the depot, prioritize a request, approve it,
plan a delivery and press **Start** — then watch the vehicle move on the map in real time (the
pharmacy sees the same delivery under *Deliveries*).

## 3. Environment variables

Configured in `.env` (see `.env.example`):

| Variable | Default | Purpose |
|----------|---------|---------|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `stockcare` | Database credentials |
| `POSTGRES_PORT` | `5432` | Host port for PostgreSQL |
| `BACKEND_PORT` | `8080` | Host port for the API |
| `SPRING_PROFILES_ACTIVE` | `docker` | Spring profile (`docker`, `dev`) |
| `STOCKCARE_JWT_SECRET` | dev placeholder | **Change in production** (≥ 32 chars, HS256) |
| `STOCKCARE_JWT_EXPIRATION_MINUTES` | `120` | Token lifetime |
| `STOCKCARE_SEED_ENABLED` | `true` | Seed demo data on first start (dev only) |
| `STOCKCARE_CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Allowed frontend origins |
| `STOCKCARE_MODEL_PREDICTION_MODE` | `mock` | `mock` or `external` (prediction model) |
| `STOCKCARE_MODEL_PRIORITY_MODE` | `mock` | `mock` or `external` (priority model) |
| `STOCKCARE_MODEL_ROUTE_MODE` | `mock` (`external` in Compose) | `mock` or `external` (MILP optimizer) |
| `STOCKCARE_ROUTING_URL` | – | Routing service base URL (`http://routing-service:8000`) |
| `STOCKCARE_ROUTING_TIMEOUT_MS` | `90000` | Must stay above the solver time limit |
| `STOCKCARE_WORKFLOW_AUTO_ROUTE` | `false` (`true` in Compose) | Approval triggers a fleet-wide MILP solve |

## 4. Docker commands

```bash
docker compose up --build            # start db + backend
docker compose --profile tools up    # also start Adminer
docker compose down                  # stop
docker compose down -v               # stop and wipe the database volume
docker compose logs -f backend       # follow backend logs
```

## 5. Development commands (local, without Docker)

Requires JDK 21 + Maven and a running PostgreSQL (or `docker compose up db`).

```bash
# Backend (JDK 21 + Maven, plus a running PostgreSQL)
cd backend
mvn spring-boot:run                  # run with the default profile
mvn clean package                    # build the jar
mvn test                             # run unit tests

# Frontend (Node 20 + npm)
cd frontend
npm install
npm start                            # ng serve on http://localhost:4200 (proxies /api to :8080)
npm run build                        # production build
```

## 6. Default test accounts (development only)

All seed users share the password **`Password123!`**.

| Role | Email | Scope |
|------|-------|-------|
| ADMIN | `admin@stockcare.tn` | Unrestricted (dev/account management) |
| DEPOT | `depot@stockcare.tn` | Depot Central Tunis + its 5 pharmacies |
| PHARMACY | `ph.tunis@stockcare.tn` | Pharmacie Centrale Tunis |
| PHARMACY | `ph.ariana@stockcare.tn` | Pharmacie El Menzah |
| PHARMACY | `ph.sfax@stockcare.tn` | Pharmacie Sfax Ville |
| PHARMACY | `ph.sousse@stockcare.tn` | Pharmacie Sousse Medina |
| PHARMACY | `ph.nabeul@stockcare.tn` | Pharmacie Nabeul Centre |

Example login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ph.tunis@stockcare.tn","password":"Password123!"}'
```

More example requests: [`docs/api-examples.md`](docs/api-examples.md).

## 7. API documentation

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

Endpoint groups: `/api/auth`, `/api/pharmacies`, `/api/depots`, `/api/medications`, `/api/inventory`,
`/api/predictions`, `/api/requests`, `/api/depot/requests`, `/api/priorities`, `/api/deliveries`,
`/api/tracking` (SSE), `/api/vehicles`, `/api/drivers`, `/api/notifications`, `/api/audit`, `/api/time`.

## 8. Project architecture

A **modular monolith** — each module is self-contained (domain / repo / service / web / dto) so it
can later be extracted into a service. See [`docs/architecture.md`](docs/architecture.md) for the
full diagram and module list.

```
Angular frontend (batch 2)
        |
Spring Boot REST API  (controllers, validation, JWT, exception handling)
        |
Application services  (business logic, ownership enforcement)
        |
Domain modules        (auth, users, pharmacy, depot, medication, inventory,
        |               prediction, requests, priority, simulated time, model log)
PostgreSQL  (Flyway migrations)

Replaceable integration boundaries (mock now, external later):
  - Pharmacy ERP adapter
  - Stock prediction model (LightGBM)   ← model.txt
  - Priority-coefficient model
  - MILP route optimizer
  - GPS / tracking provider
  - Notification providers (email / SMS / push)
```

Key design points:
- **ApplicationClock** abstraction — all business logic reads "now" from it, so simulated time
  affects predictions, requests and audit timestamps consistently (`/api/time`).
- **Mock model interfaces** — `StockPredictionService`, `PriorityCalculationService` (and, in batch 2,
  `RouteOptimizationService`) each have a transparent mock plus a prepared `external` implementation,
  switched via `stockcare.models.*.mode`. Every result is labelled *simulated / rule-based*.
- **Model execution log** — every model call is recorded (`model_execution_log`) with mode, version,
  correlation id, status and duration for debugging.
- **Ownership enforcement** — `PharmacyAccessService` guarantees a pharmacy user only ever sees its
  own data, and a depot user only its associated pharmacies.

## 9. Real prediction model (Layer 1)

The demand-forecasting engine in [`prediction-service/`](prediction-service/) is a gradient-boosted
model (LightGBM / Poisson, with a pure-NumPy GBT fallback) that forecasts H-day demand per
(pharmacy, medicine); `shortage/gap.py` converts the forecast into restock needs
`G = max(0, D + S − X)`. It is exposed as a FastAPI service and called by the backend through the
`StockPredictionService` boundary when `stockcare.models.prediction.mode=external` (the Docker
default). If the service is unreachable the backend degrades gracefully (no predictions) rather than
failing; set the mode to `mock` to use the built-in rule-based predictor with no Python dependency.

```bash
curl http://localhost:8000/info          # backend, horizon, validation metrics
# The backend calls POST http://prediction-service:8000/predict internally.
```

Offline pipeline (train / evaluate / backtest) and its own unit tests live in the same folder:
`cd prediction-service && pip install -r requirements.txt && python run.py && pytest -q`.

> The shipped model is trained on a synthetic 2-year Tunisian pharmacy panel. Live lag/rolling
> features are approximated from each item's average daily consumption until real POS history is
> streamed; region, category, cold-chain and calendar/epidemiological signals are real.

## 9 bis. Real route optimizer — MILP CARE (Layer 3)

The optimizer in [`routing-service/`](routing-service/) is a **VRP-MILP** (PuLP / CBC) that computes
the cost-optimal delivery trajectory for the whole fleet in a single solve. It wraps the model in
[`milp_care/`](milp_care/) unmodified and is called through the `RouteOptimizationService` boundary
when `stockcare.models.route-optimization.mode=external` (the Docker default).

It minimises transport cost + **priority-weighted SLA lateness** + arrival earliness + vehicle
activation cost, under capacity, cold-chain and time-window constraints. Priority coefficients are
**not recomputed here** — they arrive from the priority model (Layer 2) and enter the objective
directly, and each stop's SLA deadline is interpolated from them (higher priority, tighter window).

```bash
curl http://localhost:8002/info          # SLA, geometry and solver parameters in force
# The backend calls POST http://routing-service:8000/optimize internally.
```

Two ways in:

- `POST /api/deliveries/plan` — one vehicle, unchanged API.
- `POST /api/deliveries/plan/fleet` — the whole approved backlog across the fleet; the optimizer
  assigns pharmacies to vehicles and orders each route, and one delivery is created per route.

Unlike prediction, a routing failure is **not** silently degraded: a delivery planned from a broken
optimizer would be operationally wrong, so transport errors and infeasible instances surface with an
explicit reason (missing capacity, missing refrigerated capacity, an order larger than any vehicle).
Set the mode to `mock` for the built-in greedy router with no Python dependency.

Tests: `cd routing-service && pip install -r requirements.txt && pytest -q`.

## 10. Automated, event-driven workflows

The platform is proactive — users provide data and the system runs prediction, prioritization,
routing and status updates automatically. There are **no "run prediction", "calculate priority" or
"plan route" buttons** in the normal flow. The full chain is:

```
inventory change -> prediction -> draft request -> submit -> priority -> approval -> MILP fleet plan -> delivery
```

- **Auto-prediction.** Changing inventory (add / edit / adjust / delete) or the simulated clock
  publishes a domain event; an async listener runs the model in the background. A per-pharmacy
  `prediction_run` row tracks the async state (`PENDING → PROCESSING → COMPLETED / FAILED`) with a
  correlation id, timestamps, retry count, model version and an **input hash for idempotency**
  (unchanged inventory is not re-predicted). Stale results are flagged **outdated** and recomputed.
- **Auto-draft.** When shortages are predicted, draft requests are created automatically (config:
  `stockcare.workflow.shortage-action = draft | alert | off`), each referencing its source prediction,
  with **duplicate prevention** (no second request while one is open for the same pharmacy+medication).
  The pharmacy still reviews and submits (unless `auto-submit=true`).
- **Auto-priority.** On submit, a request advances automatically
  `SUBMITTED → RECEIVED → PRIORITY_PENDING → PRIORITIZED` and the coefficient is computed in the
  background — the depot never clicks "calculate".
- **Auto-route.** Approving requests for planning publishes an event; the depot's whole approved,
  unplanned backlog is then handed to the MILP optimizer in **one fleet-wide solve** and the
  resulting deliveries are created (config: `stockcare.workflow.auto-route`, threshold
  `auto-route-min-requests`). Runs are serialised per depot and each run re-reads the backlog while
  skipping requests already committed to a delivery, so approving a batch produces one coherent plan
  instead of a delivery per click. If the solve fails, the requests stay approved and the depot is
  notified — nothing is planned on a guess.
- **Live updates.** The frontend subscribes to `GET /api/stream` (SSE) and reflects state changes
  without a page reload, with controlled polling as a fallback. The UI shows processing / completed /
  outdated / failed states; a manual **Retry** appears only on failure.

Mock and real models follow the same automated path — swapping in a real model changes nothing about
the workflow. Config lives under `stockcare.workflow.*` (see `.env.example`).

## 11. Simulated application time

Because the future demand model depends on season, month, weekday, holidays and history, the app has
a central simulated clock:

```bash
# Requires a Bearer token
curl -X POST http://localhost:8080/api/time/simulate \
  -H 'Authorization: Bearer <TOKEN>' -H 'Content-Type: application/json' \
  -d '{"dateTime":"2026-01-15T09:00:00Z","frozen":false}'

curl -X POST http://localhost:8080/api/time/advance -H 'Authorization: Bearer <TOKEN>' \
  -H 'Content-Type: application/json' -d '{"days":7,"hours":0}'

curl -X POST http://localhost:8080/api/time/reset  -H 'Authorization: Bearer <TOKEN>'
curl      http://localhost:8080/api/time            -H 'Authorization: Bearer <TOKEN>'
```

## 12. Testing

```bash
cd backend && mvn test
```

Unit tests cover the core business logic that does not require a database: the mock shortage
predictor (shortage-gap logic), the priority formula (ordering + bounds + explainability), the mock
route optimizer (capacity packing + cold-chain handling) and the simulated `ApplicationClock`
(real/simulated/frozen/reset). The full end-to-end workflow (login → inventory → predict → request →
depot priority → plan → simulated delivery → track → delivered) is documented as a runnable script in
[`docs/api-examples.md`](docs/api-examples.md). Broader `@SpringBootTest` integration tests are wired
to run against H2/Testcontainers via the `test` profile (`application-test.yml`).

## 13. Known limitations & assumptions

- **Prediction runs the real LightGBM model** (section 9) and **routing runs the real VRP-MILP**
  (section 9 bis) in `external` mode. **Priority remains a transparent rule-based mock** behind
  `PriorityCalculationService`, ready to switch to `external`; its coefficients are already what the
  MILP consumes, so replacing it changes no downstream code.
- The MILP is exact, so solve time grows with stops x vehicles. Past roughly 20 stops the solver may
  hit its time limit and return the best solution found (`status=FEASIBLE_TIME_LIMIT`) rather than a
  proven optimum. Tune `solveur.time_limit` in `routing-service/config.json`.
- GPS tracking is **simulated** by a scheduled server-side mover pushing positions over SSE.
- ERP is not connected; inventory is managed manually behind the prepared `PharmacyErpAdapter`.
- One depot in the MVP; every pharmacy is associated to it via `depot_pharmacy` (a link entity, ready
  to become many-to-many).
- Schema is owned by **Flyway**; the backend runs with `spring.jpa.hibernate.ddl-auto=validate`. If a
  local PostgreSQL type nuance ever blocks validation, set `SPRING_JPA_HIBERNATE_DDL_AUTO=none`.
- Local builds use an installed Maven 3.9+ / Node 20, or (recommended) the Docker build.

## 14. Deliverables

Backend + frontend + prediction-service source, Dockerfiles + Compose, Flyway migrations (V1/V2),
seed data, unit tests, OpenAPI docs, README, architecture doc, API examples, and the
future-integration / assumptions lists above.
