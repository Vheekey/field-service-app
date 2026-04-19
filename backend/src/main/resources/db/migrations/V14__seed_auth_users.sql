INSERT INTO users (id, email, password_hash, name, status, created_at, updated_at)
VALUES
    (
        '00000000-0000-4000-8000-000000000001',
        'admin@example.com',
        '$2a$12$xpNkNORgkWSN0giFjrBd1.5hrZEL9PUXXocLIyb33am6qn.8fIeV6',
        'Admin User',
        'ACTIVE',
        now(),
        now()
    ),
    (
        '00000000-0000-4000-8000-000000000002',
        'dispatcher@example.com',
        '$2a$12$WtDBIFXcrm8JTPmiDTGesuWVuWAshU9tyrfyLaAt7UJa.Li1Vx8e.',
        'Dispatcher User',
        'ACTIVE',
        now(),
        now()
    ),
    (
        '00000000-0000-4000-8000-000000000003',
        'worker@example.com',
        '$2a$12$gU.QFSw7nTF7FyEjtEfV2eB7uZmhi8xBSL5UWMHPSgs0iKVSXTLXa',
        'Worker User',
        'ACTIVE',
        now(),
        now()
    );

INSERT INTO user_roles (user_id, role)
VALUES
    ('00000000-0000-4000-8000-000000000001', 'ADMIN'),
    ('00000000-0000-4000-8000-000000000002', 'DISPATCHER'),
    ('00000000-0000-4000-8000-000000000003', 'FIELD_WORKER');

INSERT INTO worker_profiles (id, user_id, active, home_base_name, created_at, updated_at)
VALUES (
    '00000000-0000-4000-8000-000000000103',
    '00000000-0000-4000-8000-000000000003',
    true,
    'Default depot',
    now(),
    now()
);
