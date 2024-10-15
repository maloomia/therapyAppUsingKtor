package Data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/*@Serializable
data class Appointment(
    @SerialName("_id")
    val appointmentId: String = ObjectId().toHexString(),
    val therapistId: String, // Simplified to only take therapist userId
    val clientId: String,
    val dateTime: String,
    val duration: Int,
    var totalCost: Double,
    val status: String
)*/


@Serializable
data class TherapySession(
    @SerialName("_id")
    val sessionId: String = ObjectId().toHexString(), // Unique identifier for the session
    val clientId: String, // ID of the client
    val therapistId: String, // ID of the therapist
    val sessionDateTime: String, // Scheduled date and time of the session (ISO 8601 format)
    val duration: Int, // Duration in minutes
    val status: SessionStatus, // Status of the session (e.g., Scheduled, Completed, Cancelled)
    val cost: Double // Cost of the therapy session
)

enum class SessionStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED
}


@Serializable
data class BookSessionRequest(
    val clientId: String, // ID of the client
    val therapistId: String, // ID of the therapist
    val sessionDateTime: String, // Desired date and time for the session
    val duration: Int, // Duration in minutes
    val cost: Double // Cost of the session
)

