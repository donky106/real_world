package com.hexagonkt.realworld.routes

import com.hexagonkt.http.server.Call
import com.hexagonkt.http.server.Router
import com.hexagonkt.realworld.injector
import com.hexagonkt.realworld.rest.Jwt
import com.hexagonkt.realworld.services.User
import com.hexagonkt.serialization.Json
import com.hexagonkt.store.Store

import kotlin.text.Charsets.UTF_8

data class RegistrationRequest(
    val email: String,
    val username: String,
    val password: String
)

data class RegistrationRequestRoot(val user: RegistrationRequest)

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginRequestRoot(val user: LoginRequest)

internal val usersRouter = Router {
    val jwt: Jwt = injector.inject()
    val users: Store<User, String> = injector.inject<Store<User, String>>(User::class)

    delete("/{username}") { deleteUser(users) }
    post("/login") { login(users, jwt) }
    post { register(users, jwt) }
}

private fun Call.register(users: Store<User, String>, jwt: Jwt) {
    val user = request.body<RegistrationRequestRoot>().user
    val key = users.insertOne(User(user.username, user.email, user.password))
    val content = UserResponseRoot(
        UserResponse(
            email = user.email,
            username = key,
            bio = "",
            image = "",
            token = jwt.sign(key)
        )
    )

    ok(content, charset = UTF_8)
}

private fun Call.login(users: Store<User, String>, jwt: Jwt) {
    val bodyUser = request.body<LoginRequestRoot>().user
    val filter = mapOf(User::email.name to bodyUser.email)
    val user = users.findOne(filter) ?: halt(404, "Not Found")
    if (user.password == bodyUser.password) {
        val content = UserResponseRoot(user, jwt.sign(user.username))
        ok(content, charset = UTF_8)
    } else {
        send(401, "Bad credentials")
    }
}

// TODO Authenticate and require 'root' user or owner
private fun Call.deleteUser(users: Store<User, String>) {
    val username = pathParameters["username"]
    if (users.deleteOne(username)) ok(OkResponse("$username deleted"), Json, charset = UTF_8)
    else halt(404, "$username not found")
}
