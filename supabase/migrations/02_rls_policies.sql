-- Enable Row Level Security (RLS) on all public tables

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE geofences ENABLE ROW LEVEL SECURITY;
ALTER TABLE member_geofence_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE geofence_events ENABLE ROW LEVEL SECURITY;

-- Security RLS Policies (Restricting Direct REST/PostgREST Client Access)
-- The Ktor backend operates via direct PostgreSQL connection (or service role), bypassing RLS for trusted application logic.
-- Client devices communicate strictly with Ktor API.

-- Profiles: Users can view their own profile or profiles of members in shared groups.
CREATE POLICY "Users can view their own profile" ON profiles
    FOR SELECT USING (auth.uid() = id);

-- Groups: Members can view groups they belong to.
CREATE POLICY "Group members can view group details" ON groups
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM group_members
            WHERE group_members.group_id = groups.id
            AND group_members.user_id = auth.uid()
        )
    );

-- Group Members: Members can view fellow members in their groups.
CREATE POLICY "Group members can view membership list" ON group_members
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM group_members gm
            WHERE gm.group_id = group_members.group_id
            AND gm.user_id = auth.uid()
        )
    );

-- Geofences: Members can view active geofences in their groups.
CREATE POLICY "Group members can view active geofences" ON geofences
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM group_members
            WHERE group_members.group_id = geofences.group_id
            AND group_members.user_id = auth.uid()
        )
    );

-- Member Geofence Status: Members can view statuses in their groups.
CREATE POLICY "Group members can view geofence statuses" ON member_geofence_status
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM group_members
            WHERE group_members.group_id = member_geofence_status.group_id
            AND group_members.user_id = auth.uid()
        )
    );
