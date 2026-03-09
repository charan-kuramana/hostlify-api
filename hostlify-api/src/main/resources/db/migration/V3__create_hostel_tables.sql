CREATE TABLE hostel_owners (
  id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  auth_user_id      BIGINT       NOT NULL,
  business_name     VARCHAR(200) NOT NULL,
  owner_name        VARCHAR(150) NOT NULL,
  mobile            VARCHAR(15)  NOT NULL,
  email             VARCHAR(150) NOT NULL,
  pan_number        VARCHAR(20),
  gst_number        VARCHAR(20),
  status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  subscription_plan VARCHAR(30)  NOT NULL DEFAULT 'FREE',
  plan_expires_at   DATETIME,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted        TINYINT(1)   NOT NULL DEFAULT 0,
  deleted_at        DATETIME,
  CONSTRAINT fk_ho_auth_user  FOREIGN KEY (auth_user_id) REFERENCES auth_users (id),
  INDEX idx_ho_auth_user_id   (auth_user_id),
  INDEX idx_ho_status         (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hostel_hostels (
  id            BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id      BIGINT         NOT NULL,
  name          VARCHAR(200)   NOT NULL,
  hostel_type   VARCHAR(20)    NOT NULL,
  city          VARCHAR(100)   NOT NULL,
  pincode       VARCHAR(10)    NOT NULL,
  address       TEXT,
  latitude      DECIMAL(10, 7),
  longitude     DECIMAL(10, 7),
  total_floors  INT            NOT NULL DEFAULT 0,
  total_rooms   INT            NOT NULL DEFAULT 0,
  total_beds    INT            NOT NULL DEFAULT 0,
  default_rent  DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted    TINYINT(1)     NOT NULL DEFAULT 0,
  deleted_at    DATETIME       DEFAULT NULL,
  CONSTRAINT fk_hh_owner  FOREIGN KEY (owner_id) REFERENCES hostel_owners (id),
  INDEX idx_hh_owner_id   (owner_id),
  INDEX idx_hh_city       (city),
  INDEX idx_hh_pincode    (pincode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hostel_floors (
  id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hostel_id    BIGINT      NOT NULL,
  floor_name   VARCHAR(50) NOT NULL,
  floor_number INT         NOT NULL,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted   TINYINT(1)  NOT NULL DEFAULT 0,
  deleted_at   DATETIME    DEFAULT NULL,
  CONSTRAINT fk_hf_hostel  FOREIGN KEY (hostel_id) REFERENCES hostel_hostels (id),
  INDEX idx_hf_hostel_id   (hostel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hostel_rooms (
  id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
  floor_id          BIGINT         NOT NULL,
  hostel_id         BIGINT         NOT NULL,
  room_name         VARCHAR(50)    NOT NULL,
  room_type         VARCHAR(30)    NOT NULL,
  capacity          INT            NOT NULL DEFAULT 1,
  current_occupancy INT            NOT NULL DEFAULT 0,
  rent_amount       DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted        TINYINT(1)     NOT NULL DEFAULT 0,
  deleted_at        DATETIME       DEFAULT NULL,
  CONSTRAINT fk_hr_floor   FOREIGN KEY (floor_id)  REFERENCES hostel_floors (id),
  CONSTRAINT fk_hr_hostel  FOREIGN KEY (hostel_id) REFERENCES hostel_hostels (id),
  INDEX idx_hr_floor_id    (floor_id),
  INDEX idx_hr_hostel_id   (hostel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hostel_beds (
  id                        BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  room_id                   BIGINT     NOT NULL,
  hostel_id                 BIGINT     NOT NULL,
  bed_name                  VARCHAR(30) NOT NULL,
  status                    VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  reserved_for_hosteller_id BIGINT,
  reserved_at               DATETIME,
  occupied_by_booking_id    BIGINT,
  created_at                DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted                TINYINT(1) NOT NULL DEFAULT 0,
  deleted_at                DATETIME   DEFAULT NULL,
  CONSTRAINT fk_hb_room    FOREIGN KEY (room_id)   REFERENCES hostel_rooms (id),
  CONSTRAINT fk_hb_hostel  FOREIGN KEY (hostel_id) REFERENCES hostel_hostels (id),
  INDEX idx_hb_room_id     (room_id),
  INDEX idx_hb_hostel_id   (hostel_id),
  INDEX idx_hb_status      (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hostel_staff_assignments (
  id            BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  staff_user_id BIGINT     NOT NULL,
  hostel_id     BIGINT     NOT NULL,
  owner_id      BIGINT     NOT NULL,
  is_active     TINYINT(1) NOT NULL DEFAULT 1,
  assigned_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  assigned_by   BIGINT     NOT NULL,
  created_at    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted    TINYINT(1) NOT NULL DEFAULT 0,
  deleted_at    DATETIME   DEFAULT NULL,
  CONSTRAINT fk_hsa_staff   FOREIGN KEY (staff_user_id) REFERENCES auth_users (id),
  CONSTRAINT fk_hsa_hostel  FOREIGN KEY (hostel_id)     REFERENCES hostel_hostels (id),
  CONSTRAINT fk_hsa_owner   FOREIGN KEY (owner_id)      REFERENCES hostel_owners (id),
  INDEX idx_hsa_staff_user  (staff_user_id),
  INDEX idx_hsa_hostel_id   (hostel_id),
  INDEX idx_hsa_owner_id    (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
