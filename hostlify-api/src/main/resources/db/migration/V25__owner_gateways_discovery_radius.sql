-- Owner-set discovery radius: a hostel shows up in "Nearby" only for people within this distance.
ALTER TABLE hostel_hostels ADD COLUMN discovery_radius_km INT NOT NULL DEFAULT 10;

-- Each owner connects their own payment gateway accounts; residents' online payments go straight to
-- the owner's account. hostel_id NULL = used for all of the owner's hostels; a row for a specific
-- hostel overrides the owner-wide one for that provider.
CREATE TABLE owner_payment_gateways (
    id                 BIGSERIAL PRIMARY KEY,
    owner_id           BIGINT       NOT NULL REFERENCES hostel_owners(id) ON DELETE CASCADE,
    hostel_id          BIGINT       REFERENCES hostel_hostels(id) ON DELETE CASCADE,
    provider           VARCHAR(20)  NOT NULL,             -- RAZORPAY | STRIPE | PAYPAL
    mode               VARCHAR(10)  NOT NULL DEFAULT 'TEST', -- TEST | LIVE
    label              VARCHAR(80),
    public_key         VARCHAR(255),                      -- Razorpay key id / Stripe publishable key / PayPal client id
    secret_enc         TEXT         NOT NULL,             -- AES-GCM encrypted secret
    webhook_secret_enc TEXT,
    webhook_token      VARCHAR(40)  NOT NULL UNIQUE,      -- unguessable part of this gateway's webhook URL
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    last_tested_at     TIMESTAMP,
    last_test_ok       BOOLEAN,
    last_test_message  VARCHAR(300),
    created_by         BIGINT,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_opg_owner_scope_provider ON owner_payment_gateways (owner_id, COALESCE(hostel_id, 0), provider);

-- One checkout attempt through an owner's gateway.
CREATE TABLE payment_gateway_orders (
    id                  BIGSERIAL PRIMARY KEY,
    gateway_id          BIGINT       NOT NULL REFERENCES owner_payment_gateways(id) ON DELETE CASCADE,
    provider            VARCHAR(20)  NOT NULL,
    hostel_id           BIGINT       NOT NULL,
    hosteller_id        BIGINT       NOT NULL,
    due_id              BIGINT       NOT NULL,
    amount_paise        BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'INR',
    provider_order_id   VARCHAR(120),                     -- Razorpay order / Stripe checkout session / PayPal order
    provider_payment_id VARCHAR(120),
    status              VARCHAR(20)  NOT NULL DEFAULT 'CREATED', -- CREATED | PAID | FAILED | EXPIRED
    payment_record_id   BIGINT,
    failure_reason      VARCHAR(300),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    paid_at             TIMESTAMP
);
CREATE INDEX idx_pgo_provider_order ON payment_gateway_orders (provider_order_id);
CREATE INDEX idx_pgo_status ON payment_gateway_orders (status, created_at);
CREATE INDEX idx_pgo_hosteller ON payment_gateway_orders (hosteller_id);
