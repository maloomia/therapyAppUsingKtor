package com.example

import Data.ChatMessage
import Data.ConversationRepository
import Data.UserRepository
import Data.UserSignUpRequest
import com.example.plugins.*
import db.DatabaseFactory

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import org.slf4j.event.Level

// WebSocket imports

import io.ktor.websocket.*

// Routing
import io.ktor.server.routing.*

// Authentication and session management
import io.ktor.server.auth.*
import io.ktor.server.websocket.*

// JSON handling
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// MongoDB (KMongo) related
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.eq

// Coroutines support
import kotlinx.coroutines.*

// Other

import org.bson.types.ObjectId

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080) {
        module()  // This is where the application modules (routes, DB connection, etc.) are configured
    }.start(wait = true)
}



fun Application.module() {
    configureSerialization()
    configureHTTP()
    configureMonitoring()
    configureSecurity()  // Ensure this is called before configureRouting
    DatabaseFactory.init()

    if (this.pluginOrNull(CallLogging) == null) {
        install(CallLogging) {
            level = Level.TRACE
        }
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val conversationRepository = ConversationRepository() // Make sure you have the repository ready
    configureChatWebSockets(conversationRepository)


    configureRouting()


}



