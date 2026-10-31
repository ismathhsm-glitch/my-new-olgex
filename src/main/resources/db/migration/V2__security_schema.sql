CREATE TABLE face_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    embedding_encrypted VARBINARY(4096) NOT NULL,
    sample_index INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_face_template_sample (user_id, sample_index),
    CONSTRAINT fk_face_templates_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE trusted_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    device_identifier VARCHAR(255) NOT NULL,
    device_hash VARCHAR(64),
    device_name VARCHAR(255),
    user_agent VARCHAR(1000),
    ip_address VARCHAR(64),
    last_ip VARCHAR(64),
    last_location VARCHAR(255),
    trusted BOOLEAN NOT NULL DEFAULT FALSE,
    first_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trusted_device_hash (user_id, device_hash),
    KEY idx_trusted_devices_user (user_id),
    CONSTRAINT fk_trusted_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE login_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    email VARCHAR(255) NOT NULL,
    ip VARCHAR(64),
    ip_address VARCHAR(64),
    device_hash VARCHAR(64),
    location VARCHAR(255),
    risk_score INT NOT NULL DEFAULT 0,
    risk_reasons JSON,
    outcome VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    is_successful BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason VARCHAR(255),
    attempted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_history_user_created (user_id, created_at),
    KEY idx_login_history_email_attempted (email, attempted_at),
    CONSTRAINT fk_login_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE login_attempts LIKE login_history;
ALTER TABLE login_attempts
    ADD CONSTRAINT fk_login_attempts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user_active (user_id, revoked, expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_hash (token_hash),
    KEY idx_password_reset_user (user_id, expires_at),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE email_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_hash (token_hash),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE organization_invitation_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_invitation_hash (token_hash),
    KEY idx_org_invitation_user (user_id, organization_id, expires_at),
    CONSTRAINT fk_org_invitation_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_invitation_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE login_challenges (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    device_hash VARCHAR(64),
    ip_address VARCHAR(64),
    risk_score INT NOT NULL,
    risk_reasons JSON,
    nonce_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_challenge_user_expires (user_id, expires_at),
    CONSTRAINT fk_login_challenges_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE step_up_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    nonce_hash VARCHAR(64),
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_step_up_token_hash (token_hash),
    KEY idx_step_up_user_action (user_id, action, used, expires_at),
    CONSTRAINT fk_step_up_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE step_up_challenges (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    nonce_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_step_up_challenge_user (user_id, expires_at),
    CONSTRAINT fk_step_up_challenge_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    actor_id BIGINT,
    organization_id BIGINT,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    details JSON,
    ip_address VARCHAR(64),
    device VARCHAR(255),
    location VARCHAR(255),
    risk_score INT,
    verification_result VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_org_created (organization_id, created_at),
    KEY idx_audit_actor_created (actor_id, created_at),
    KEY idx_audit_action_created (action, created_at),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL
) ENGINE=InnoDB;
