ALTER TABLE hostel_hostels
    ADD COLUMN google_place_id VARCHAR(100) NULL AFTER longitude,
    ADD COLUMN landmark VARCHAR(255) NULL AFTER google_place_id,
    ADD COLUMN about TEXT NULL AFTER landmark,
    ADD COLUMN contact_phone VARCHAR(20) NULL AFTER about;
