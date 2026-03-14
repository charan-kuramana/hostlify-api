CREATE TABLE service_cleaning_requests (
  id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hosteller_id   BIGINT       NOT NULL,
  hostel_id      BIGINT       NOT NULL,
  room_number    VARCHAR(20)  DEFAULT NULL,
  request_date   DATE         NOT NULL,
  status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  notes          VARCHAR(500) DEFAULT NULL,
  completed_by   BIGINT       DEFAULT NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted     TINYINT(1)   NOT NULL DEFAULT 0,
  deleted_at     DATETIME     DEFAULT NULL,
  CONSTRAINT fk_scr_hosteller FOREIGN KEY (hosteller_id) REFERENCES hosteller_profiles (id),
  CONSTRAINT fk_scr_hostel    FOREIGN KEY (hostel_id)    REFERENCES hostel_hostels (id),
  INDEX idx_scr_hosteller_id  (hosteller_id),
  INDEX idx_scr_hostel_id     (hostel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
