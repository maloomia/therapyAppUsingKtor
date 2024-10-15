package Data

import db.DatabaseFactory
import org.litote.kmongo.and
import org.litote.kmongo.eq

class NotesRepository {
    private val journalCollection = DatabaseFactory.getJournalCollection()
    private val therapistNotesCollection = DatabaseFactory.getTherapistNotesCollection()

    // For client journal
    suspend fun addJournalEntry(entry: ClientJournalEntry): Boolean {
        val insertResult = journalCollection.insertOne(entry)
        return insertResult.wasAcknowledged()
    }

    suspend fun getJournalEntries(userId: String): List<ClientJournalEntry> {
        return journalCollection.find(ClientJournalEntry::userId eq userId).toList()
    }

    // For therapist notes
    suspend fun addTherapistNote(note: TherapistNote): Boolean {
        val insertResult = therapistNotesCollection.insertOne(note)
        return insertResult.wasAcknowledged()
    }

    suspend fun getTherapistNotes(therapistId: String, userId: String): List<TherapistNote> {
        return therapistNotesCollection.find(
            and(TherapistNote::therapistId eq therapistId, TherapistNote::userId eq userId)
        ).toList()
    }

    suspend fun getPersonalNotes(therapistId: String): List<TherapistNote> {
        return therapistNotesCollection.find(TherapistNote::therapistId eq therapistId).toList()
    }
}
