package Data



import com.example.plugins.hashPassword
import com.mongodb.client.model.Filters
import db.DatabaseFactory
import org.bson.conversions.Bson
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import org.slf4j.LoggerFactory

class UserRepository {
    private val usersCollection = DatabaseFactory.getUsersCollection()
    private val preferencesCollection = DatabaseFactory.getPreferencesCollection()
    private val therapistDetailsCollection = DatabaseFactory.getTherapistDetailsCollection()
    private val profilesCollection = DatabaseFactory.getProfilesCollection()  // Define profiles collection
    private val logger = LoggerFactory.getLogger(this::class.java)

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

    private fun parseAvailability(availabilityString: String): Triple<Int, String, String> {
        val parts = availabilityString.split(",")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid availability format")
        }
        val dayOfWeek = parts[0].toInt()
        val startTime = parts[1]
        val endTime = parts[2]
        return Triple(dayOfWeek, startTime, endTime)
    }


    suspend fun searchTherapists(filters: SearchFilters): List<Map<String, Any?>> {
        val query = mutableListOf<Bson>()

        // Apply the name filter if provided
        filters.name?.let {
            filters.name.let {
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
        filters.availability?.let { availabilityString ->
            logger.info("Applying availability filter: $availabilityString")
            val (dayOfWeek, startTime, endTime) = parseAvailability(availabilityString)

            query.add(
                TherapistDetails::availability.elemMatch(
                    and(
                        Availability::dayOfWeek eq dayOfWeek,
                        Availability::startTime lte startTime,
                        Availability::endTime gte endTime
                    )
                )
            )


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
        }
    }

    suspend fun countTherapists(): Long {
        val count = therapistDetailsCollection.countDocuments()
        logger.info("Total therapist count: $count")
        return count
    }

    suspend fun getAllTherapists(): List<TherapistSearchResult> {
        val therapistsDetails = therapistDetailsCollection.find().toList()
        logger.info("Found ${therapistsDetails.size} therapist details")

        return therapistsDetails.mapNotNull { therapist ->
            logger.info("Processing therapist with userId: ${therapist.userId}")
            val user = usersCollection.findOne(UserSignUpRequest::userId eq therapist.userId)

            if (user == null) {
                logger.warn("No user found for therapist with userId: ${therapist.userId}")
                return@mapNotNull null
            }

            try {
                TherapistSearchResult(
                    name = user.name,
                    qualifications = therapist.qualifications,
                    experience = therapist.experience,
                    specialty = therapist.specialty,
                    clientTypes = therapist.clientTypes ?: emptyList(),
                    issuesTreated = therapist.issuesTreated ?: emptyList(),
                    treatmentApproaches = therapist.treatmentApproaches ?: emptyList(),
                    availability = therapist.availability ?: emptyList(),
                    cost = therapist.cost ?: 0.0,
                    gender = therapist.gender ?: ""
                )
            } catch (e: Exception) {
                logger.error("Error creating TherapistSearchResult for userId: ${therapist.userId}", e)
                null
            }
        }.also { results ->
            logger.info("Returning ${results.size} TherapistSearchResults")
        }
    }


    suspend fun getTherapistDetails(userId: String): TherapistDetails? {
        println("Looking for therapist with userId: $userId")
        val therapistCollection = DatabaseFactory.getTherapistDetailsCollection()
        val therapist = therapistCollection.findOne(TherapistDetails::userId eq userId.trim())

        if (therapist == null) {
            println("No therapist found for userId: $userId")
        } else {
            println("Therapist found: ${therapist.userId}")
        }
        return therapist
    }







}
