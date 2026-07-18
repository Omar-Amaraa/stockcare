FROM python:3.11-slim
WORKDIR /app

# LightGBM needs libgomp; curl for the container healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=15 \
  CMD curl -fsS http://localhost:8000/health | grep -q '"status":"UP"' || exit 1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
