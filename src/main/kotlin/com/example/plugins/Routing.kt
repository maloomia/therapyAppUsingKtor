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
                    .withExpiresAt(Date(System.currentTimeMillis() +30L * 24 * 60 * 60 * 1000)) // 1 hour expiration
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


        val notesRepository = NotesRepository()

        // Route to add a journal entry for clients
        authenticate("jwt") {
            post("/journal/entry") {
                val userId = call.principal<UserIdPrincipal>()?.name
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                try {
                    // Receive the journal entry payload
                    val journalEntry = call.receive<ClientJournalEntry>()

                    // Add the userId to the journal entry
                    val newJournalEntry = journalEntry.copy(userId = userId)

                    // Save the journal entry using the NotesRepository
                    val isAdded = notesRepository.addJournalEntry(newJournalEntry)

                    if (isAdded) {
                        call.respond(HttpStatusCode.Created, "Journal entry added successfully")
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to add journal entry")
                    }
                } catch (e: Exception) {
                    // If any error occurs, log it and respond with a bad request
                    log.error("Invalid journal entry data: ${e.message}")
                    call.respond(HttpStatusCode.BadRequest, "Invalid journal entry data: ${e.message}")
                    return@post
                }
            }

            // Route to retrieve journal entries for clients
            get("/journal/entries") {
                val userId = call.principal<UserIdPrincipal>()?.name ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Unauthorized"
                )

                val journalEntries = notesRepository.getJournalEntries(userId)
                call.respond(HttpStatusCode.OK, journalEntries)
            }

            // Route to add a therapist note
            post("/therapist/note") {
                val therapistId = call.principal<UserIdPrincipal>()?.name
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                val therapistNote = try {
                    call.receive<TherapistNote>()
                } catch (e: SerializationException) {
                    log.error("Invalid JSON format: ${e.message}")
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON format: ${e.message}")
                } catch (e: Exception) {
                    log.error("Invalid therapist note data: ${e.message}")
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid therapist note data: ${e.message}")
                }

                // Ensure that personal notes are not included in this request
                val newNote = therapistNote.copy(therapistId = therapistId)

                // This may be a check to ensure that `diagnosis` and `generalNotes` are provided
                if (therapistNote.diagnosis == null && therapistNote.generalNotes == null) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "Either diagnosis or general notes must be provided."
                    )
                }

                val isAdded = notesRepository.addTherapistNote(newNote)
                if (isAdded) {
                    call.respond(HttpStatusCode.Created, "Therapist note added successfully")
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to add therapist note")
                }
            }
            // Route to retrieve therapist notes related to a specific user
            get("/therapist/notes/{userId}") {
                val therapistId = call.principal<UserIdPrincipal>()?.name ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Unauthorized"
                )
                val userId =
                    call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing user ID")

                val therapistNotes = notesRepository.getTherapistNotes(therapistId, userId)
                call.respond(HttpStatusCode.OK, therapistNotes)
            }

            // Route to post a new personal note (not related to a client)
            // Route to post a new personal note (not related to a client)
            post("/therapist/note/personal") {
                val therapistId = call.principal<UserIdPrincipal>()?.name
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                try {
                    // Receive the JSON request body
                    val therapistNote = call.receive<TherapistNote>()

                    // Check if personal notes are provided; if not, you can handle it as needed
                    if (therapistNote.personalNotes.isNullOrEmpty()) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Personal notes must be provided if required.")
                    }

                    // Create a new personal note, ignoring userId
                    val newPersonalNote = TherapistNote(
                        therapistId = therapistId, // Use authenticated therapistId
                        userId = null, // Explicitly set to null for personal notes
                        diagnosis = therapistNote.diagnosis, // Optional
                        generalNotes = therapistNote.generalNotes, // Optional
                        personalNotes = therapistNote.personalNotes // Can be null
                    )

                    // Save the personal note
                    val isAdded = notesRepository.addTherapistNote(newPersonalNote)
                    if (isAdded) {
                        call.respond(HttpStatusCode.Created, "Personal note added successfully")
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to add personal note")
                    }
                } catch (e: SerializationException) {
                    log.error("Invalid JSON format: ${e.message}")
                    call.respond(HttpStatusCode.BadRequest, "Invalid JSON format: ${e.message}")
                } catch (e: Exception) {
                    log.error("Invalid personal note data: ${e.message}")
                    call.respond(HttpStatusCode.BadRequest, "Invalid personal note data: ${e.message}")
                }
            }
            // Route to get personal notes for the therapist
            get("/therapist/notes/personal") {
                val therapistId = call.principal<UserIdPrincipal>()?.name
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                val personalNotes = notesRepository.getPersonalNotes(therapistId)
                call.respond(HttpStatusCode.OK, personalNotes)
            }}


        /*post("/appointments/book") {
            try {
                // Read the incoming request body as text first
                val jsonData = call.receiveText() // Get raw JSON text
                log.info("Received JSON data: $jsonData") // Log the raw JSON

                // Now parse the JSON into the Appointment object
                val appointment: Appointment = try {
                    Json.decodeFromString<Appointment>(jsonData) // Decode using Kotlin Serialization
                } catch (e: Exception) {
                    log.error("Invalid appointment data: ${e.message}")
                    call.respond(HttpStatusCode.BadRequest, "Invalid appointment data: ${e.message}")
                    return@post
                }

                val sessionRepository = SessionRepository()

                // Log the therapistId from the appointment request
                log.info("Received appointment request for therapistId: ${appointment.therapistId}")

                // Fetch therapist details
                val therapistDetails = sessionRepository.getTherapistDetails(appointment.therapistId)

                // Check if therapist details were found
                if (therapistDetails == null) {
                    log.error("Therapist not found with ID: ${appointment.therapistId}")
                    call.respond(HttpStatusCode.NotFound, "Therapist not found.")
                    return@post
                }

                // Log the therapist availability check
                log.info("Checking therapist availability for ${therapistDetails.userId} on ${appointment.dateTime}")

                // Check if the therapist is available at the requested time
                if (!sessionRepository.isTherapistAvailable(therapistDetails.userId, appointment.dateTime)) {
                    call.respond(HttpStatusCode.Conflict, "Therapist is not available at the requested time.")
                    return@post
                }

                // Calculate the total cost based on the therapist's cost and the appointment duration
                val totalCost = sessionRepository.calculateCost(therapistDetails.cost, appointment.duration)

                // Create the new Appointment instance
                val newAppointment = appointment.copy(
                    totalCost = totalCost,
                    status = "confirmed"
                )

                // Attempt to book the appointment
                val isBooked = sessionRepository.bookAppointment(newAppointment)

                // Respond based on the booking success
                if (isBooked) {
                    log.info("Appointment successfully booked for ${newAppointment.dateTime} with therapist ${therapistDetails.userId}")
                    call.respondText("Appointment booked successfully for ${newAppointment.dateTime} at a cost of ${newAppointment.totalCost}")
                } else {
                    log.error("Failed to book appointment for ${newAppointment.dateTime}")
                    call.respond(HttpStatusCode.InternalServerError, "Failed to book appointment.")
                }
            } catch (e: Exception) {
                log.error("Error processing appointment booking: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Server error: ${e.message}")
            }
        }*/




        post("/book-session") {
            val request = call.receive<BookSessionRequest>()
            val sessionRepository = SessionRepository()
            // Call the availability check
            val isAvailable = sessionRepository.checkTherapistAvailability(request.therapistId, request.sessionDateTime, request.duration)

            if (isAvailable) {
                val newSession = TherapySession(
                    clientId = request.clientId,
                    therapistId = request.therapistId,
                    sessionDateTime = request.sessionDateTime,
                    duration = request.duration,
                    status = SessionStatus.SCHEDULED,
                    cost = request.cost
                )
                val sessionsCollection = DatabaseFactory.getSessionsCollection()
                sessionsCollection.insertOne(newSession) // Save to the database

                call.respond(HttpStatusCode.Created, newSession)
            } else {
                call.respond(HttpStatusCode.Conflict, "Therapist is not available at the requested time.")
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