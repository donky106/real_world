package com.hexagonkt.realworld.routes

import com.auth0.jwt.interfaces.DecodedJWT
import com.hexagonkt.http.server.Router
import com.hexagonkt.realworld.injector
import com.hexagonkt.realworld.rest.Jwt
import com.hexagonkt.realworld.services.User
import com.hexagonkt.serialization.convertToMap
import com.hexagonkt.store.Store

internal val userRouter = Router {
    val jwt: Jwt = injector.inject()
    val users: Store<User, String> = injector.inject<Store<User, String>>(User::class)

    authenticate(jwt)

    get {
        val principal = attributes["principal"] as DecodedJWT
        val user = users.findOne(principal.subject) ?: halt(404, "Not Found")
        val content = UserResponseRoot(
            UserResponse(
                email = user.email,
                username = user.username,
                bio = user.bio ?: "",
                image = user.image?.toString() ?: "",
                token = jwt.sign(user.username)
            )
        )

        ok(content, charset = Charsets.UTF_8)
    }

    put {
        val principal = attributes["principal"] as DecodedJWT
        val body = request.body<PutUserRequestRoot>().user
        val updates = body.convertToMap().mapKeys { it.key.toString() }
        val updated = users.updateOne(principal.subject, updates)

        if (updated) {
            val user = users.findOne(principal.subject) ?: halt(500)
            val content = UserResponseRoot(
                UserResponse(
                    email = user.email,
                    username = user.username,
                    bio = user.bio ?: "",
                    image = user.image?.toString() ?: "",
                    token = jwt.sign(user.username)
                )
            )

            ok(content, charset = Charsets.UTF_8)
        } else {
            send(500, "Username ${principal.subject} not updated")
        }
    }
}