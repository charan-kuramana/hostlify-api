-- Access tokens issued before this time are rejected (set on logout / password reset)
ALTER TABLE auth_users ADD COLUMN sessions_revoked_at TIMESTAMP NULL;
