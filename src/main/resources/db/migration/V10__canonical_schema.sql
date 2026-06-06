CREATE SCHEMA canonical;

CREATE TABLE canonical.customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_refs   JSONB,
    name            VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(50),
    segment         VARCHAR(50),
    region          VARCHAR(100),
    country         VARCHAR(100),
    city            VARCHAR(100),
    primary_source  UUID REFERENCES data_sources(id),
    quality_score   NUMERIC(5,4),
    has_conflicts   BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE canonical.products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_refs   JSONB,
    sku             VARCHAR(100),
    name            VARCHAR(255),
    category        VARCHAR(100),
    sub_category    VARCHAR(100),
    unit_price      NUMERIC(12,4),
    currency        CHAR(3),
    active          BOOLEAN NOT NULL DEFAULT true,
    primary_source  UUID REFERENCES data_sources(id),
    quality_score   NUMERIC(5,4),
    has_conflicts   BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE canonical.salespersons (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_refs   JSONB,
    name            VARCHAR(255),
    email           VARCHAR(255),
    team            VARCHAR(100),
    territory       VARCHAR(100),
    region          VARCHAR(100),
    active          BOOLEAN NOT NULL DEFAULT true,
    primary_source  UUID REFERENCES data_sources(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE canonical.regions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    country         VARCHAR(100),
    zone            VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE canonical.orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_refs   JSONB,
    order_date      DATE,
    ship_date       DATE,
    customer_id     UUID REFERENCES canonical.customers(id),
    salesperson_id  UUID REFERENCES canonical.salespersons(id),
    region_id       UUID REFERENCES canonical.regions(id),
    ship_mode       VARCHAR(50),
    shipping_cost   NUMERIC(12,4),
    total_amount    NUMERIC(12,4),
    currency        CHAR(3),
    source_id       UUID REFERENCES data_sources(id),
    job_id          UUID REFERENCES ingestion_jobs(id),
    quality_score   NUMERIC(5,4),
    has_conflicts   BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE canonical.order_line_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES canonical.orders(id),
    product_id      UUID REFERENCES canonical.products(id),
    quantity        INTEGER NOT NULL,
    unit_price      NUMERIC(12,4) NOT NULL,
    discount        NUMERIC(5,4) NOT NULL DEFAULT 0,
    line_total      NUMERIC(12,4) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
