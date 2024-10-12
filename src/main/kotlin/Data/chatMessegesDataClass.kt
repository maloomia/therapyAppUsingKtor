package Data

import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class ChatMessage(
    val messageId: String = ObjectId().toHexString(),
    val senderId: String,    // ID of the sender
    val receiverId: String,  // ID of the receiver
    val content: String,     // Message content
    val timestamp: Long = System.currentTimeMillis(), // Timestamp of message
    val isFromTherapist: Boolean // True if message is from therapist, false if from user
)

@Serializable
data class Conversation(
    val conversationId: String = ObjectId().toHexString(),
    val userId: String,       // ID of the user (non-therapist)
    val therapistId: String,  // ID of the therapist
    val messages: MutableList<ChatMessage> = mutableListOf() // List of messages in the conversation
)


@Serializable
data class StartConversationRequest(
    val therapistId: String // The ID of the therapist to start the conversation with
)
