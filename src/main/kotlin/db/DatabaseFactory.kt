package db



import Data.*
import kotlinx.coroutines.flow.combine
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineDatabase

import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.coroutine.CoroutineCollection


object DatabaseFactory {
    lateinit var database: CoroutineDatabase
    lateinit var client: CoroutineClient



    fun init() {
        client = KMongo.createClient().coroutine
        database = client.getDatabase("therapyApp")  // Replace with your actual DB name
    }

    fun getUsersCollection(): CoroutineCollection<UserSignUpRequest> {
        return database.getCollection("users")
    }

    fun getPreferencesCollection(): CoroutineCollection<UserPreferences> {
        return database.getCollection("userPreferences")
    }

    fun getTherapistDetailsCollection(): CoroutineCollection<TherapistDetails> {
        return database.getCollection("therapistDetails")
    }

    fun getProfilesCollection(): CoroutineCollection<UserProfile> {
        return database.getCollection("profiles")  // Collection for UserProfile
    }

    fun getChatCollection(): CoroutineCollection<ChatMessage> {
        return database.getCollection("chatMessages")
    }
    fun getConversationCollection(): CoroutineCollection<Conversation> {
        return database.getCollection<Conversation>("conversations")
    }

    fun getJournalCollection(): CoroutineCollection<ClientJournalEntry> {
        return database.getCollection("journalEntries")
    }

    fun getTherapistNotesCollection(): CoroutineCollection<TherapistNote> {
        return database.getCollection("therapistNotes")
    }

   /* fun getAppointmentsCollection(): CoroutineCollection<Appointment> {
        return database.getCollection("appointments") // Ensure this is set up in your DatabaseFactory
    }*/

    fun getSessionsCollection(): CoroutineCollection<TherapySession> {
        return database.getCollection("sessions") // Ensure this is set up in your DatabaseFactory
    }

}
