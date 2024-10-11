package Data

object constants {
    // Constants.kt
    object Constants {
        const val BASE_URL = "localhost:8080"
        const val STATIC_ROOT = "static/"
        const val CERTIFICATE_IMAGE_DIRECTORY = "uploads/certificates/"
        const val PROFILE_PICTURE_DIRECTORY = "uploads/profile_pictures/"
        const val CERTIFICATE_IMAGE_PATH = "$STATIC_ROOT/$CERTIFICATE_IMAGE_DIRECTORY"
        const val EXTERNAL_CERTIFICATE_IMAGE_PATH = "/images"
    }
}