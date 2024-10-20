package com.example.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*



import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("jwt") {
            realm = "Access to protected routes"
            verifier(JWT.require(Algorithm.HMAC256("your-secret-key")).build())
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val username = credential.payload.getClaim("username").asString()
                if (userId != null && username != null) {
                    UserIdPrincipal(userId)
                } else {
                    null
                }
            }
        }
    }
}



