ALTER TABLE hostel_hostels
    ADD COLUMN google_place_id VARCHAR(100) NULL,
    ADD COLUMN landmark VARCHAR(255) NULL,
    ADD COLUMN about TEXT NULL,
    ADD COLUMN contact_phone VARCHAR(20) NULL;
