INSERT INTO organizations (id, name, email, currency, created_at)
VALUES (1, 'Olgax Demo Organization', 'demo-org@olgax.local', 'USD', CURRENT_TIMESTAMP);

INSERT INTO users
    (id, organization_id, full_name, name, email, password_hash, role, system_role,
     status, email_verified, failed_attempts, face_enrolled, is_active, mfa_enabled, created_at)
VALUES
    (1, 1, 'Demo Super Admin', 'Demo Super Admin', 'superadmin@olgax.local',
     '${demo_password_hash}',
     'SUPER_ADMIN', 'SUPER_ADMIN', 'ACTIVE', TRUE, 0, FALSE, TRUE, FALSE, CURRENT_TIMESTAMP),
    (2, 1, 'Demo Organization Admin', 'Demo Organization Admin', 'orgadmin@olgax.local',
     '${demo_password_hash}',
     'ORG_ADMIN', 'USER', 'ACTIVE', TRUE, 0, FALSE, TRUE, FALSE, CURRENT_TIMESTAMP),
    (3, 1, 'Demo Accountant', 'Demo Accountant', 'accountant@olgax.local',
     '${demo_password_hash}',
     'ACCOUNTANT', 'USER', 'ACTIVE', TRUE, 0, FALSE, TRUE, FALSE, CURRENT_TIMESTAMP),
    (4, 1, 'Demo Staff', 'Demo Staff', 'staff@olgax.local',
     '${demo_password_hash}',
     'STAFF', 'USER', 'ACTIVE', TRUE, 0, FALSE, TRUE, FALSE, CURRENT_TIMESTAMP),
    (5, 1, 'Demo Viewer', 'Demo Viewer', 'viewer@olgax.local',
     '${demo_password_hash}',
     'VIEWER', 'USER', 'ACTIVE', TRUE, 0, FALSE, TRUE, FALSE, CURRENT_TIMESTAMP);

UPDATE organizations SET admin_user_id = 2 WHERE id = 1;

INSERT INTO organization_members (user_id, org_id, role, status, invited_at)
VALUES
    (1, 1, 'ORG_ADMIN', 'ACTIVE', CURRENT_TIMESTAMP),
    (2, 1, 'ORG_ADMIN', 'ACTIVE', CURRENT_TIMESTAMP),
    (3, 1, 'ACCOUNTANT', 'ACTIVE', CURRENT_TIMESTAMP),
    (4, 1, 'STAFF', 'ACTIVE', CURRENT_TIMESTAMP),
    (5, 1, 'VIEWER', 'ACTIVE', CURRENT_TIMESTAMP);
