-- Development Seed Data for Supabase
-- NOTE: auth.users entries are required before profiles due to FK constraint.

DO $$
DECLARE
    vedang_id UUID := '11111111-1111-1111-1111-111111111111';
    rahul_id  UUID := '22222222-2222-2222-2222-222222222222';
    amit_id   UUID := '33333333-3333-3333-3333-333333333333';
    john_id   UUID := '44444444-4444-4444-4444-444444444444';
    
    group_goa_id UUID := 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d';
    geofence_goa_id UUID := 'f1e2d3c4-b5a6-9788-7654-3210fedcba98';
BEGIN
    -- 1. Insert into auth.users (if not already existing)
    INSERT INTO auth.users (
        id, instance_id, aud, role, email, encrypted_password, 
        email_confirmed_at, recovery_sent_at, last_sign_in_at, 
        raw_app_meta_data, raw_user_meta_data, is_super_admin, 
        created_at, updated_at, phone, phone_confirmed_at, 
        confirmed_at, email_change, email_change_token_new, 
        recovery_token
    ) VALUES 
    (vedang_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'vedang@example.com', '$2a$10$abcdefghijklmnopqrstuv', NOW(), NULL, NOW(), '{"provider":"email","providers":["email"]}', '{"name":"Vedang"}', FALSE, NOW(), NOW(), NULL, NULL, NOW(), NULL, NULL, NULL),
    (rahul_id,  '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'rahul@example.com',  '$2a$10$abcdefghijklmnopqrstuv', NOW(), NULL, NOW(), '{"provider":"email","providers":["email"]}', '{"name":"Rahul"}',  FALSE, NOW(), NOW(), NULL, NULL, NOW(), NULL, NULL, NULL),
    (amit_id,   '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'amit@example.com',   '$2a$10$abcdefghijklmnopqrstuv', NOW(), NULL, NOW(), '{"provider":"email","providers":["email"]}', '{"name":"Amit"}',   FALSE, NOW(), NOW(), NULL, NULL, NOW(), NULL, NULL, NULL),
    (john_id,   '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'john@example.com',   '$2a$10$abcdefghijklmnopqrstuv', NOW(), NULL, NOW(), '{"provider":"email","providers":["email"]}', '{"name":"John"}',   FALSE, NOW(), NOW(), NULL, NULL, NOW(), NULL, NULL, NULL)
    ON CONFLICT (id) DO NOTHING;

    -- 2. Insert into public.profiles
    INSERT INTO public.profiles (id, name, email, phone, created_at, updated_at)
    VALUES 
    (vedang_id, 'Vedang', 'vedang@example.com', '+1234567890', NOW(), NOW()),
    (rahul_id,  'Rahul',  'rahul@example.com',  '+1234567891', NOW(), NOW()),
    (amit_id,   'Amit',   'amit@example.com',   '+1234567892', NOW(), NOW()),
    (john_id,   'John',   'john@example.com',   '+1234567893', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, email = EXCLUDED.email;

    -- 3. Create Group: "Goa Trip" (created by Vedang)
    INSERT INTO public.groups (id, name, created_by, created_at, updated_at)
    VALUES (group_goa_id, 'Goa Trip', vedang_id, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

    -- 4. Create Group Members
    -- Vedang = ADMIN, Rahul = MEMBER, Amit = MEMBER, John = MEMBER
    INSERT INTO public.group_members (group_id, user_id, role, membership_status, joined_at)
    VALUES
    (group_goa_id, vedang_id, 'ADMIN'::group_role, 'ACTIVE'::membership_status, NOW()),
    (group_goa_id, rahul_id,  'MEMBER'::group_role, 'ACTIVE'::membership_status, NOW()),
    (group_goa_id, amit_id,   'MEMBER'::group_role, 'ACTIVE'::membership_status, NOW()),
    (group_goa_id, john_id,   'MEMBER'::group_role, 'ACTIVE'::membership_status, NOW())
    ON CONFLICT (group_id, user_id) DO UPDATE SET role = EXCLUDED.role, membership_status = EXCLUDED.membership_status;

    -- 5. Create Active Geofence for "Goa Trip"
    -- lat = 18.5204, lon = 73.8567, radius = 1000 meters
    INSERT INTO public.geofences (id, group_id, latitude, longitude, radius_meters, is_active, created_at, updated_at)
    VALUES (geofence_goa_id, group_goa_id, 18.5204, 73.8567, 1000.0, true, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

    -- 6. Initial Member Geofence States
    -- Vedang = INSIDE, Rahul = INSIDE, Amit = INSIDE, John = OUTSIDE
    INSERT INTO public.member_geofence_status 
    (group_id, user_id, status, last_latitude, last_longitude, last_event, last_event_at, updated_at)
    VALUES
    (group_goa_id, vedang_id, 'INSIDE'::geofence_member_status,  18.5204, 73.8567, 'ENTER'::geofence_event_type, NOW(), NOW()),
    (group_goa_id, rahul_id,  'INSIDE'::geofence_member_status,  18.5210, 73.8570, 'ENTER'::geofence_event_type, NOW(), NOW()),
    (group_goa_id, amit_id,   'INSIDE'::geofence_member_status,  18.5198, 73.8560, 'ENTER'::geofence_event_type, NOW(), NOW()),
    (group_goa_id, john_id,   'OUTSIDE'::geofence_member_status, 18.5400, 73.8800, 'EXIT'::geofence_event_type,  NOW(), NOW())
    ON CONFLICT (group_id, user_id) DO UPDATE 
    SET status = EXCLUDED.status, 
        last_latitude = EXCLUDED.last_latitude, 
        last_longitude = EXCLUDED.last_longitude,
        last_event = EXCLUDED.last_event,
        last_event_at = EXCLUDED.last_event_at,
        updated_at = NOW();

END $$;
