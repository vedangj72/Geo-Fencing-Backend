package com.geofencing

import com.geofencing.db.DatabaseFactory
import com.geofencing.models.ApiResponse
import com.geofencing.routes.configureRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // 1. Initialize Supabase PostgreSQL connection pool
    DatabaseFactory.init()

    // 2. Configure JSON Content Negotiation
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // 3. Configure CORS
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    // 4. Configure Status Pages for Error Handling
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, error = cause.message ?: "Invalid request"))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(false, error = cause.message ?: "Server error"))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(false, error = cause.message ?: "Unexpected error occurred"))
        }
    }

    // 5. Configure Routing
    routing {
        configureRoutes()
    }
}
