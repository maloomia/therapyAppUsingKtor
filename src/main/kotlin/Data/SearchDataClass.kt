package Data

import kotlinx.serialization.Serializable

@Serializable
data class SearchFilters(
    val name: String? = null,
    val specialties: List<String>?= null,
    val cost: Double?= null,
    val availability: String?= null,
    val gender: String?= null
)

@Serializable
data class TherapistSearchResult(
    val name: String,
    val qualifications: String?,
    val experience: Int?,
    val specialty: String,
    val clientTypes: List<String>,
    val issuesTreated: List<String>,
    val treatmentApproaches: List<String>,
    val availability: List<Availability>,
    val cost: Double,
    val gender: String
)

