# StockCare — Example API requests

Base URL: `http://localhost:8080`. Authenticated endpoints require `Authorization: Bearer <TOKEN>`.

## 1. Login (pharmacy)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ph.tunis@stockcare.tn","password":"Password123!"}' | jq -r .token)
```

## 2. Who am I

```bash
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
```

## 3. My pharmacy inventory

```bash
curl -s http://localhost:8080/api/inventory/me -H "Authorization: Bearer $TOKEN"
```

## 4. Add a medication to inventory

```bash
# find a medication id
MED=$(curl -s "http://localhost:8080/api/medications?query=Paracetamol" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.content[0].id')
PHARM=$(curl -s http://localhost:8080/api/pharmacies/me -H "Authorization: Bearer $TOKEN" | jq -r .id)

curl -s -X POST "http://localhost:8080/api/inventory/pharmacies/$PHARM/items" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"medicationId\":\"$MED\",\"currentQuantity\":10,\"minimumQuantity\":40,\"averageDailyConsumption\":8,\"reorderThreshold\":45}"
```

## 5. Record a stock adjustment (a sale)

```bash
ITEM=<inventoryItemId>
curl -s -X POST "http://localhost:8080/api/inventory/pharmacies/$PHARM/items/$ITEM/adjustments" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"delta":-5,"reason":"SALE","note":"Counter sale"}'
```

## 6. Run the (mock) shortage prediction

```bash
curl -s -X POST http://localhost:8080/api/predictions/me/run -H "Authorization: Bearer $TOKEN"
```

## 7. Draft a request from a prediction, then submit

```bash
PRED=$(curl -s http://localhost:8080/api/predictions/me -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')

REQ=$(curl -s -X POST http://localhost:8080/api/requests/from-prediction \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"predictionId\":\"$PRED\",\"urgency\":\"HIGH\",\"affectedPatients\":25}" | jq -r .id)

curl -s -X POST "http://localhost:8080/api/requests/$REQ/submit" -H "Authorization: Bearer $TOKEN"
```

## 8. Depot: list incoming requests, prioritize, approve

```bash
DTOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"depot@stockcare.tn","password":"Password123!"}' | jq -r .token)

# list SUBMITTED requests
curl -s "http://localhost:8080/api/depot/requests?status=SUBMITTED" -H "Authorization: Bearer $DTOKEN"

# calculate the mock priority coefficient
curl -s -X POST "http://localhost:8080/api/depot/requests/$REQ/prioritize" -H "Authorization: Bearer $DTOKEN"

# approve for route planning
curl -s -X POST "http://localhost:8080/api/depot/requests/$REQ/approve" -H "Authorization: Bearer $DTOKEN"
```

## 9. Simulated time

```bash
curl -s -X POST http://localhost:8080/api/time/simulate -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"dateTime":"2026-01-15T09:00:00Z","frozen":false}'
curl -s -X POST http://localhost:8080/api/time/advance  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"days":10,"hours":0}'
curl -s http://localhost:8080/api/time -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://localhost:8080/api/time/reset -H "Authorization: Bearer $TOKEN"
```

## 10. Data-isolation check (should fail with 403)

```bash
# ph.tunis trying to read another pharmacy's inventory
OTHER=<some other pharmacy id>
curl -s -o /dev/null -w '%{http_code}\n' \
  "http://localhost:8080/api/inventory/pharmacies/$OTHER" -H "Authorization: Bearer $TOKEN"
# → 403
```
