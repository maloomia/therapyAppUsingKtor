package Data

import kotlinx.serialization.Serializable


@Serializable
data class LoginRequest(

    val username: String,
    val password: String
)

@Serializable
data class ResetPasswordRequest(
    val userId: String,
    val newPassword: String
)
