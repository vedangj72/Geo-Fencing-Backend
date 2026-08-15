package com.geofencing.repository

import com.geofencing.db.DatabaseFactory
import com.geofencing.models.*
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

class ProfileRepository {

    fun getAllProfiles(): List<Profile> {
        val sql = "SELECT id, name, email, phone, fcm_token, created_at, updated_at FROM profiles ORDER BY name ASC"
        val profiles = mutableListOf<Profile>()
        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    profiles.add(rs.toProfile())
                }
            }
        }
        return profiles
    }

    fun getProfileById(userId: String): Profile? {
        val sql = "SELECT id, name, email, phone, fcm_token, created_at, updated_at FROM profiles WHERE id = CAST(? AS uuid)"
        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.toProfile()
                }
            }
        }
        return null
    }

    fun upsertProfile(id: String, name: String, email: String?, phone: String?, fcmToken: String?): Profile {
        val sql = """
            INSERT INTO profiles (id, name, email, phone, fcm_token, created_at, updated_at)
            VALUES (CAST(? AS uuid), ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (id) DO UPDATE 
            SET name = EXCLUDED.name, 
                email = COALESCE(EXCLUDED.email, profiles.email), 
                phone = COALESCE(EXCLUDED.phone, profiles.phone),
                fcm_token = COALESCE(EXCLUDED.fcm_token, profiles.fcm_token),
                updated_at = NOW()
            RETURNING id, name, email, phone, fcm_token, created_at, updated_at
        """.trimIndent()

        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, name)
                stmt.setString(3, email)
                stmt.setString(4, phone)
                stmt.setString(5, fcmToken)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.toProfile()
                }
            }
        }
        throw IllegalStateException("Failed to upsert profile for user: $id")
    }

    fun updateProfile(id: String, name: String?, phone: String?, fcmToken: String?): Profile? {
        val sql = """
            UPDATE profiles 
            SET name = COALESCE(?, name),
                phone = COALESCE(?, phone),
                fcm_token = COALESCE(?, fcm_token),
                updated_at = NOW()
            WHERE id = CAST(? AS uuid)
            RETURNING id, name, email, phone, fcm_token, created_at, updated_at
        """.trimIndent()

        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, name)
                stmt.setString(2, phone)
                stmt.setString(3, fcmToken)
                stmt.setString(4, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.toProfile()
                }
            }
        }
        return null
    }

    private fun ResultSet.toProfile(): Profile = Profile(
        id = getString("id"),
        name = getString("name"),
        email = getString("email"),
        phone = getString("phone"),
        fcmToken = getString("fcm_token"),
        createdAt = getTimestamp("created_at").toInstant().toString(),
        updatedAt = getTimestamp("updated_at").toInstant().toString()
    )
}

class GroupRepository {

    fun createGroup(name: String, createdByUserId: String): GroupDetailResponse {
        val createGroupSql = """
            INSERT INTO groups (name, created_by, created_at, updated_at)
            VALUES (?, CAST(? AS uuid), NOW(), NOW())
            RETURNING id, name, created_by, created_at, updated_at
        """.trimIndent()

        val addAdminSql = """
            INSERT INTO group_members (group_id, user_id, role, membership_status, joined_at)
            VALUES (CAST(? AS uuid), CAST(? AS uuid), 'ADMIN'::group_role, 'ACTIVE'::membership_status, NOW())
            ON CONFLICT (group_id, user_id) DO UPDATE SET role = 'ADMIN'::group_role, membership_status = 'ACTIVE'::membership_status
            RETURNING id, group_id, user_id, role, membership_status, joined_at
        """.trimIndent()

        DatabaseFactory.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                var group: Group? = null
                conn.prepareStatement(createGroupSql).use { stmt ->
                    stmt.setString(1, name)
                    stmt.setString(2, createdByUserId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        group = rs.toGroup()
                    }
                }

                val createdGroup = group ?: throw IllegalStateException("Failed to create group")

                var member: GroupMember? = null
                conn.prepareStatement(addAdminSql).use { stmt ->
                    stmt.setString(1, createdGroup.id)
                    stmt.setString(2, createdByUserId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        member = rs.toGroupMember()
                    }
                }

                conn.commit()

                val creatorProfile = ProfileRepository().getProfileById(createdByUserId)
                    ?: Profile(id = createdByUserId, name = "User", createdAt = "", updatedAt = "")

                val memberWithProfile = GroupMemberWithProfile(
                    member = member!!,
                    profile = creatorProfile
                )

                return GroupDetailResponse(
                    group = createdGroup,
                    members = listOf(memberWithProfile),
                    activeGeofence = null
                )
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    fun addMember(groupId: String, userId: String, role: GroupRole): GroupMember {
        val sql = """
            INSERT INTO group_members (group_id, user_id, role, membership_status, joined_at)
            VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS group_role), 'ACTIVE'::membership_status, NOW())
            ON CONFLICT (group_id, user_id) DO UPDATE SET role = EXCLUDED.role, membership_status = 'ACTIVE'::membership_status
            RETURNING id, group_id, user_id, role, membership_status, joined_at
        """.trimIndent()

        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, groupId)
                stmt.setString(2, userId)
                stmt.setString(3, role.name)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.toGroupMember()
                }
            }
        }
        throw IllegalStateException("Failed to add member to group")
    }

    fun getAllGroups(): List<Group> {
        val sql = "SELECT id, name, created_by, created_at, updated_at FROM groups ORDER BY created_at DESC"
        val groups = mutableListOf<Group>()
        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    groups.add(rs.toGroup())
                }
            }
        }
        return groups
    }

    fun getUserGroups(userId: String): List<Group> {
        val sql = """
            SELECT g.id, g.name, g.created_by, g.created_at, g.updated_at 
            FROM groups g
            INNER JOIN group_members gm ON g.id = gm.group_id
            WHERE gm.user_id = CAST(? AS uuid) AND gm.membership_status = 'ACTIVE'::membership_status
            ORDER BY g.created_at DESC
        """.trimIndent()

        val groups = mutableListOf<Group>()
        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    groups.add(rs.toGroup())
                }
            }
        }
        return groups
    }

    fun isUserMemberOfGroup(groupId: String, userId: String): Boolean {
        val sql = """
            SELECT 1 FROM group_members 
            WHERE group_id = CAST(? AS uuid) 
              AND user_id = CAST(? AS uuid) 
              AND membership_status = 'ACTIVE'::membership_status
        """.trimIndent()

        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, groupId)
                stmt.setString(2, userId)
                val rs = stmt.executeQuery()
                return rs.next()
            }
        }
    }

    fun getGroupDetail(groupId: String): GroupDetailResponse? {
        val groupSql = "SELECT id, name, created_by, created_at, updated_at FROM groups WHERE id = CAST(? AS uuid)"
        val membersSql = """
            SELECT gm.id, gm.group_id, gm.user_id, gm.role, gm.membership_status, gm.joined_at,
                   p.name, p.email, p.phone, p.fcm_token, p.created_at as p_created_at, p.updated_at as p_updated_at
            FROM group_members gm
            JOIN profiles p ON gm.user_id = p.id
            WHERE gm.group_id = CAST(? AS uuid)
        """.trimIndent()

        var foundGroup: Group? = null
        val members = mutableListOf<GroupMemberWithProfile>()

        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(groupSql).use { stmt ->
                stmt.setString(1, groupId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    foundGroup = rs.toGroup()
                }
            }

            if (foundGroup == null) return null

            conn.prepareStatement(membersSql).use { stmt ->
                stmt.setString(1, groupId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val member = rs.toGroupMember()
                    val profile = Profile(
                        id = rs.getString("user_id"),
                        name = rs.getString("name"),
                        email = rs.getString("email"),
                        phone = rs.getString("phone"),
                        fcmToken = rs.getString("fcm_token"),
                        createdAt = rs.getTimestamp("p_created_at").toInstant().toString(),
                        updatedAt = rs.getTimestamp("p_updated_at").toInstant().toString()
                    )
                    members.add(GroupMemberWithProfile(member, profile))
                }
            }
        }

        val geofence = GeofenceRepository().getActiveGeofenceForGroup(groupId)
        return GroupDetailResponse(foundGroup!!, members, geofence)
    }

    private fun ResultSet.toGroup(): Group = Group(
        id = getString("id"),
        name = getString("name"),
        createdBy = getString("created_by"),
        createdAt = getTimestamp("created_at").toInstant().toString(),
        updatedAt = getTimestamp("updated_at").toInstant().toString()
    )

    private fun ResultSet.toGroupMember(): GroupMember = GroupMember(
        id = getString("id"),
        groupId = getString("group_id"),
        userId = getString("user_id"),
        role = GroupRole.valueOf(getString("role")),
        membershipStatus = MembershipStatus.valueOf(getString("membership_status")),
        joinedAt = getTimestamp("joined_at").toInstant().toString()
    )
}

class GeofenceRepository {

    fun createOrUpdateGeofence(groupId: String, latitude: Double, longitude: Double, radiusMeters: Double): Geofence {
        val deactivateSql = "UPDATE geofences SET is_active = false, updated_at = NOW() WHERE group_id = CAST(? AS uuid) AND is_active = true"
        val insertSql = """
            INSERT INTO geofences (group_id, latitude, longitude, radius_meters, is_active, created_at, updated_at)
            VALUES (CAST(? AS uuid), ?, ?, ?, true, NOW(), NOW())
            RETURNING id, group_id, latitude, longitude, radius_meters, is_active, created_at, updated_at
        """.trimIndent()

        DatabaseFactory.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(deactivateSql).use { stmt ->
                    stmt.setString(1, groupId)
                    stmt.executeUpdate()
                }

                var geofence: Geofence? = null
                conn.prepareStatement(insertSql).use { stmt ->
                    stmt.setString(1, groupId)
                    stmt.setDouble(2, latitude)
                    stmt.setDouble(3, longitude)
                    stmt.setDouble(4, radiusMeters)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        geofence = rs.toGeofence()
                    }
                }

                conn.commit()
                return geofence ?: throw IllegalStateException("Failed to create geofence")
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    fun getActiveGeofenceForGroup(groupId: String): Geofence? {
        val sql = """
            SELECT id, group_id, latitude, longitude, radius_meters, is_active, created_at, updated_at
            FROM geofences
            WHERE group_id = CAST(? AS uuid) AND is_active = true
            LIMIT 1
        """.trimIndent()

        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, groupId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.toGeofence()
                }
            }
        }
        return null
    }

    private fun ResultSet.toGeofence(): Geofence = Geofence(
        id = getString("id"),
        groupId = getString("group_id"),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        radiusMeters = getDouble("radius_meters"),
        isActive = getBoolean("is_active"),
        createdAt = getTimestamp("created_at").toInstant().toString(),
        updatedAt = getTimestamp("updated_at").toInstant().toString()
    )
}

class GeofenceStatusRepository {

    fun getMemberStatusesForGroup(groupId: String): List<MemberStatusWithProfile> {
        val sql = """
            SELECT mgs.id, mgs.group_id, mgs.user_id, mgs.status, mgs.last_latitude, mgs.last_longitude,
                   mgs.last_event, mgs.last_event_at, mgs.updated_at,
                   p.name, p.email, p.phone, p.fcm_token, p.created_at as p_created_at, p.updated_at as p_updated_at
            FROM member_geofence_status mgs
            JOIN profiles p ON mgs.user_id = p.id
            WHERE mgs.group_id = CAST(? AS uuid)
        """.trimIndent()

        val list = mutableListOf<MemberStatusWithProfile>()
        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, groupId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val status = MemberGeofenceStatus(
                        id = rs.getString("id"),
                        groupId = rs.getString("group_id"),
                        userId = rs.getString("user_id"),
                        status = GeofenceMemberStatus.valueOf(rs.getString("status")),
                        lastLatitude = rs.getObject("last_latitude") as? Double,
                        lastLongitude = rs.getObject("last_longitude") as? Double,
                        lastEvent = rs.getString("last_event")?.let { GeofenceEventType.valueOf(it) },
                        lastEventAt = rs.getTimestamp("last_event_at")?.toInstant()?.toString(),
                        updatedAt = rs.getTimestamp("updated_at").toInstant().toString()
                    )
                    val profile = Profile(
                        id = rs.getString("user_id"),
                        name = rs.getString("name"),
                        email = rs.getString("email"),
                        phone = rs.getString("phone"),
                        fcmToken = rs.getString("fcm_token"),
                        createdAt = rs.getTimestamp("p_created_at").toInstant().toString(),
                        updatedAt = rs.getTimestamp("p_updated_at").toInstant().toString()
                    )
                    list.add(MemberStatusWithProfile(status, profile))
                }
            }
        }
        return list
    }

    fun updateStatus(
        groupId: String,
        userId: String,
        eventType: GeofenceEventType,
        lat: Double,
        lon: Double,
        occurredAtIso: String?
    ): MemberGeofenceStatus {
        val memberStatus = when (eventType) {
            GeofenceEventType.ENTER -> GeofenceMemberStatus.INSIDE
            GeofenceEventType.EXIT -> GeofenceMemberStatus.OUTSIDE
            GeofenceEventType.DWELL -> GeofenceMemberStatus.INSIDE
        }

        val sql = """
            INSERT INTO member_geofence_status 
            (group_id, user_id, status, last_latitude, last_longitude, last_event, last_event_at, updated_at)
            VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS geofence_member_status), ?, ?, CAST(? AS geofence_event_type), ?, NOW())
            ON CONFLICT (group_id, user_id) DO UPDATE 
            SET status = EXCLUDED.status,
                last_latitude = EXCLUDED.last_latitude,
                last_longitude = EXCLUDED.last_longitude,
                last_event = EXCLUDED.last_event,
                last_event_at = EXCLUDED.last_event_at,
                updated_at = NOW()
            RETURNING id, group_id, user_id, status, last_latitude, last_longitude, last_event, last_event_at, updated_at
        """.trimIndent()

        val timestamp = try {
            if (occurredAtIso != null) Timestamp.from(Instant.parse(occurredAtIso)) else Timestamp.from(Instant.now())
        } catch (e: Exception) {
            Timestamp.from(Instant.now())
        }

        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, groupId)
                stmt.setString(2, userId)
                stmt.setString(3, memberStatus.name)
                stmt.setDouble(4, lat)
                stmt.setDouble(5, lon)
                stmt.setString(6, eventType.name)
                stmt.setTimestamp(7, timestamp)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return MemberGeofenceStatus(
                        id = rs.getString("id"),
                        groupId = rs.getString("group_id"),
                        userId = rs.getString("user_id"),
                        status = GeofenceMemberStatus.valueOf(rs.getString("status")),
                        lastLatitude = rs.getDouble("last_latitude"),
                        lastLongitude = rs.getDouble("last_longitude"),
                        lastEvent = rs.getString("last_event")?.let { GeofenceEventType.valueOf(it) },
                        lastEventAt = rs.getTimestamp("last_event_at")?.toInstant()?.toString(),
                        updatedAt = rs.getTimestamp("updated_at").toInstant().toString()
                    )
                }
            }
        }
        throw IllegalStateException("Failed to update member geofence status")
    }
}

class GeofenceEventRepository {

    fun recordEvent(
        groupId: String,
        userId: String,
        geofenceId: String,
        eventType: GeofenceEventType,
        latitude: Double,
        longitude: Double,
        occurredAtIso: String?
    ): GeofenceEvent {
        val sql = """
            INSERT INTO geofence_events 
            (group_id, user_id, geofence_id, event_type, latitude, longitude, occurred_at, received_at, processed)
            VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS geofence_event_type), ?, ?, ?, NOW(), true)
            RETURNING id, group_id, user_id, geofence_id, event_type, latitude, longitude, occurred_at, received_at, processed
        """.trimIndent()

        val occurredTimestamp = try {
            if (occurredAtIso != null) Timestamp.from(Instant.parse(occurredAtIso)) else Timestamp.from(Instant.now())
        } catch (e: Exception) {
            Timestamp.from(Instant.now())
        }

        DatabaseFactory.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, groupId)
                stmt.setString(2, userId)
                stmt.setString(3, geofenceId)
                stmt.setString(4, eventType.name)
                stmt.setDouble(5, latitude)
                stmt.setDouble(6, longitude)
                stmt.setTimestamp(7, occurredTimestamp)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return GeofenceEvent(
                        id = rs.getString("id"),
                        groupId = rs.getString("group_id"),
                        userId = rs.getString("user_id"),
                        geofenceId = rs.getString("geofence_id"),
                        eventType = GeofenceEventType.valueOf(rs.getString("event_type")),
                        latitude = rs.getDouble("latitude"),
                        longitude = rs.getDouble("longitude"),
                        occurredAt = rs.getTimestamp("occurred_at").toInstant().toString(),
                        receivedAt = rs.getTimestamp("received_at").toInstant().toString(),
                        processed = rs.getBoolean("processed")
                    )
                }
            }
        }
        throw IllegalStateException("Failed to record geofence event")
    }
}
