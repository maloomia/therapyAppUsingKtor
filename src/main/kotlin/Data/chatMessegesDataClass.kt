package Data

import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class ChatMessage(
    val messageId: String = ObjectId().toHexString(),
    val conversationId: String,
    val senderId: String,    // ID of the sender
    val receiverId: String,  // ID of the receiver
    val content: String,     // Message content
    val timestamp: Long = System.currentTimeMillis(), // Timestamp of message
    val isFromTherapist: Boolean,
    val isRead: Boolean = false
)

@Serializable
data class Conversation(
    val conversationId: String = ObjectId().toHexString(),
    val userId: String,       // ID of the user (non-therapist)
    val therapistId: String,  // ID of the therapist
    val messages: MutableList<ChatMessage> = mutableListOf(), // List of messages in the conversation
    val isActive: Boolean = true // Field to track if the conversation is still active
)


@Serializable
data class StartConversationRequest(
    val therapistId: String // The ID of the therapist to start the conversation with
)
