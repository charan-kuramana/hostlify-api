CREATE TABLE notification_log (
  id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  recipient_type      VARCHAR(20)  NOT NULL,
  recipient_id        BIGINT       NOT NULL,
  channel             VARCHAR(10)  NOT NULL,
  template_name       VARCHAR(80)  NOT NULL,
  subject             VARCHAR(300),
  body_preview        VARCHAR(500),
  to_email            VARCHAR(150),
  status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  provider_message_id VARCHAR(200),
  error_message       TEXT,
  retry_count         INT          NOT NULL DEFAULT 0,
  sent_at             DATETIME,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted          TINYINT(1)   NOT NULL DEFAULT 0,
  deleted_at          DATETIME     DEFAULT NULL,
  INDEX idx_nl_recipient  (recipient_type, recipient_id),
  INDEX idx_nl_status     (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE menu_weekly (
  id         BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hostel_id  BIGINT     NOT NULL,
  week_start DATE       NOT NULL,
  created_by BIGINT     NOT NULL,
  created_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  deleted_at DATETIME   DEFAULT NULL,
  CONSTRAINT fk_mw_hostel  FOREIGN KEY (hostel_id) REFERENCES hostel_hostels (id),
  INDEX idx_mw_hostel_id   (hostel_id),
  INDEX idx_mw_week_start  (week_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE menu_items (
  id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  menu_id     BIGINT      NOT NULL,
  day_of_week VARCHAR(10) NOT NULL,
  meal_type   VARCHAR(15) NOT NULL,
  items       TEXT        NOT NULL,
  timing      VARCHAR(50),
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted  TINYINT(1)  NOT NULL DEFAULT 0,
  deleted_at  DATETIME    DEFAULT NULL,
  CONSTRAINT fk_mi_menu  FOREIGN KEY (menu_id) REFERENCES menu_weekly (id),
  INDEX idx_mi_menu_id   (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE announcements (
  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hostel_id  BIGINT       NOT NULL,
  title      VARCHAR(200) NOT NULL,
  body       TEXT,
  priority   VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
  valid_from DATE         NOT NULL,
  valid_till DATE,
  is_emailed TINYINT(1)   NOT NULL DEFAULT 0,
  created_by BIGINT       NOT NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
  deleted_at DATETIME     DEFAULT NULL,
  CONSTRAINT fk_ann_hostel  FOREIGN KEY (hostel_id) REFERENCES hostel_hostels (id),
  INDEX idx_ann_hostel_id   (hostel_id),
  INDEX idx_ann_valid_from  (valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE referral_codes (
  id                    BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hosteller_id          BIGINT         NOT NULL,
  code                  VARCHAR(20)    NOT NULL,
  total_referrals       INT            NOT NULL DEFAULT 0,
  total_discount_earned DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  created_at            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted            TINYINT(1)     NOT NULL DEFAULT 0,
  deleted_at            DATETIME       DEFAULT NULL,
  UNIQUE KEY uk_referral_code        (code),
  CONSTRAINT fk_rcode_hosteller  FOREIGN KEY (hosteller_id) REFERENCES hosteller_profiles (id),
  INDEX idx_rc_hosteller_id          (hosteller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE referral_records (
  id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
  referrer_id     BIGINT         NOT NULL,
  referred_id     BIGINT         NOT NULL,
  status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
  discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted      TINYINT(1)     NOT NULL DEFAULT 0,
  deleted_at      DATETIME       DEFAULT NULL,
  CONSTRAINT fk_rr_referrer  FOREIGN KEY (referrer_id) REFERENCES hosteller_profiles (id),
  CONSTRAINT fk_rr_referred  FOREIGN KEY (referred_id) REFERENCES hosteller_profiles (id),
  INDEX idx_rr_referrer_id   (referrer_id),
  INDEX idx_rr_referred_id   (referred_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE leave_requests (
  id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hosteller_id BIGINT      NOT NULL,
  booking_id   BIGINT      NOT NULL,
  leave_type   VARCHAR(20) NOT NULL,
  from_date    DATE        NOT NULL,
  to_date      DATE        NOT NULL,
  reason       TEXT,
  status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewed_by  BIGINT,
  reviewed_at  DATETIME,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted   TINYINT(1)  NOT NULL DEFAULT 0,
  deleted_at   DATETIME    DEFAULT NULL,
  CONSTRAINT fk_lr_hosteller  FOREIGN KEY (hosteller_id) REFERENCES hosteller_profiles (id),
  CONSTRAINT fk_lr_booking    FOREIGN KEY (booking_id)   REFERENCES booking_checkins (id),
  INDEX idx_lr_hosteller_id   (hosteller_id),
  INDEX idx_lr_booking_id     (booking_id),
  INDEX idx_lr_status         (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE service_parking (
  id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hosteller_id   BIGINT      NOT NULL,
  hostel_id      BIGINT      NOT NULL,
  vehicle_number VARCHAR(20) NOT NULL,
  vehicle_type   VARCHAR(20) NOT NULL,
  slot_number    VARCHAR(20),
  status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  registered_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted     TINYINT(1)  NOT NULL DEFAULT 0,
  deleted_at     DATETIME    DEFAULT NULL,
  CONSTRAINT fk_sp_hosteller  FOREIGN KEY (hosteller_id) REFERENCES hosteller_profiles (id),
  CONSTRAINT fk_sp_hostel     FOREIGN KEY (hostel_id)    REFERENCES hostel_hostels (id),
  INDEX idx_sp_hosteller_id   (hosteller_id),
  INDEX idx_sp_hostel_id      (hostel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hostel_reviews (
  id           BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hosteller_id BIGINT     NOT NULL,
  hostel_id    BIGINT     NOT NULL,
  booking_id   BIGINT     NOT NULL,
  rating       INT        NOT NULL,
  review_text  TEXT,
  is_visible   TINYINT(1) NOT NULL DEFAULT 1,
  created_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted   TINYINT(1) NOT NULL DEFAULT 0,
  deleted_at   DATETIME   DEFAULT NULL,
  CONSTRAINT fk_hvr_hosteller  FOREIGN KEY (hosteller_id) REFERENCES hosteller_profiles (id),
  CONSTRAINT fk_hvr_hostel     FOREIGN KEY (hostel_id)    REFERENCES hostel_hostels (id),
  CONSTRAINT fk_hvr_booking    FOREIGN KEY (booking_id)   REFERENCES booking_checkins (id),
  INDEX idx_hvr_hosteller_id   (hosteller_id),
  INDEX idx_hvr_hostel_id      (hostel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_audit_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_user_id BIGINT       NOT NULL,
  actor_role    VARCHAR(20)  NOT NULL,
  action        VARCHAR(80)  NOT NULL,
  entity_type   VARCHAR(50),
  entity_id     BIGINT,
  hostel_id     BIGINT,
  before_state  JSON,
  after_state   JSON,
  ip_address    VARCHAR(50),
  user_agent    VARCHAR(300),
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
  deleted_at    DATETIME     DEFAULT NULL,
  INDEX idx_aal_actor_user  (actor_user_id),
  INDEX idx_aal_entity      (entity_type, entity_id),
  INDEX idx_aal_hostel_id   (hostel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_subscriptions (
  id            BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id      BIGINT         NOT NULL,
  plan_name     VARCHAR(30)    NOT NULL,
  max_hostels   INT            NOT NULL DEFAULT 1,
  max_beds      INT            NOT NULL DEFAULT 50,
  price_monthly DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted    TINYINT(1)     NOT NULL DEFAULT 0,
  deleted_at    DATETIME       DEFAULT NULL,
  CONSTRAINT fk_asub_owner  FOREIGN KEY (owner_id) REFERENCES hostel_owners (id),
  INDEX idx_asub_owner_id   (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_feature_flags (
  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id   BIGINT,
  flag_name  VARCHAR(80)  NOT NULL,
  is_enabled TINYINT(1)   NOT NULL DEFAULT 1,
  set_by     BIGINT       NOT NULL,
  reason     VARCHAR(300),
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
  deleted_at DATETIME     DEFAULT NULL,
  INDEX idx_aff_owner_id  (owner_id),
  INDEX idx_aff_flag_name (flag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
