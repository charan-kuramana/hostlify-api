-- Section 9: what residents struggle with most: deposit refunds, hidden charges / notice fights, safety, and
-- smaller everyday issues (house rules, late entry, cleaning proof, lost items, falling ill).

-- One row per hostel: the "fine print" shown before booking, house rules and safety facilities.
CREATE TABLE hostel_policies (
    hostel_id            BIGINT PRIMARY KEY REFERENCES hostel_hostels(id) ON DELETE CASCADE,
    lock_in_months       INT           NOT NULL DEFAULT 0,
    early_exit_fee       NUMERIC(12,2) NOT NULL DEFAULT 0,
    admission_fee        NUMERIC(12,2) NOT NULL DEFAULT 0,   -- one-time, non-refundable
    deposit_refund_days  INT           NOT NULL DEFAULT 15,  -- refund deposit within N days of check-out
    other_charges        VARCHAR(1000),                      -- anything else, in the owner's words
    curfew_time          VARCHAR(5),                         -- HH:mm; NULL = no curfew
    guest_policy         VARCHAR(30)   NOT NULL DEFAULT 'DAYTIME', -- NOT_ALLOWED | DAYTIME | OVERNIGHT_WITH_APPROVAL
    house_rules          TEXT,                               -- one rule per line
    late_entry_requests  BOOLEAN       NOT NULL DEFAULT TRUE,
    room_cleaning_days   VARCHAR(40),                        -- e.g. MON,THU ; NULL = daily
    safety_features      VARCHAR(400),                       -- comma list: CCTV,SECURITY_GUARD,…
    updated_by           BIGINT,
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- The cost of stay and rules as shown when the resident booked, so they can't change afterwards.
ALTER TABLE booking_requests ADD COLUMN terms_json TEXT, ADD COLUMN terms_accepted_at TIMESTAMP;
ALTER TABLE booking_checkins ADD COLUMN terms_json TEXT,
    ADD COLUMN deposit_alerts SMALLINT NOT NULL DEFAULT 0; -- 1 = staff reminded before the refund deadline, 2 = overdue alerts sent

-- Notice estimate shown to the resident when they asked to check out (kept for staff and the settlement).
ALTER TABLE checkout_requests ADD COLUMN estimate_json TEXT;

-- Deposit: a resident can dispute a settlement; staff reply and may waive part of the deductions.
ALTER TABLE deposit_settlements
    ADD COLUMN dispute_status      VARCHAR(10),  -- OPEN | RESOLVED
    ADD COLUMN dispute_reason      VARCHAR(1000),
    ADD COLUMN disputed_at         TIMESTAMP,
    ADD COLUMN dispute_response    VARCHAR(1000),
    ADD COLUMN dispute_resolved_at TIMESTAMP,
    ADD COLUMN waived_amount       NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Health details staff may need in an emergency.
ALTER TABLE hosteller_profiles ADD COLUMN blood_group VARCHAR(5), ADD COLUMN medical_notes VARCHAR(300);

-- SOS from a resident.
CREATE TABLE sos_alerts (
    id               BIGSERIAL PRIMARY KEY,
    hostel_id        BIGINT       NOT NULL,
    hosteller_id     BIGINT       NOT NULL,
    latitude         NUMERIC(9,6),
    longitude        NUMERIC(9,6),
    accuracy_m       INT,
    message          VARCHAR(300),
    status           VARCHAR(12)  NOT NULL DEFAULT 'OPEN', -- OPEN | ACKNOWLEDGED | RESOLVED | CANCELLED
    acknowledged_by  BIGINT,
    acknowledged_at  TIMESTAMP,
    resolved_by      BIGINT,
    resolved_at      TIMESTAMP,
    resolution_note  VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sos_hostel_status ON sos_alerts (hostel_id, status);
CREATE INDEX idx_sos_hosteller ON sos_alerts (hosteller_id);

-- "I'm not feeling well": staff check on the resident.
CREATE TABLE wellness_flags (
    id            BIGSERIAL PRIMARY KEY,
    hostel_id     BIGINT       NOT NULL,
    hosteller_id  BIGINT       NOT NULL,
    note          VARCHAR(300),
    needs         VARCHAR(100),  -- comma list: FOOD_IN_ROOM,DOCTOR,CHECK_ON_ME,MEDICINE
    status        VARCHAR(10)  NOT NULL DEFAULT 'OPEN', -- OPEN | CLEARED
    checked_by    BIGINT,
    checked_at    TIMESTAMP,
    staff_note    VARCHAR(300),
    cleared_at    TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_wellness_hostel ON wellness_flags (hostel_id, status);

-- Coming back after curfew.
CREATE TABLE late_entry_requests (
    id             BIGSERIAL PRIMARY KEY,
    hostel_id      BIGINT       NOT NULL,
    hosteller_id   BIGINT       NOT NULL,
    entry_date     DATE         NOT NULL,
    expected_time  VARCHAR(5)   NOT NULL,
    reason         VARCHAR(300) NOT NULL,
    status         VARCHAR(10)  NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED | CANCELLED
    reviewed_by    BIGINT,
    reviewed_at    TIMESTAMP,
    staff_note     VARCHAR(300),
    arrived_at     TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_late_entry_hostel_date ON late_entry_requests (hostel_id, entry_date);
CREATE INDEX idx_late_entry_hosteller ON late_entry_requests (hosteller_id);

-- Housekeeping proof: staff record each room / area cleaning, with an optional photo.
CREATE TABLE cleaning_logs (
    id          BIGSERIAL PRIMARY KEY,
    hostel_id   BIGINT       NOT NULL,
    room_id     BIGINT,
    area        VARCHAR(80),
    cleaned_on  DATE         NOT NULL,
    cleaned_by  BIGINT       NOT NULL,
    photo_path  VARCHAR(500),
    note        VARCHAR(300),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cleaning_logs_hostel_date ON cleaning_logs (hostel_id, cleaned_on);
CREATE INDEX idx_cleaning_logs_room ON cleaning_logs (room_id, cleaned_on);
