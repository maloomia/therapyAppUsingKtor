package Data



import db.DatabaseFactory
import org.litote.kmongo.combine
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import org.litote.kmongo.coroutine.*

class UserRepository {
    private val usersCollection = DatabaseFactory.getUsersCollection()
    private val preferencesCollection = DatabaseFactory.getPreferencesCollection()
    private val therapistDetailsCollection = DatabaseFactory.getTherapistDetailsCollection()
    private val profilesCollection = DatabaseFactory.getProfilesCollection()  // Define profiles collection

    suspend fun createUser(user: UserSignUpRequest): Boolean {
        val insertResult = usersCollection.insertOne(user)
        return insertResult.wasAcknowledged()
    }

    suspend fun findByUsername(username: String): UserSignUpRequest? {
        return try {
            usersCollection.findOne(UserSignUpRequest::username eq username)
        } catch (e: Exception) {
            // Log the error if needed
            null // Return null in case of an error
        }
    }


    suspend fun createUserPreferences(preferences: UserPreferences): Boolean {
        val insertResult = preferencesCollection.insertOne(preferences)
        return insertResult.wasAcknowledged()
    }

    suspend fun createTherapistDetails(therapistDetails: TherapistDetails): Boolean {
        val insertResult = therapistDetailsCollection.insertOne(therapistDetails)
        return insertResult.wasAcknowledged()
    }

    suspend fun createUserProfile(profile: UserProfile): Boolean {
        val insertResult = profilesCollection.insertOne(profile)
        return insertResult.wasAcknowledged()
    }

    suspend fun updateTherapistDetails(userId: String, therapistDetails: TherapistDetails): Boolean {
        val updateResult = therapistDetailsCollection.updateOne(
            TherapistDetails::userId eq userId,
            combine(
                setValue(TherapistDetails::experience, therapistDetails.experience),
                setValue(TherapistDetails::specialty, therapistDetails.specialty),
                setValue(TherapistDetails::clientTypes, therapistDetails.clientTypes),
                setValue(TherapistDetails::issuesTreated, therapistDetails.issuesTreated),
                setValue(TherapistDetails::treatmentApproaches, therapistDetails.treatmentApproaches)
            )
        )
        return updateResult.modifiedCount > 0
    }

    suspend fun updateUserProfile(userId: String, userProfile: UserProfile): Boolean {
        val updateResult = profilesCollection.updateOne(
            UserSignUpRequest::userId eq userId,
            combine(
                setValue(UserProfile::profilePicturePath, userProfile.profilePicturePath),
                setValue(UserProfile::bio, userProfile.bio)
            )
        )
        return updateResult.wasAcknowledged()
    }
}