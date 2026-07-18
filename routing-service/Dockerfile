# Build context is the REPOSITORY ROOT, not this folder: the image needs both the
# service and the MILP CARE sources it wraps.
#   docker build -f routing-service/Dockerfile -t stockcare-routing .
FROM python:3.11-slim
WORKDIR /app

# curl for the container healthcheck; PuLP ships its own CBC binary.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY routing-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY routing-service/ ./
# The solver sources, imported unmodified (see milp_path.py).
COPY milp_care/milp_care_delivery/src/ ./milp_src/

EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=10 \
  CMD curl -fsS http://localhost:8000/health | grep -q '"status":"UP"' || exit 1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
