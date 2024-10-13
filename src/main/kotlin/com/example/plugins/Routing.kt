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
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.litote.kmongo.eq
import org.litote.kmongo.setValue



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

                // Check if mandatory fields are null
                if (userRequest == null || userProfile == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid sign-up data")
                    return@post
                }

                // Handle non-therapists
                if (userRequest!!.isTherapist == false && userPreferences == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid sign-up data for non-therapist")
                    return@post
                }

                // Validate password and confirm password
                if (userRequest!!.password != userRequest!!.confirmPassword) {
                    call.respond(HttpStatusCode.BadRequest, "Passwords do not match")
                    return@post
                }

                // Validate password strength (optional)
                if (!isValidPassword(userRequest!!.password)) {
                    call.respond(HttpStatusCode.BadRequest, "Password is too weak")
                    return@post
                }

                // Hash the password before storing
                val hashedPassword = hashPassword(userRequest!!.password)

                // Create user instance with hashed password
                val userToStore = userRequest!!.copy(password = hashedPassword, confirmPassword = null.toString())

                // Set userId in preferences and profile before saving
                val profileToStore =
                    userProfile!!.copy(userId = userToStore.userId, profilePicturePath = profilePictureFile)

                // Save user data to repository
                val userRepository = UserRepository()
                userRepository.createUser(userToStore)
                userRepository.createUserProfile(profileToStore)

                // Handle therapist-specific details
                if (userRequest!!.isTherapist) {
                    if (certificateFile == null) {
                        call.respond(HttpStatusCode.BadRequest, "Therapist must upload a certificate")
                        return@post
                    }

                    val certificate = Certificate(
                        path = certificateFile!!,
                        uploadDate = Date().toString()
                    )

                    // Update therapist-specific details
                    therapistDetails = therapistDetails?.copy(
                        userId = userToStore.userId,
                        certificate = certificate
                    )
                    userRepository.createTherapistDetails(therapistDetails!!)
                } else {
                    // Handle user preferences for non-therapists
                    userPreferences = userPreferences!!.copy(userId = userToStore.userId)
                    userRepository.createUserPreferences(userPreferences!!)
                }

                // Respond with success
                call.respond(HttpStatusCode.Created, "User signed up successfully")
            } catch (e: Exception) {
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
                val updatedTherapistDetails =
                    therapistDetails!!.copy(userId = userId, profilePicturePath = profilePictureFile)
                val updatedUserProfile = userProfile!!.copy(userId = userId, profilePicturePath = profilePictureFile)

                val therapistUpdatedSuccessfully =
                    userRepository.updateTherapistDetails(userId, updatedTherapistDetails)
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


        post("/reset-password") {
            try {
                // Receive the reset request
                val resetRequest = call.receive<ResetPasswordRequest>()
                val userRepository = UserRepository()

                // Call resetUserPassword to update the password in the database
                val updated = userRepository.resetUserPassword(resetRequest.userId, resetRequest.newPassword)

                if (updated) {
                    call.respond(HttpStatusCode.OK, "Password reset successfully")
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to reset password")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
            }
        }




        post("/search/therapists") {
            try {
                // Receive the filters from the request body
                val filters = call.receive<SearchFilters>()
                val userRepository = UserRepository()

                // Search for therapists with the provided filters, but handle nulls in the filters
                val therapists = userRepository.searchTherapists(filters)

                // If no therapists are found, return a NotFound response
                if (therapists.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, "No therapists found matching your filters")
                } else {
                    // Otherwise, return the list of therapists
                    call.respond(HttpStatusCode.OK, therapists)
                }
            } catch (e: Exception) {
                // Handle any exceptions (such as invalid data, or server-side issues)
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }

        authenticate("jwt") {
            post("/start-conversation") {
                val userId = call.principal<UserIdPrincipal>()?.name
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                println("User ID (from token): $userId")  // Debug: Log the extracted userId

                // Receive the conversation request payload
                val conversationRequest = try {
                    call.receive<StartConversationRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request data")
                    return@post
                }

                val therapistId = conversationRequest.therapistId
                println("Therapist ID (from request): $therapistId")  // Debug: Log the therapistId

                try {
                    // Call the repository to start or retrieve the conversation
                    val conversationRepository = ConversationRepository()
                    val conversationId = conversationRepository.startConversation(userId, therapistId)

                    // Respond with the created or existing conversation ID
                    if (conversationId != null) {
                        call.respond(HttpStatusCode.Created, mapOf("conversationId" to conversationId))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Error: Conversation ID not created")
                    }
                } catch (e: Exception) {
                    println("Error starting conversation: ${e.message}")  // Debug: Log any error
                    call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
                }
            }
        }

        authenticate("jwt") {
            post("/chat/send") {
                // Authenticate the user
                val userId = call.principal<UserIdPrincipal>()?.name
                if (userId == null) {
                    log.error("JWT token not found or is invalid")
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@post
                }

                // Receive the message payload
                val chatMessage = try {
                    call.receive<ChatMessage>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid message data")
                    return@post
                }

                // Ensure the message sender is valid (can be authenticated based on userId)
                val senderId = chatMessage.senderId
                val receiverId = chatMessage.receiverId

                // Validate that the sender is either the user or therapist in the conversation
                if (senderId != userId && receiverId != userId) {
                    return@post call.respond(HttpStatusCode.Unauthorized, "You are not authorized to send this message")
                }

                // Save the chat message to the Chat collection (if needed)
                val chatRepository = ConversationRepository()
                val isMessageSaved = chatRepository.saveChatMessage(chatMessage)

                // Append the message to the conversation's messages array in the Conversation collection
                val conversationId = chatMessage.conversationId // Make sure conversationId is part of the ChatMessage
                val isMessageAddedToConversation = chatRepository.addMessageToConversation(conversationId, chatMessage)

                if (isMessageSaved && isMessageAddedToConversation) {
                    call.respond(HttpStatusCode.OK, "Message sent successfully")
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to send message")
                }
            }
        }









   authenticate ("jwt"){
       get("/chat/history/{userId}") {
           val userId = call.parameters["userId"]
           val limit = call.request.queryParameters["limit"]?.toInt() ?: 20
           val offset = call.request.queryParameters["offset"]?.toInt() ?: 0

           val chatRepository = ConversationRepository()
           val chatHistory = chatRepository.getChatHistory(userId!!, limit, offset)
           call.respond(HttpStatusCode.OK, chatHistory)
       }
   }






        get("/conversations/{userId}") {
            val userId =
                call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing user ID")

            val conversationRepository = ConversationRepository()
            val conversations = conversationRepository.getConversationsForUser(userId)

            if (conversations.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, conversations)
            } else {
                call.respond(HttpStatusCode.NotFound, "No conversations found for user")
            }
        }



        get("/chat/history/{conversationId}") {
            val conversationId = call.parameters["conversationId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                "Missing conversation ID"
            )
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50 // Default limit
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0 // Default offset

            val conversationRepository = ConversationRepository()
            val chatHistory = conversationRepository.getChatHistory(conversationId, limit, offset)

            if (chatHistory.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, chatHistory)
            } else {
                call.respond(HttpStatusCode.NotFound, "No chat history found")
            }
        }

        authenticate("jwt") {
            post("/conversation/end/{conversationId}") {
                val conversationId = call.parameters["conversationId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Missing conversation ID"
                )

                val conversationRepository = ConversationRepository()
                val isEnded = conversationRepository.endConversation(conversationId)

                if (isEnded) {
                    call.respond(HttpStatusCode.OK, "Conversation ended successfully")
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to end conversation")
                }
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