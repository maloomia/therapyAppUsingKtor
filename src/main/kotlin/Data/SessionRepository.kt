package Data

import db.DatabaseFactory
import org.bson.types.ObjectId
import org.litote.kmongo.*

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class SessionRepository {

    private val sessionsCollection = DatabaseFactory.getSessionsCollection()
    // Method to check if a therapist is available at the requested time
   /* suspend fun isTherapistAvailable(therapistId: String, requestedTime: String): Boolean {
        val therapistCollection = DatabaseFactory.getTherapistDetailsCollection()
        val therapist = therapistCollection.findOne(TherapistDetails::userId eq therapistId)


        // Assuming availability is stored as a list of time slots, check if requestedTime matches any available slot
        return therapist?.availability?.contains(requestedTime) ?: false
    }*/

    // Method to book an appointment


    /*suspend fun bookAppointment(appointment: Appointment): Boolean {
        val appointmentCollection = DatabaseFactory.getAppointmentsCollection() // Use the correct collection for appointments
        return try {
            appointmentCollection.insertOne(appointment)
            true // Booking successful
        } catch (e: Exception) {
            println("Error booking appointment: ${e.message}")
            false // Booking failed
        }
    }*/

    suspend fun checkTherapistAvailability(
        therapistId: String,
        sessionDateTime: String,
        duration: Int
    ): Boolean {
        val sessionStart = Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(sessionDateTime))
        val sessionEnd = sessionStart.plus(duration.toLong(), ChronoUnit.MINUTES)

        val sessionsCollection = DatabaseFactory.getSessionsCollection()

        val existingSessions = sessionsCollection.find(
            and(
                TherapySession::therapistId eq therapistId,
                TherapySession::sessionDateTime gte sessionStart.toString(),
                TherapySession::sessionDateTime lt sessionEnd.toString()
            )
        ).toList()

        return existingSessions.isEmpty()
    }




   /* fun calculateCost(costPerMinute: Double, duration: Int): Double {
        return costPerMinute * duration
    }

    // Additional methods related to session management*/

    suspend fun getSessionById(sessionId: String): TherapySession? {
        return try {
            sessionsCollection.findOneById(sessionId)
        } catch (e: Exception) {
            println("Invalid sessionId format: ${e.message}")
            null
        }
    }




    suspend fun cancelSession(sessionId: String): Boolean {
        // Update the session status to CANCELLED
        val result = sessionsCollection.updateOneById(
            sessionId,
            setValue(TherapySession::status, SessionStatus.CANCELLED)
        )

        return result.matchedCount > 0  // Return true if a session was updated
    }

    suspend fun logCancellation(session: TherapySession) {
        // Implementation for logging cancellation details (to a file, database, etc.)
        println("Session ${session.sessionId} cancelled for client ${session.clientId} by therapist ${session.therapistId}.")
    }


    fun notifyTherapist(therapistId: String, sessionId: String) {
        // Implementation to send notification (email, SMS, etc.)
        println("Notified therapist $therapistId about the cancellation of session $sessionId.")
    }
    
     suspend fun updatePaymentStatus(sessionId: String, status: PaymentStatus): Boolean {
        val result = sessionsCollection.updateOneById(
            sessionId,
            setValue(TherapySession::paymentStatus, status)
        )
        return result.modifiedCount > 0
    }

    suspend fun isSessionPaidAndActive(sessionId: String): Boolean {
        val session = getSessionById(sessionId)
        return session?.paymentStatus == PaymentStatus.PAID && 
               session.status == SessionStatus.SCHEDULED &&
               Instant.parse(session.sessionDateTime).isBefore(Instant.now())
    }
}



