package com.example.plugins
import Data.constants.Constants
import Data.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import db.DatabaseFactory
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject


val json = Json {
    ignoreUnknownKeys = true
}

fun hashPassword(password: String): String {
    return BCrypt.withDefaults().hashToString(12, password.toCharArray())
}

fun isValidPassword(password: String): Boolean {
    // Example validation: Password must be at least 8 characters long and contain at least one digit.
    return password.length >= 8 && password.any { it.isDigit() } && password.any { it.isLetter() }
}


fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }



        // Import the Constants object


        post("/signup") {
            try {
                val multipart = call.receiveMultipart()
                var userRequest: UserSignUpRequest? = null
                var userPreferences: UserPreferences? = null
                var certificateFile: String? = null
                var therapistDetails: TherapistDetails? = null
                var userProfile: UserProfile? = null
                var profilePictureFile: String? = null

                // Define the directory for certificate uploads using Constants
                val directory = File(Constants.CERTIFICATE_IMAGE_DIRECTORY)
                if (!directory.exists()) {
                    directory.mkdirs() // Create the directory if it doesn't exist
                }

                val profilePictureDirectory = File(Constants.PROFILE_PICTURE_DIRECTORY)
                if (!profilePictureDirectory.exists()) {
                    profilePictureDirectory.mkdirs() // Create the directory if it doesn't exist
                }

                try {
                    // Process multipart form data
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                when (part.name) {
                                    "userData" -> userRequest = json.decodeFromString(part.value)
                                    "preferences" -> if (userRequest?.isTherapist == false) {
                                        userPreferences = json.decodeFromString(part.value)
                                    }
                                    "profile" -> userProfile = json.decodeFromString(part.value)
                                    "therapistDetails" -> therapistDetails = json.decodeFromString(part.value)
                                }
                            }
                            is PartData.FileItem -> {
                                // Handle certificate upload for therapists
                                if (part.name == "certificate" && userRequest?.isTherapist == true) {
                                    certificateFile = part.save(Constants.CERTIFICATE_IMAGE_DIRECTORY)
                                }

                                if (part.name == "profilePicture") {
                                    profilePictureFile = part.save(Constants.PROFILE_PICTURE_DIRECTORY)
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Error saving certificate file: ${ex.message}")
                    return@post
                }

                // Check if mandatory fields are null
                if (userRequest == null || userProfile == null) {
                    log.error("Invalid signup data: userRequest: $userRequest, userPreferences: $userPreferences, userProfile: $userProfile")
                    call.respond(HttpStatusCode.BadRequest, "Invalid sign-up data")
                    return@post
                }

                if (userRequest!!.isTherapist == false && userPreferences == null) {
                    log.error("Invalid signup data: userPreferences: $userPreferences")
                    call.respond(HttpStatusCode.BadRequest, "Invalid sign-up data")
                    return@post
                }

                // Validate password and confirm password
                if (userRequest!!.password != userRequest!!.confirmPassword) {
                    log.error("Passwords do not match: ${userRequest!!.password}, ${userRequest!!.confirmPassword}")
                    call.respond(HttpStatusCode.BadRequest, "Passwords do not match")
                    return@post
                }

                // Validate password strength (optional)
                if (!isValidPassword(userRequest!!.password)) {
                    log.error("Weak password: ${userRequest!!.password}")
                    call.respond(HttpStatusCode.BadRequest, "Password is too weak")
                    return@post
                }

                // Hash the password before storing
                val hashedPassword = hashPassword(userRequest!!.password)

                // Create user instance with hashed password
                val userToStore = userRequest!!.copy(password = hashedPassword, confirmPassword = null.toString()) // Do not store confirmPassword

                // Set userId in preferences and profile before saving
                val profileToStore = userProfile!!.copy(userId = userToStore.userId, profilePicturePath = profilePictureFile)

                // If the user is a therapist, handle therapist-specific details
                if (userRequest!!.isTherapist) {
                    if (certificateFile == null) {
                        log.error("Therapist must upload a certificate")
                        call.respond(HttpStatusCode.BadRequest, "Therapist must upload a certificate")
                        return@post
                    }

                    val certificate = Certificate(
                        path = certificateFile!!,
                        uploadDate = Date().toString()
                    )

                    // Set therapist-specific details
                    therapistDetails = therapistDetails?.copy(
                        userId = userToStore.userId,
                        certificate = certificate, // Save the path to the certificate file
                        cost = therapistDetails!!.cost,
                        availability = therapistDetails!!.availability,
                        gender = therapistDetails!!.gender
                    )
                } else {
                    // Set userId in preferences before saving
                    userPreferences = userPreferences!!.copy(userId = userToStore.userId)
                }

                // Save user data to repository
                val userRepository = UserRepository()
                try {
                    // Store the user, profile, and preferences
                    userRepository.createUser(userToStore)
                    userRepository.createUserProfile(profileToStore)

                    if (!userRequest!!.isTherapist) {
                        userRepository.createUserPreferences(userPreferences!!)
                    }

                    // If the user is a therapist, save therapist details
                    if (userRequest!!.isTherapist && therapistDetails != null) {
                        userRepository.createTherapistDetails(therapistDetails!!)
                    }

                    // Respond with success
                    call.respond(HttpStatusCode.Created, "User signed up successfully")
                } catch (e: Exception) {
                    log.error("Error during signup process", e)
                    call.respond(HttpStatusCode.InternalServerError, "Server error: ${e.message}")
                }
            } catch (e: Exception) {
                log.error("Error during signup process", e)
                call.respond(HttpStatusCode.InternalServerError, "Server error: ${e.message}")
            }
        }

        authenticate("jwt") {
            put("/profile/info") {
                val user = call.principal<UserIdPrincipal>()
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
                    return@put
                }

                val userId = user.name
                val multipart = call.receiveMultipart()
                var updatedProfile: UserProfile? = null
                var profilePictureFile: String? = null

                try {
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                if (part.name == "profileData") {
                                    updatedProfile = json.decodeFromString(part.value)
                                }
                            }
                            is PartData.FileItem -> {
                                if (part.name == "profilePicture") {
                                    profilePictureFile = part.save(Constants.PROFILE_PICTURE_DIRECTORY)
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }
                } catch (ex: SerializationException) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid JSON format: ${ex.message}")
                    return@put
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Error processing request: ${ex.message}")
                    return@put
                }

                if (updatedProfile == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid profile data")
                    return@put
                }

                val userRepository = UserRepository() // Consider dependency injection here
                val profileToStore = updatedProfile!!.copy(userId = userId, profilePicturePath = profilePictureFile)

                val updatedSuccessfully = userRepository.updateUserProfile(userId, profileToStore)

                if (updatedSuccessfully) {
                    call.respond(HttpStatusCode.OK, "Profile updated successfully")
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Error updating profile")
                }
            }
        }
        authenticate("jwt") {
            put("/profile/therapistDetails") {
                log.info("Accessing therapist details")
                val user = call.principal<UserIdPrincipal>()
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
                    return@put
                }

                val userId = user.name
                val multipart = call.receiveMultipart()
                var therapistDetails: TherapistDetails? = null
                var userProfile: UserProfile? = null
                var profilePictureFile: String? = null

                val json = Json {
                    ignoreUnknownKeys = true
                }

                try {
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                when (part.name) {
                                    "therapistDetails" -> {
                                        val jsonString = part.value
                                        val jsonObject = json.parseToJsonElement(jsonString).jsonObject
                                        val modifiedJsonObject = jsonObject.toMutableMap().apply {
                                            remove("certificate")
                                        }
                                        therapistDetails = json.decodeFromJsonElement(JsonObject(modifiedJsonObject))
                                    }
                                    "userProfile" -> userProfile = json.decodeFromString(part.value)
                                }
                            }
                            is PartData.FileItem -> {
                                if (part.name == "profilePicture") {
                                    profilePictureFile = part.save(Constants.PROFILE_PICTURE_DIRECTORY)
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }
                } catch (ex: SerializationException) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid JSON format: ${ex.message}")
                    return@put
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error processing request: ${ex.message}")
                    return@put
                }

                if (therapistDetails == null || userProfile == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid therapist details or user profile")
                    return@put
                }

                val userRepository = UserRepository()
                val updatedTherapistDetails = therapistDetails!!.copy(userId = userId, profilePicturePath = profilePictureFile)
                val updatedUserProfile = userProfile!!.copy(userId = userId, profilePicturePath = profilePictureFile)

                val therapistUpdatedSuccessfully = userRepository.updateTherapistDetails(userId, updatedTherapistDetails)
                val profileUpdatedSuccessfully = userRepository.updateUserProfile(userId, updatedUserProfile)

                if (therapistUpdatedSuccessfully && profileUpdatedSuccessfully) {
                    call.respond(HttpStatusCode.OK, "Therapist details and user profile updated successfully")
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Error updating therapist details or user profile")
                }
            }
        }
        post("/login") {
            try {
                val loginRequest = call.receive<LoginRequest>()

                val userRepository = UserRepository()
                val user = userRepository.findByUsername(loginRequest.username)

                if (user == null) {
                    log.warn("User not found for username: ${loginRequest.username}")
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                    return@post
                }

                // Verify the hashed password with the provided password
                if (!BCrypt.verifyer().verify(loginRequest.password.toCharArray(), user.password).verified) {
                    log.warn("Password verification failed for user: ${loginRequest.username}")
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                    return@post
                }

                // Generate JWT token
                val token = JWT.create()
                    .withClaim("username", user.username)
                    .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 1000)) // 1 hour expiration
                    .sign(Algorithm.HMAC256("your-secret-key"))

                call.respond(HttpStatusCode.OK, mapOf("token" to token))
            } catch (e: Exception) {
                log.error("Error during login process", e)
                call.respond(HttpStatusCode.InternalServerError, "Server error: ${e.message}")
            }
        }

        post("/search/therapists") {
            val filters = call.receive<SearchFilters>()
            val userRepository = UserRepository()
            val therapists = userRepository.searchTherapists(filters)
            call.respond(HttpStatusCode.OK, therapists)
        }


                authenticate("jwt") {
                    post("/chat/send") {
                        val chatMessage = call.receive<ChatMessage>()
                        val userRepository = UserRepository()
                        userRepository.saveChatMessage(chatMessage)
                        call.respond(HttpStatusCode.OK, "Message sent")
                    }

                    get("/chat/history/{userId}") {
                        val userId = call.parameters["userId"]
                        val userRepository = UserRepository()
                        val chatHistory = userRepository.getChatHistory(userId!!)
                        call.respond(HttpStatusCode.OK, chatHistory)
                    }
                }

        post("/start-conversation") {
            val userId = call.principal<UserIdPrincipal>()?.name ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

            // Receive the conversation request payload
            val conversationRequest = call.receive<StartConversationRequest>()
            val therapistId = conversationRequest.therapistId

            // Call the repository to start the conversation
            val conversationRepository = ConversationRepository()
            val conversationId = conversationRepository.startConversation(userId, therapistId)

            // Respond with the created or existing conversation ID
            call.respond(HttpStatusCode.Created, mapOf("conversationId" to conversationId))
        }



        get("/conversation/{conversationId}") {
            val conversationId = call.parameters["conversationId"]
            if (conversationId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing conversation ID")
                return@get
            }

            val conversationRepository = ConversationRepository() // Create an instance of ConversationRepository
            val conversation = conversationRepository.getConversation(conversationId)

            if (conversation != null) {
                call.respond(HttpStatusCode.OK, conversation)
            } else {
                call.respond(HttpStatusCode.NotFound, "Conversation not found")
            }
        }







        authenticate("jwt") {
            get("/secure") {
                val principal = call.principal<UserIdPrincipal>()
                if (principal != null) {
                    call.respondText("This is a secure endpoint")
                } else {
                    call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                }
            }
        }

        authenticate("jwt") {
            get("/protected/route/basic") {
                val principal = call.principal<UserIdPrincipal>()!!
                call.respondText("Hello ${principal.name}")
            }

            get("/protected/route/form") {
                val principal = call.principal<UserIdPrincipal>()!!
                call.respondText("Hello ${principal.name}")
            }

            get("/test/auth") {
                val principal = call.principal<UserIdPrincipal>()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
                } else {
                    call.respondText("Authenticated user: ${principal.name}")
                }
            }
        }


        // Static plugin. Try to access `/static/index.html`
        staticResources("/static", "static")
    }
}