import Data.Payment
import Data.PaymentStatus
import db.DatabaseFactory
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import org.bson.types.ObjectId

class PaymentRepository {
    private val paymentsCollection = DatabaseFactory.getPaymentsCollection()

    suspend fun processPayment(sessionId: String, amount: Double): Boolean {
        return try {
            val payment = Payment(
                paymentId = ObjectId().toString(),
                sessionId = sessionId,
                amount = amount,
                status = PaymentStatus.PAID
            )
            val result = paymentsCollection.insertOne(payment)
            result.wasAcknowledged()
        } catch (e: Exception) {
            println("Error processing payment: ${e.message}")
            false
        }
    }

    suspend fun refundPayment(sessionId: String): Boolean {
        return try {
            val result = paymentsCollection.updateOne(
                Payment::sessionId eq sessionId,
                setValue(Payment::status, PaymentStatus.REFUNDED)
            )
            result.modifiedCount > 0
        } catch (e: Exception) {
            println("Error refunding payment: ${e.message}")
            false
        }
    }

    suspend fun getPaymentBySessionId(sessionId: String): Payment? {
        return paymentsCollection.findOne(Payment::sessionId eq sessionId)
    }
}
