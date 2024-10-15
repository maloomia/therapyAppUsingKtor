package Data

import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class ClientJournalEntry(
    val entryId: String = ObjectId().toHexString(),
    val userId: String,
    val moodIcon: MoodIcon,
    val date: String,
    val hour: String,
    val emotions: List<String>,
    val title: String,
    val content: String
)







@Serializable
data class TherapistNote(
    val noteId: String = ObjectId().toHexString(), // Optionally generated
    val therapistId: String, // Required for all notes
    val userId: String?, // Nullable for some routes
    val diagnosis: String? = null, // Optional
    val generalNotes: String? = null, // Optional
    val personalNotes: String? = null // Nullable to allow flexibility
)


@Serializable
enum class MoodIcon {
    HAPPY, SAD, ANGRY, NEUTRAL, ANXIOUS
}
