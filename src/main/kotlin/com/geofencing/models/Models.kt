package com.geofencing.models

import kotlinx.serialization.Serializable

// --- Enums matching PostgreSQL enums ---

@Serializable
enum class GroupRole {
    ADMIN, MEMBER
}

@Serializable
enum class MembershipStatus {
    ACTIVE, PENDING, REJECTED, LEFT, REMOVED
}

@Serializable
enum class InvitationType {
    DIRECT, LINK, CODE
}

@Serializable
enum class InvitationStatus {
    PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED
}

@Serializable
enum class GeofenceMemberStatus {
    INSIDE, OUTSIDE, UNKNOWN
}

@Serializable
enum class GeofenceEventType {
    ENTER, EXIT, DWELL
}

// --- Domain Models ---

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val fcmToken: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class GroupMember(
    val id: String,
    val groupId: String,
    val userId: String,
    val role: GroupRole,
    val membershipStatus: MembershipStatus,
    val joinedAt: String
)

@Serializable
data class Geofence(
    val id: String,
    val groupId: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class MemberGeofenceStatus(
    val id: String,
    val groupId: String,
    val userId: String,
    val status: GeofenceMemberStatus,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastEvent: GeofenceEventType? = null,
    val lastEventAt: String? = null,
    val updatedAt: String
)

@Serializable
data class GeofenceEvent(
    val id: String,
    val groupId: String,
    val userId: String,
    val geofenceId: String,
    val eventType: GeofenceEventType,
    val latitude: Double,
    val longitude: Double,
    val occurredAt: String,
    val receivedAt: String,
    val processed: Boolean
)

// --- Request DTOs (Prototype Friendly - Optional JWT & Explicit userId allowed) ---

@Serializable
data class CreateGroupRequest(
    val name: String,
    val userId: String? = null
)

@Serializable
data class AddGroupMemberRequest(
    val userId: String,
    val role: GroupRole = GroupRole.MEMBER
)

@Serializable
data class CreateGeofenceRequest(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double
)

@Serializable
data class RecordGeofenceEventRequest(
    val userId: String? = null,
    val eventType: GeofenceEventType,
    val latitude: Double,
    val longitude: Double,
    val occurredAt: String? = null
)

@Serializable
data class UpdateProfileRequest(
    val userId: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val fcmToken: String? = null
)

// --- Response Wrappers ---

@Serializable
data class GroupDetailResponse(
    val group: Group,
    val members: List<GroupMemberWithProfile>,
    val activeGeofence: Geofence?
)

@Serializable
data class GroupMemberWithProfile(
    val member: GroupMember,
    val profile: Profile
)

@Serializable
data class MemberStatusWithProfile(
    val status: MemberGeofenceStatus,
    val profile: Profile
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)
