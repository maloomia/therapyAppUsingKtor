package Data

import db.DatabaseFactory

import org.litote.kmongo.and
import org.litote.kmongo.eq
import org.litote.kmongo.gte
import org.litote.kmongo.lt
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.minutes

class SessionRepository {

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
}