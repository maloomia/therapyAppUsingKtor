package Data

import db.DatabaseFactory
import org.bson.types.ObjectId
import org.litote.kmongo.and

import org.litote.kmongo.eq
import org.litote.kmongo.push

class ConversationRepository {
    private val conversationCollection = DatabaseFactory.getConversationCollection()

    // Fetch a conversation by ID
    suspend fun getConversation(conversationId: String): Conversation? {
        return conversationCollection.findOne(Conversation::conversationId eq conversationId)
    }

    // Add a message to a conversation
    suspend fun addMessageToConversation(conversationId: String, message: ChatMessage) {
        conversationCollection.updateOne(
            Conversation::conversationId eq conversationId,
            push(Conversation::messages, message)
        )
    }

    suspend fun startConversation(userId: String, therapistId: String): String {
        // Check if a conversation between the user and therapist already exists
        val existingConversation = conversationCollection.findOne(
            and(Conversation::userId eq userId, Conversation::therapistId eq therapistId)
        )

        // If a conversation exists, return its ID
        if (existingConversation != null) {
            return existingConversation.conversationId
        }

        // Otherwise, create a new conversation
        val conversationId = ObjectId().toHexString()

        val newConversation = Conversation(
            conversationId = conversationId,
            userId = userId,
            therapistId = therapistId
        )

        // Save the conversation to the database
        conversationCollection.insertOne(newConversation)

        return conversationId // Return the newly created conversation ID
    }

    // Add a message to a conversation

}


