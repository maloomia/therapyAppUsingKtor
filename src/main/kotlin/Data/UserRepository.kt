package Data



import db.DatabaseFactory
import org.bson.conversions.Bson
import org.litote.kmongo.*
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
            println("Error finding user: ${e.message}") // Or use a logging framework
            null
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



    suspend fun searchTherapists(filters: SearchFilters): List<TherapistDetails> {
        val query = mutableListOf<Bson>()

        filters.specialties?.let {
            query.add(TherapistDetails::specialty `in` it)
        }
        filters.cost?.let {
            query.add(TherapistDetails::cost lte it)
        }
        filters.availability?.let {
            query.add(TherapistDetails::availability eq it)
        }
        filters.gender?.let {
            query.add(TherapistDetails::gender eq it)
        }

        return therapistDetailsCollection.find(and(query)).toList()
    }

    private val chatCollection = DatabaseFactory.getChatCollection()

    suspend fun saveChatMessage(chatMessage: ChatMessage): Boolean {
        val insertResult = chatCollection.insertOne(chatMessage)
        return insertResult.wasAcknowledged()
    }

    suspend fun getChatHistory(userId: String): List<ChatMessage> {
        return chatCollection.find(or(ChatMessage::senderId eq userId, ChatMessage::receiverId eq userId)).toList()
    }





}