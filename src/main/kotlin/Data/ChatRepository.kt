package Data

import db.DatabaseFactory
import org.bson.types.ObjectId
import org.litote.kmongo.and

import org.litote.kmongo.eq
import org.litote.kmongo.or
import org.litote.kmongo.push
import com.mongodb.client.model.Updates.set
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import org.litote.kmongo.coroutine.updateOne
import com.mongodb.client.model.Updates.set
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import org.litote.kmongo.coroutine.updateOne
import org.litote.kmongo.coroutine.CoroutineCollection


class ConversationRepository {
    private val conversationCollection = DatabaseFactory.getConversationCollection()
    private val chatCollection = DatabaseFactory.getChatCollection()

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

    suspend fun startConversation(userId: String, therapistId: String): String? {
        try {
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
        } catch (e: Exception) {
            // Handle any database or other errors
            println("Error starting conversation: ${e.message}")
            return null
        }
    }




    suspend fun getConversationsForUser(userId: String): List<Conversation> {
        return conversationCollection.find(
            or(Conversation::userId eq userId, Conversation::therapistId eq userId)
        ).toList()
    }

    suspend fun getChatHistory(conversationId: String, limit: Int, offset: Int): List<ChatMessage> {
        val conversation = conversationCollection.findOne(Conversation::conversationId eq conversationId)

        return conversation?.messages?.drop(offset)?.take(limit) ?: emptyList()
    }


    suspend fun endConversation(conversationId: String): Boolean {
        return conversationCollection.updateOne(
            Conversation::conversationId eq conversationId,
            setValue(Conversation::isActive, false)
        ).modifiedCount > 0
    }



    suspend fun saveChatMessage(chatMessage: ChatMessage): Boolean {
        val insertResult = chatCollection.insertOne(chatMessage)
        return insertResult.wasAcknowledged()
    }

    // Add a message to a conversation

}


