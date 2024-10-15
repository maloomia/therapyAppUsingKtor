package Data

import db.DatabaseFactory
import org.litote.kmongo.and
import org.litote.kmongo.eq

class TherapistNoteRepository {
    private val notesCollection = DatabaseFactory.getTherapistNotesCollection()

    // Save a therapist note
    suspend fun saveTherapistNote(note: TherapistNote): Boolean {
        val insertResult = notesCollection.insertOne(note)
        return insertResult.wasAcknowledged()
    }

    // Fetch notes connected to a user (diagnosis or general treatment notes)
    suspend fun getNotesForUser(userId: String, therapistId: String): List<TherapistNote> {
        return notesCollection.find(and(TherapistNote::userId eq userId, TherapistNote::therapistId eq therapistId)).toList()
    }


}
