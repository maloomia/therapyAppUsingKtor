package com.example

import Data.UserRepository
import Data.UserSignUpRequest
import com.example.plugins.*
import db.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*




fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080) {
        module()  // This is where the application modules (routes, DB connection, etc.) are configured
    }.start(wait = true)
}



fun Application.module() {
    configureSerialization()
    configureHTTP()
    configureMonitoring()
    configureSecurity()  // Ensure this is called before configureRouting
    DatabaseFactory.init()
    configureRouting()



}




