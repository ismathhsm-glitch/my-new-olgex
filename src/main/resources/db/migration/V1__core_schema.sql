CREATE TABLE organizations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(100),
    logo_url VARCHAR(1000),
    currency VARCHAR(3),
    tax_settings JSON,
    bank_details_encrypted VARBINARY(4096),
    admin_user_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_organizations_email (email)
) ENGINE=InnoDB;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    system_role VARCHAR(32) NOT NULL DEFAULT 'USER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME,
    face_enrolled BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_organization_id (organization_id),
    CONSTRAINT fk_users_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT chk_users_role CHECK (role IN ('SUPER_ADMIN', 'ORG_ADMIN', 'ACCOUNTANT', 'STAFF', 'VIEWER', 'ADMIN')),
    CONSTRAINT chk_users_system_role CHECK (system_role IN ('SUPER_ADMIN', 'USER'))
) ENGINE=InnoDB;

ALTER TABLE organizations
    ADD CONSTRAINT fk_organizations_admin FOREIGN KEY (admin_user_id) REFERENCES users (id);

CREATE TABLE organization_members (
    user_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    invited_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, org_id),
    KEY idx_org_members_org_role (org_id, role),
    CONSTRAINT fk_org_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_members_org FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT chk_org_members_role CHECK (role IN ('ORG_ADMIN', 'ACCOUNTANT', 'STAFF', 'VIEWER'))
) ENGINE=InnoDB;

CREATE TABLE invoices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    invoice_number VARCHAR(100),
    amount DECIMAL(15, 2),
    status VARCHAR(32),
    due_date DATE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_invoices_organization_id (organization_id),
    CONSTRAINT fk_invoices_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
) ENGINE=InnoDB;
