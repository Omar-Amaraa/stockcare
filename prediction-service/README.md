# StockCare Prediction Service (Layer 1)

Demand-forecasting engine for pharmacy shortage prediction. A gradient-boosted model
(LightGBM / Poisson, with a pure-NumPy GBT fallback) forecasts H-day demand per
(pharmacy, medicine); `shortage/gap.py` turns the forecast into restock needs
`G = max(0, D + S - X)`.

## Offline pipeline
```
pip install -r requirements.txt
python run.py          # generate -> features -> train -> evaluate -> emit queries
pytest -q              # pipeline unit tests
```
Artifacts (trained model, panel, metrics, sample queries) live in `artifacts/`.

## Serving API (used by the Spring backend in `external` mode)
```
uvicorn app:app --host 0.0.0.0 --port 8000
```
- `GET /health` — liveness + model backend/version
- `GET /info` — horizon, thresholds, validation metrics
- `POST /predict` — batch shortage prediction for live inventory snapshots

`POST /predict` body:
```json
{
  "decisionDate": "2026-01-15",
  "horizonDays": 14,
  "items": [
    {"medicationId": "<uuid>", "region": "Tunis", "category": "Antidiabetic",
     "coldChain": true, "currentStock": 12, "averageDailyConsumption": 4.0}
  ]
}
```
Response `predictions[]`: `medicationId, currentStock, predictedShortageDate,
estimatedRemainingDays, predictedMissingQuantity, demandForecast, urgency, reason,
modelVersion`.

> Live lag/rolling features are approximated from `averageDailyConsumption` until real
> POS history is streamed; region, category, cold-chain and calendar/epidemiological
> signals are real. The `/predict` contract does not change when richer history arrives.
