package Data



import com.example.plugins.hashPassword
import com.mongodb.client.model.Filters
import db.DatabaseFactory
import org.bson.conversions.Bson
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.eq
import org.litote.kmongo.setValue

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
            println("Error finding user: ${e.message}") // Or use a logging framework
            null
        }
    }

    suspend fun resetUserPassword(userId: String, newPassword: String): Boolean {
        return try {
            val hashedPassword = hashPassword(newPassword)
            val updateResult = usersCollection.updateOne(
                UserSignUpRequest::userId eq userId,
                setValue(UserSignUpRequest::password, hashedPassword)
            )
            println("Modified count: ${updateResult.modifiedCount}")
            updateResult.wasAcknowledged()
        } catch (e: Exception) {
            println("Error during password reset: ${e.message}")
            false
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




    suspend fun searchTherapists(filters: SearchFilters): List<Map<String, Any?>> {
        val query = mutableListOf<Bson>()

        // Apply the name filter if provided
        filters.name?.let {
            filters.name?.let {
                query.add(UserSignUpRequest::name.regex(".*${it}.*", "i"))  // Case-insensitive search by name
            } // Case-insensitive search by name
        }

        // Apply specialties filter if provided
        filters.specialties?.let {
            query.add(TherapistDetails::specialty `in` it)
        }

        // Apply cost filter if provided
        filters.cost?.let {
            query.add(TherapistDetails::cost lte it)
        }

        // Apply availability filter if provided
        filters.availability?.let {
            query.add(TherapistDetails::availability eq it)
        }

        // Apply gender filter if provided
        filters.gender?.let {
            query.add(TherapistDetails::gender eq it)
        }

        // Execute the query and get the therapist details
        val therapistsDetails = therapistDetailsCollection.find(and(query)).toList()

        // Now, retrieve the associated name from the usersCollection
        return therapistsDetails.map { therapist ->
            val user = usersCollection.findOne(UserSignUpRequest::userId eq therapist.userId)

            // Log the fetched user
            println("Fetched user for userId ${therapist.userId}: $user")

            mapOf(
                "name" to user?.name,
                "details" to therapist
            )
        }}

















}