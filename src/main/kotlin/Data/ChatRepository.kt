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
    suspend fun addMessageToConversation(conversationId: String, message: ChatMessage): Boolean {
        val updateResult = conversationCollection.updateOne(
            Conversation::conversationId eq conversationId,
            push(Conversation::messages, message)
        )
        return updateResult.modifiedCount > 0
    }





    suspend fun startConversation(userId: String, therapistId: String, sessionId: String): String? {
        try {
            // First, check if the session is paid and active
            val sessionRepository = SessionRepository()
            if (!sessionRepository.isSessionPaidAndActive(sessionId)) {
                println("Session is not paid or active for sessionId: $sessionId")
                return null
            }

            // Check if a conversation between the user and therapist already exists for this session
            val existingConversation = conversationCollection.findOne(
                and(
                    Conversation::userId eq userId,
                    Conversation::therapistId eq therapistId,
                    Conversation::sessionId eq sessionId
                )
            )

            // If a conversation exists, return its ID without creating a new one
            if (existingConversation != null) {
                return existingConversation.conversationId
            }

            // Otherwise, create a new conversation
            val conversationId = ObjectId().toHexString()

            val newConversation = Conversation(
                conversationId = conversationId,
                userId = userId,
                therapistId = therapistId,
                sessionId = sessionId
            )

            // Save the conversation to the database and ensure it was acknowledged
            val insertResult = conversationCollection.insertOne(newConversation)
            return if (insertResult.wasAcknowledged()) {
                conversationId  // Return the newly created conversation ID
            } else {
                null  // Insert failed, return null
            }
        } catch (e: Exception) {
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
        val conversationId = chatMessage.conversationId  // Ensure correct conversationId is passed

        // Check if the exact message already exists in the chat collection based on messageId or other unique fields
        val existingMessage = chatCollection.findOne(ChatMessage::messageId eq chatMessage.messageId)

        if (existingMessage != null) {
            println("Message already exists in chatCollection with ID: ${chatMessage.messageId}")
            return false  // Skip saving duplicate message
        }

        // Insert the new message into the chat collection
        val insertResult = chatCollection.insertOne(chatMessage)

        // Check if the conversation exists before adding the message
        val existingConversation = conversationCollection.findOne(Conversation::conversationId eq conversationId)

        if (existingConversation == null) {
            println("Conversation not found with ID: $conversationId")
            return false
        }

        // Append the message to the conversation's messages array
        if (insertResult.wasAcknowledged()) {
            val updateResult = conversationCollection.updateOne(
                Conversation::conversationId eq conversationId,
                push(Conversation::messages, chatMessage)
            )

            return updateResult.modifiedCount > 0
        }

        return false
    }



    suspend fun deleteOldConversation(userId: String, therapistId: String): Boolean {
        try {
            val existingConversation = conversationCollection.findOne(
                and(Conversation::userId eq userId, Conversation::therapistId eq therapistId)
            )

            if (existingConversation != null) {
                // Delete the old conversation if it exists
                val deleteResult = conversationCollection.deleteOne(
                    Conversation::conversationId eq existingConversation.conversationId
                )
                return deleteResult.deletedCount > 0
            }
            return false  // No conversation to delete
        } catch (e: Exception) {
            println("Error deleting old conversation: ${e.message}")
            return false
        }
    }





}



