package com.geofencing.routes

import com.geofencing.models.*
import com.geofencing.repository.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun ApplicationCall.extractUserId(explicitUserId: String? = null): String {
    if (!explicitUserId.isNullOrBlank()) return explicitUserId
    val headerUserId = request.headers["X-User-Id"]
    if (!headerUserId.isNullOrBlank()) return headerUserId
    val queryUserId = request.queryParameters["userId"]
    if (!queryUserId.isNullOrBlank()) return queryUserId
    
    // Fallback default: Vedang seed UUID
    return "11111111-1111-1111-1111-111111111111"
}

fun Routing.configureRoutes() {

    val profileRepo = ProfileRepository()
    val groupRepo = GroupRepository()
    val geofenceRepo = GeofenceRepository()
    val statusRepo = GeofenceStatusRepository()
    val eventRepo = GeofenceEventRepository()

    get("/health") {
        call.respond(HttpStatusCode.OK, ApiResponse(true, data = "Geo-Fencing Ktor Backend Prototype is running normally", error = null))
    }

    get("/") {
        call.respond(HttpStatusCode.OK, ApiResponse(true, data = "Welcome to Geo-Fencing Backend Prototype API", error = null))
    }

    route("/api/v1") {


        route("/profiles") {
            get {
                val profiles = profileRepo.getAllProfiles()
                call.respond(HttpStatusCode.OK, ApiResponse(true, data = profiles))
            }

            get("/me") {
                val userId = call.extractUserId()
                val profile = profileRepo.getProfileById(userId)
                    ?: profileRepo.upsertProfile(userId, "User-${userId.take(8)}", null, null, null)
                call.respond(HttpStatusCode.OK, ApiResponse(true, data = profile))
            }

            get("/{userId}") {
                val targetUserId = call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse<Profile>(false, error = "Missing userId"))
                val profile = profileRepo.getProfileById(targetUserId)
                if (profile != null) {
                    call.respond(HttpStatusCode.OK, ApiResponse(true, data = profile))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Profile>(false, error = "Profile not found"))
                }
            }

            put("/me") {
                val req = call.receive<UpdateProfileRequest>()
                val userId = call.extractUserId(req.userId)
                val updated = profileRepo.updateProfile(userId, req.name, req.phone, req.fcmToken)
                    ?: profileRepo.upsertProfile(userId, req.name ?: "User", null, req.phone, req.fcmToken)
                call.respond(HttpStatusCode.OK, ApiResponse(true, data = updated))
            }
        }

        // --- Groups ---
        route("/groups") {
            get {
                val userId = call.request.queryParameters["userId"]
                val groups = if (userId != null) groupRepo.getUserGroups(userId) else groupRepo.getAllGroups()
                call.respond(HttpStatusCode.OK, ApiResponse(true, data = groups))
            }

            post {
                val req = call.receive<CreateGroupRequest>()
                if (req.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<GroupDetailResponse>(false, error = "Group name cannot be empty"))
                    return@post
                }
                val userId = call.extractUserId(req.userId)
                val createdGroup = groupRepo.createGroup(req.name, userId)
                call.respond(HttpStatusCode.Created, ApiResponse(true, data = createdGroup))
            }

            get("/{groupId}") {
                val groupId = call.parameters["groupId"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse<GroupDetailResponse>(false, error = "Missing groupId"))
                val groupDetail = groupRepo.getGroupDetail(groupId)
                if (groupDetail != null) {
                    call.respond(HttpStatusCode.OK, ApiResponse(true, data = groupDetail))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<GroupDetailResponse>(false, error = "Group not found"))
                }
            }

            // Join or Add Member to Group
            post("/{groupId}/members") {
                val groupId = call.parameters["groupId"] ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<GroupMember>(false, error = "Missing groupId"))
                val req = call.receive<AddGroupMemberRequest>()
                val member = groupRepo.addMember(groupId, req.userId, req.role)
                call.respond(HttpStatusCode.Created, ApiResponse(true, data = member))
            }

            // --- Geofence Configuration ---
            post("/{groupId}/geofence") {
                val groupId = call.parameters["groupId"] ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Geofence>(false, error = "Missing groupId"))
                val req = call.receive<CreateGeofenceRequest>()
                val geofence = geofenceRepo.createOrUpdateGeofence(groupId, req.latitude, req.longitude, req.radiusMeters)
                call.respond(HttpStatusCode.Created, ApiResponse(true, data = geofence))
            }

            get("/{groupId}/geofence") {
                val groupId = call.parameters["groupId"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse<Geofence>(false, error = "Missing groupId"))
                val geofence = geofenceRepo.getActiveGeofenceForGroup(groupId)
                if (geofence != null) {
                    call.respond(HttpStatusCode.OK, ApiResponse(true, data = geofence))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Geofence>(false, error = "No active geofence for this group"))
                }
            }

            // --- GEOFENCE EVENT RECORDING ---
            // Endpoint: POST /api/v1/groups/{groupId}/geofence/events (and /groups/{groupId}/geofence/events)
            post("/{groupId}/geofence/events") {
                val groupId = call.parameters["groupId"] ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<GeofenceEvent>(false, error = "Missing groupId"))
                val req = call.receive<RecordGeofenceEventRequest>()
                val userId = call.extractUserId(req.userId)

                val activeGeofence = geofenceRepo.getActiveGeofenceForGroup(groupId)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<GeofenceEvent>(false, error = "No active geofence found for this group"))

                // Record the event
                val recordedEvent = eventRepo.recordEvent(
                    groupId = groupId,
                    userId = userId,
                    geofenceId = activeGeofence.id,
                    eventType = req.eventType,
                    latitude = req.latitude,
                    longitude = req.longitude,
                    occurredAtIso = req.occurredAt
                )
                
                statusRepo.updateStatus(
                    groupId = groupId,
                    userId = userId,
                    eventType = req.eventType,
                    lat = req.latitude,
                    lon = req.longitude,
                    occurredAtIso = req.occurredAt
                )

                call.respond(HttpStatusCode.Created, ApiResponse(true, data = recordedEvent))
            }

            get("/{groupId}/members/status") {
                val groupId = call.parameters["groupId"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse<List<MemberStatusWithProfile>>(false, error = "Missing groupId"))
                val statuses = statusRepo.getMemberStatusesForGroup(groupId)
                call.respond(HttpStatusCode.OK, ApiResponse(true, data = statuses))
            }
        }
    }
}
