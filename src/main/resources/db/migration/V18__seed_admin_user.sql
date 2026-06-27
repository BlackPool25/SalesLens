-- Seed default admin user
-- Password: Admin123! (BCrypt, cost 12)

INSERT INTO users (id, username, first_name, last_name, email, password, role)
VALUES (9999, 'admin', 'System', 'Admin', 'admin@saleslens.local',
        '$2a$12$xdt3q2fl74QwTLbKsYQQ2eocV.vmWUgNOFydCmLCVYJ7uCPQCcncO',
        'ADMIN')
ON CONFLICT (username) DO NOTHING;
