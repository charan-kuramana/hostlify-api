-- Owner's choice: electricity, water and Wi-Fi are included in rent (default) or billed separately.
ALTER TABLE hostel_payment_settings ADD COLUMN utilities_included BOOLEAN NOT NULL DEFAULT TRUE;

-- Short stays (1 day / 1 week / 15 days …): owner switches them on and sets day / week / 15-day rates per room type.
ALTER TABLE hostel_booking_settings
    ADD COLUMN short_stay_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN short_stay_min_days INT     NOT NULL DEFAULT 1,
    ADD COLUMN short_stay_max_days INT     NOT NULL DEFAULT 30;
ALTER TABLE hostel_pricing
    ADD COLUMN daily_rent     NUMERIC(12,2),
    ADD COLUMN weekly_rent    NUMERIC(12,2),
    ADD COLUMN fortnight_rent NUMERIC(12,2);
ALTER TABLE booking_requests
    ADD COLUMN stay_type      VARCHAR(10)   NOT NULL DEFAULT 'MONTHLY', -- MONTHLY | SHORT
    ADD COLUMN check_out_date DATE,
    ADD COLUMN quoted_amount  NUMERIC(12,2);
-- rent_frequency already exists ('MONTHLY'); short stays use 'SHORT' and a planned check-out date.
ALTER TABLE booking_checkins ADD COLUMN expected_checkout_date DATE;

-- Resident asks to check out; staff inspect the room (bed, blanket, keys …) and approve.
CREATE TABLE checkout_requests (
    id              BIGSERIAL PRIMARY KEY,
    hostel_id       BIGINT      NOT NULL,
    hosteller_id    BIGINT      NOT NULL,
    booking_id      BIGINT      NOT NULL,
    requested_date  DATE        NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(12) NOT NULL DEFAULT 'REQUESTED', -- REQUESTED | APPROVED | REJECTED | CANCELLED
    items_json      TEXT,                                      -- staff inspection: [{label, status: OK|DAMAGED|MISSING, note}]
    staff_note      VARCHAR(500),
    decided_by      BIGINT,
    decided_at      TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cor_hostel_status ON checkout_requests (hostel_id, status);
CREATE INDEX idx_cor_hosteller ON checkout_requests (hosteller_id);

-- Optional push "How was lunch?" after each meal (owner toggle; residents always see the rating card).
ALTER TABLE mess_settings ADD COLUMN ask_meal_feedback BOOLEAN NOT NULL DEFAULT FALSE;
-- Avoid nudging the same meal twice.
CREATE TABLE meal_feedback_nudges (
    hostel_id  BIGINT      NOT NULL,
    meal_date  DATE        NOT NULL,
    meal_type  VARCHAR(10) NOT NULL,
    PRIMARY KEY (hostel_id, meal_date, meal_type)
);
