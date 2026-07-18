-- Automated-workflow state: per-pharmacy prediction run (idempotency + async status).

CREATE TABLE prediction_run (
    id             UUID PRIMARY KEY,
    pharmacy_id    UUID NOT NULL UNIQUE REFERENCES pharmacy(id),
    status         VARCHAR(16) NOT NULL,
    input_hash     VARCHAR(64),
    correlation_id VARCHAR(64),
    model_version  VARCHAR(64),
    retry_count    INTEGER NOT NULL,
    shortage_count INTEGER NOT NULL,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    error_message  VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    version        BIGINT NOT NULL
);
