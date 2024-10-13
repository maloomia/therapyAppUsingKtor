package com.example.plugins

import Data.ChatMessage
import Data.ConversationRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*



import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.slf4j.LoggerFactory

fun Application.configureChatWebSockets(conversationRepository: ConversationRepository) {
    val logger = LoggerFactory.getLogger("ChatWebSocket")

    routing {
        webSocket("/chat/{conversationId}") {  // Conversation ID passed as a parameter
            val conversationId = call.parameters["conversationId"]
                ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid conversation"))

            val userId = call.principal<UserIdPrincipal>()?.name
                ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Unauthorized"))

            // Fetch the conversation
            val conversation = conversationRepository.getConversation(conversationId)
                ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Conversation not found"))

            logger.info("User $userId joined conversation $conversationId")

            // Listen for incoming WebSocket frames (messages)
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val receivedText = frame.readText()

                    // Decode the incoming message
                    val newMessage = Json.decodeFromString<ChatMessage>(receivedText)

                    // Add the message to the conversation and broadcast it
                    conversationRepository.addMessageToConversation(conversationId, newMessage)

                    // Broadcast the new message to both participants
                    outgoing.send(Frame.Text(Json.encodeToString(newMessage)))
                }
            }
        }


    }


}
