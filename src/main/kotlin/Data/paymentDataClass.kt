package Data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Payment(
    @SerialName("_id")
    val paymentId: String = ObjectId().toHexString(),
    val sessionId: String,
    val amount: Double,
    val status: PaymentStatus
)


enum class PaymentStatus {
    PENDING, PAID, REFUNDED
}