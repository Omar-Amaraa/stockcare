# StockCare — Architecture

## Overview

StockCare is a **modular monolith** built with Spring Boot 3.3 (Java 21) and PostgreSQL 16. Each
functional area is a module with clean internal layering, so a module can later be promoted to a
standalone service with minimal change.

```
                 ┌──────────────────────────────┐
                 │   Angular frontend (batch 2)  │
                 └───────────────┬──────────────┘
                                 │ HTTPS / JSON, JWT Bearer
                 ┌───────────────▼──────────────┐
                 │        REST Controllers        │  validation, status codes,
                 │  (thin, no business logic)     │  centralized error handling
                 └───────────────┬──────────────┘
                 ┌───────────────▼──────────────┐
                 │      Application Services      │  business rules,
                 │   + ownership enforcement      │  transactions
                 └───────────────┬──────────────┘
        ┌────────────────────────┼────────────────────────┐
        ▼                        ▼                         ▼
 ┌────────────┐         ┌────────────────┐        ┌──────────────────┐
 │  Domain    │         │   SPI / model  │        │  Integration      │
 │  entities  │         │  interfaces    │        │  adapters (mock)  │
 │  + repos   │         │  (mock/ext)    │        │  ERP, models      │
 └─────┬──────┘         └────────────────┘        └──────────────────┘
       ▼
 ┌────────────┐
 │ PostgreSQL │  (schema managed by Flyway)
 └────────────┘
```

## Layering per module

```
modules/<name>/
  domain/   JPA entities + enums (extend BaseEntity: UUID id, audit fields, @Version)
  repo/     Spring Data repositories
  service/  application services, mappers (business logic lives here)
  spi/      replaceable model interfaces + transport records (prediction, priority)
  web/      REST controllers (thin)
  dto/      request/response records
```

Cross-cutting packages:

```
common/audit   BaseEntity, JPA auditor
common/error   ApiError, exceptions, GlobalExceptionHandler, CorrelationIdFilter
common/api     PageResponse
config         JwtProperties, CorsProperties, ModelsProperties, OpenApiConfig, DevDataSeeder
security       JwtService, filter, CustomUserDetails, SecurityConfig, SecurityUtils
time           ApplicationClock abstraction + simulated-time service
integration    ERP adapter + model execution log / recorder
```

## Modules delivered (batch 1)

Authentication & authorization · Users & accounts · Pharmacy management · Depot management ·
Depot–pharmacy association · Medication catalogue · Pharmacy inventory + stock adjustments ·
Stock prediction integration (mock) · Shortage detection · Pharmacy requests · Priority calculation
(mock) · Simulated application time · Model execution log.

## Modules planned (batch 2)

Route optimization integration (MILP boundary + mock) · Deliveries · Delivery tracking (SSE/WebSocket
simulator + Leaflet map) · Notifications · Audit history views · Angular frontend · Test suites.

## Future external integration points

| Boundary | Interface (batch) | Config switch |
|----------|-------------------|---------------|
| Pharmacy ERP | `PharmacyErpAdapter` (batch 2) | — |
| Stock prediction (LightGBM, `model.txt`) | `StockPredictionService` → **live** via `prediction-service/` REST | `stockcare.models.prediction.mode` (`external` wired) |
| Priority coefficient model | `PriorityCalculationService` | `stockcare.models.priority.mode` |
| MILP route optimizer | `RouteOptimizationService` (batch 2) | `stockcare.models.route-optimization.mode` |
| GPS / tracking provider | tracking simulator (batch 2) | — |
| Notification providers | notification adapters (batch 2) | — |

Each model boundary supports: version info, request/response logging without sensitive data
(`model_execution_log`), status + duration capture, correlation IDs, and a mock↔external switch.

## Data model (batch 1 tables)

`depot`, `pharmacy`, `depot_pharmacy`, `app_user`, `medication`, `inventory_item`,
`stock_adjustment`, `prediction_result`, `pharmacy_request`, `pharmacy_request_item`,
`priority_result`, `model_execution_log`, `simulated_time_config`.

All tables carry: `id` (UUID), `created_at`, `updated_at`, `created_by`, `updated_by`, `version`
(optimistic locking). Foreign keys and useful indexes are defined in `V1__init_schema.sql`.

Batch-2 tables (`vehicle`, `driver`, `delivery`, `delivery_item`, `delivery_route`, `route_stop`,
`tracking_position`, `delivery_event`, `notification`, `audit_log`) will be added as `V2/V3`
migrations together with their entities.

## Request lifecycle

```
DRAFT → SUBMITTED → RECEIVED → PRIORITIZED → PLANNED → PREPARING → IN_DELIVERY → DELIVERED
                 ↘ CANCELLED (while DRAFT/SUBMITTED/RECEIVED)     ↘ REJECTED
```

Pharmacy actions: create draft (manual or from a prediction), edit draft, submit, cancel, track.
Depot actions: filter/sort, change status, internal note, calculate priority, approve for planning.

## Security model

- Stateless JWT (HS256), roles `PHARMACY` / `DEPOT` / `ADMIN`.
- Passwords hashed with BCrypt; hashes never serialized.
- `PharmacyAccessService` enforces entity-level ownership: a pharmacy user is restricted to its own
  pharmacy; a depot user to pharmacies associated with its depot; admin is unrestricted.
- Centralized exception handling returns safe errors (no stack traces, no secrets) with a correlation
  id for tracing.

## Assumptions & known limitations

- One depot in the MVP; every pharmacy is associated with it via `depot_pharmacy` (modelled as a link
  entity so it can become many-to-many later).
- Inventory is managed manually (no ERP yet); the ERP adapter boundary is prepared for batch 2.
- Prediction and priority outputs are transparent, rule-based mocks — explicitly **not** trained
  models — and every result is labelled `simulated`.
- Simulated time is a single global setting persisted for the test environment.
