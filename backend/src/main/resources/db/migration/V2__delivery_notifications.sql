-- StockCare batch-2 schema: fleet, deliveries, tracking, notifications, audit.

CREATE TABLE vehicle (
    id             UUID PRIMARY KEY,
    depot_id       UUID NOT NULL REFERENCES depot(id),
    code           VARCHAR(32) NOT NULL UNIQUE,
    plate_number   VARCHAR(32),
    capacity_units INTEGER NOT NULL,
    refrigerated   BOOLEAN NOT NULL,
    active         BOOLEAN NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    version        BIGINT NOT NULL
);
CREATE INDEX idx_vehicle_depot ON vehicle(depot_id);

CREATE TABLE driver (
    id         UUID PRIMARY KEY,
    depot_id   UUID NOT NULL REFERENCES depot(id),
    full_name  VARCHAR(255) NOT NULL,
    phone      VARCHAR(32),
    active     BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version    BIGINT NOT NULL
);
CREATE INDEX idx_driver_depot ON driver(depot_id);

CREATE TABLE delivery (
    id                     UUID PRIMARY KEY,
    depot_id               UUID NOT NULL REFERENCES depot(id),
    vehicle_id             UUID REFERENCES vehicle(id),
    driver_id              UUID REFERENCES driver(id),
    status                 VARCHAR(20) NOT NULL,
    reference              VARCHAR(32),
    simulated              BOOLEAN NOT NULL,
    optimizer_version      VARCHAR(64),
    total_distance_km      DOUBLE PRECISION,
    total_duration_minutes DOUBLE PRECISION,
    current_latitude       DOUBLE PRECISION,
    current_longitude      DOUBLE PRECISION,
    current_stop_index     INTEGER,
    progress               DOUBLE PRECISION NOT NULL,
    eta_minutes            DOUBLE PRECISION,
    planned_at             TIMESTAMPTZ,
    started_at             TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    created_by             VARCHAR(255),
    updated_by             VARCHAR(255),
    version                BIGINT NOT NULL
);
CREATE INDEX idx_delivery_depot ON delivery(depot_id);
CREATE INDEX idx_delivery_status ON delivery(status);

CREATE TABLE route_stop (
    id                       UUID PRIMARY KEY,
    delivery_id              UUID NOT NULL REFERENCES delivery(id) ON DELETE CASCADE,
    sequence                 INTEGER NOT NULL,
    pharmacy_id              UUID NOT NULL REFERENCES pharmacy(id),
    request_id               UUID,
    latitude                 DOUBLE PRECISION NOT NULL,
    longitude                DOUBLE PRECISION NOT NULL,
    status                   VARCHAR(16) NOT NULL,
    estimated_arrival_minute DOUBLE PRECISION,
    distance_from_prev_km    DOUBLE PRECISION,
    arrived_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    version                  BIGINT NOT NULL
);
CREATE INDEX idx_route_stop_delivery ON route_stop(delivery_id);

CREATE TABLE delivery_item (
    id            UUID PRIMARY KEY,
    delivery_id   UUID NOT NULL REFERENCES delivery(id) ON DELETE CASCADE,
    pharmacy_id   UUID NOT NULL REFERENCES pharmacy(id),
    request_id    UUID,
    medication_id UUID NOT NULL REFERENCES medication(id),
    quantity      INTEGER NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    version       BIGINT NOT NULL
);
CREATE INDEX idx_delivery_item_delivery ON delivery_item(delivery_id);

CREATE TABLE tracking_position (
    id          UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES delivery(id) ON DELETE CASCADE,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    stop_index  INTEGER,
    eta_minutes DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT NOT NULL
);
CREATE INDEX idx_tracking_delivery ON tracking_position(delivery_id);

CREATE TABLE delivery_event (
    id          UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES delivery(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,
    message     VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT NOT NULL
);
CREATE INDEX idx_delivery_event_delivery ON delivery_event(delivery_id);

CREATE TABLE notification (
    id                UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    type              VARCHAR(32) NOT NULL,
    title             VARCHAR(255) NOT NULL,
    message           VARCHAR(1000),
    read_flag         BOOLEAN NOT NULL,
    reference_id      UUID,
    reference_type    VARCHAR(32),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    version           BIGINT NOT NULL
);
CREATE INDEX idx_notification_recipient ON notification(recipient_user_id);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    actor       VARCHAR(255),
    action      VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64),
    entity_id   UUID,
    detail      VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT NOT NULL
);
CREATE INDEX idx_audit_occurred ON audit_log(occurred_at);
