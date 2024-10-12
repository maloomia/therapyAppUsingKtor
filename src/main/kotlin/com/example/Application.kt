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

    // Configure WebSocket routes for chat
    fun Application.configureChatWebSockets(chatRepository: ConversationRepository) {
        routing {
            val conversationRepository = ConversationRepository() // Create an instance of ConversationRepository

            webSocket("/chat/{conversationId}") {  // Conversation ID passed as a parameter
                val conversationId = call.parameters["conversationId"]
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid conversation"))

                val userId = call.principal<UserIdPrincipal>()?.name
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Unauthorized"))

                // Fetch the conversation using conversationRepository instance
                val conversation = conversationRepository.getConversation(conversationId)
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Conversation not found"))

                // Listen for incoming WebSocket messages
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val receivedText = frame.readText()

                        // Decode the incoming message
                        val newMessage = Json.decodeFromString<ChatMessage>(receivedText)

                        // Add the message to the conversation and broadcast it to all participants
                        conversationRepository.addMessageToConversation(conversationId, newMessage)
                        outgoing.send(Frame.Text(Json.encodeToString(newMessage)))
                    }
                }
            }

        }
    }


    // Configure standard routing for the rest of the API
    configureRouting()


}



