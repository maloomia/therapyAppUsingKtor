package com.example.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("jwt") {
            realm = "Access to the '/profile' path"
            verifier(JWT.require(Algorithm.HMAC256("your-secret-key")).build())
            validate { credential ->
                val username = credential.payload.getClaim("username").asString()
                if (username != null) {
                    UserIdPrincipal(username)
                } else {
                    null
                }
            }
        }
    }

    routing {
        authenticate("jwt") {
            get("/protected/route/basic") {
                val principal = call.principal<UserIdPrincipal>()!!
                call.respondText("Hello ${principal.name}")
            }

            get("/protected/route/form") {
                val principal = call.principal<UserIdPrincipal>()!!
                call.respondText("Hello ${principal.name}")
            }
        }
    }
}