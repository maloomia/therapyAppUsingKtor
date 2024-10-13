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

