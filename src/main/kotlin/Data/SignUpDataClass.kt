package Data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId


@Serializable
data class UserSignUpRequest(
    @SerialName("_id")
    val userId: String = ObjectId().toHexString(),
    val name: String,
    val username: String,
    val email: String,
    val password: String,
    val isTherapist: Boolean // Indicates if the user is a therapist
)

@Serializable
data class UserPreferences(
    @SerialName("_id")
    val userId: String = ObjectId().toHexString(),// To link preferences to the user
    val sessionType: String, // "جلسة انفرادية"، "جلسة للأزواج"، "لطفلي", "عائلية"
    val gender: String, // "Female" or "Male"
    val therapistPreference: String, // "Male", "Female", "No preference"
    val topics: List<String>, // List of topics like "اكتئاب", "اضطرابات القلق"
    val languagePreference: String // "English" or "Arabic"
)

@Serializable
data class Certificate(
    val path: String, // Path to the uploaded certificate
    val uploadDate: String // Date when the certificate was uploaded
)

@Serializable
data class Availability(
    val dayOfWeek: Int, // 1 (Monday) to 7 (Sunday)
    val startTime: String, // Format: "HH:mm"
    val endTime: String // Format: "HH:mm"
)


@Serializable
data class TherapistDetails(
    @SerialName("_id")
    val userId: String = ObjectId().toHexString(),
    // To link therapist-specific data to the user
    val certificate: Certificate?, // Path to the uploaded certificate
    val qualifications: String?, // Optional: Qualifications or certifications
    val experience: Int?, // Optional: Years of experience
    val specialty: String, // Therapist's specialty (e.g., anxiety, trauma, etc.)
    val clientTypes: List<String>, // Types of clients they work with (e.g., children, couples, families)
    val issuesTreated: List<String>, // Issues they work with (e.g., anxiety disorders, depression)
    val treatmentApproaches: List<String>, // Approaches they use in therapy (e.g., CBT, DBT)
   
    val cost: Double, // Cost of the therapy session
  val availability: List<Availability>,
    val gender: String // Gender of the therapist
)


@Serializable
data class UserProfile(
    @SerialName("_id")
    val userId: String = ObjectId().toHexString(), // To link the profile with the user
    val profilePicturePath: String? = null, // Optional profile picture path
    val bio: String? = null, // Optional user bio
    val name: String // User's name
)


