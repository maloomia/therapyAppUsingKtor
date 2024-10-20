package com.example.plugins

import Data.ChatMessage
import Data.ConversationRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

fun Application.configureChatWebSockets(conversationRepository: ConversationRepository) {
    val logger = LoggerFactory.getLogger("ChatWebSocket")
    val activeConnections = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    routing {
        authenticate("jwt") {
            webSocket("/chat/{conversationId}") {
                val conversationId = call.parameters["conversationId"]
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid conversation"))

                val userId = call.principal<UserIdPrincipal>()?.name
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Unauthorized"))

                val conversation = conversationRepository.getConversation(conversationId)
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Conversation not found"))

                logger.info("User $userId joined conversation $conversationId")

                // Add this connection to active connections
                activeConnections.getOrPut(conversationId) { mutableSetOf() }.add(this)

                try {
                    updateUserStatus(userId, true)
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val receivedText = frame.readText()
                                val messageData = Json.decodeFromString<Map<String, String>>(receivedText)

                                when (messageData["type"]) {
                                    "message" -> handleChatMessage(messageData, conversationId, userId, conversationRepository, activeConnections)
                                    "typing" -> handleTypingIndicator(messageData, conversationId, userId, activeConnections)
                                    else -> logger.warn("Unknown message type received")
                                }
                            }
                            else -> logger.warn("Frame type not supported: ${frame.frameType}")
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    logger.info("WebSocket connection closed for user $userId in conversation $conversationId")
                } catch (e: Exception) {
                    logger.error("Error in WebSocket connection: ${e.message}")
                } finally {
                    activeConnections[conversationId]?.remove(this)
                    if (activeConnections[conversationId]?.isEmpty() == true) {
                        activeConnections.remove(conversationId)
                    }
                    updateUserStatus(userId, false)
                }
            }
        }
    }
}

private suspend fun handleChatMessage(
    messageData: Map<String, String>,
    conversationId: String,
    userId: String,
    conversationRepository: ConversationRepository,
    activeConnections: ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>
) {
    val newMessage = ChatMessage(
        conversationId = conversationId,
        senderId = userId,
        receiverId = messageData["receiverId"] ?: return,
        content = messageData["content"] ?: return,
        timestamp = System.currentTimeMillis(),
        isFromTherapist = messageData["isFromTherapist"]?.toBoolean() ?: false,
        isRead = false // Initially set to false as it's a new message
    )

    val isAdded = conversationRepository.addMessageToConversation(conversationId, newMessage)
    val isSaved = conversationRepository.saveChatMessage(newMessage)

    if (isAdded && isSaved) {
        broadcastMessage(conversationId, Json.encodeToString(newMessage), activeConnections)
    }
}

private suspend fun handleTypingIndicator(
    messageData: Map<String, String>,
    conversationId: String,
    userId: String,
    activeConnections: ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>
) {
    val typingStatus = messageData["isTyping"]?.toBoolean() ?: return
    val typingMessage = mapOf(
        "type" to "typing",
        "userId" to userId,
        "isTyping" to typingStatus.toString()
    )
    broadcastMessage(conversationId, Json.encodeToString(typingMessage), activeConnections)
}

private suspend fun broadcastMessage(
    conversationId: String,
    message: String,
    activeConnections: ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>
) {
    activeConnections[conversationId]?.forEach { socket ->
        socket.send(Frame.Text(message))
    }
}

private fun updateUserStatus(userId: String, isOnline: Boolean) {
    // Implement user status update logic here
    // This could involve updating a database and notifying other users
    // For example:
    // userRepository.updateUserStatus(userId, isOnline)
    // broadcastUserStatus(userId, isOnline)
}
