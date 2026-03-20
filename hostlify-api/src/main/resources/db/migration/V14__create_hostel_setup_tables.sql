CREATE TABLE hostel_amenities (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    hostel_id BIGINT NOT NULL,
    amenity_key VARCHAR(50) NOT NULL,
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_amenity_hostel FOREIGN KEY (hostel_id) REFERENCES hostel_hostels(id)
);

CREATE TABLE hostel_pricing (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    hostel_id BIGINT NOT NULL,
    room_type VARCHAR(30) NOT NULL,
    base_rent DECIMAL(12,2) NOT NULL,
    deposit_months INT NOT NULL DEFAULT 2,
    late_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_hostel FOREIGN KEY (hostel_id) REFERENCES hostel_hostels(id),
    CONSTRAINT uq_pricing_hostel_room_type UNIQUE (hostel_id, room_type)
);

CREATE TABLE hostel_booking_settings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    hostel_id BIGINT NOT NULL UNIQUE,
    self_booking_enabled TINYINT(1) NOT NULL DEFAULT 1,
    requires_admin_approval TINYINT(1) NOT NULL DEFAULT 1,
    auto_expire_days INT NOT NULL DEFAULT 3,
    max_pending_per_bed INT NOT NULL DEFAULT 1,
    geolocation_radius_km DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_settings_hostel FOREIGN KEY (hostel_id) REFERENCES hostel_hostels(id)
);
