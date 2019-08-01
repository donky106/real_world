package com.hexagonkt.realworld

import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import com.auth0.jwt.interfaces.DecodedJWT
import com.hexagonkt.helpers.Resource
import com.hexagonkt.helpers.require
import com.hexagonkt.helpers.withZone
import com.hexagonkt.http.server.*
import com.hexagonkt.http.server.jetty.JettyServletAdapter
import com.hexagonkt.http.server.servlet.ServletServer
import com.hexagonkt.injection.InjectionManager
import com.hexagonkt.settings.SettingsManager.settings
import com.hexagonkt.store.IndexOrder.ASCENDING
import com.hexagonkt.store.Store
import com.hexagonkt.store.mongodb.MongoDbStore
import com.hexagonkt.realworld.rest.Jwt
import com.hexagonkt.realworld.rest.cors
import com.hexagonkt.serialization.convertToMap
import java.time.LocalDateTime
import javax.servlet.annotation.WebListener
import kotlin.text.Charsets.UTF_8
import java.time.ZoneOffset.UTC

internal val injector = InjectionManager {
    // HTTP
    bindObject<ServerPort>(JettyServletAdapter())

    // JWT
    val keyStoreResource = settings.require("keyStoreResource").toString()
    val keyStorePassword = settings.require("keyStorePassword").toString()
    val keyPairAlias = settings.require("keyPairAlias").toString()

    bindObject(Jwt(Resource(keyStoreResource), keyStorePassword, keyPairAlias))

    // DB
    val mongodbUrl = settings.require("mongodbUrl").toString()

    val userStore = MongoDbStore(User::class, User::username, mongodbUrl)
    userStore.createIndex(true, User::email.name to ASCENDING)

    val articleStore = MongoDbStore(Article::class, Article::slug, mongodbUrl)
    articleStore.createIndex(true, Article::author.name to ASCENDING)

    bindObject<Store<User, String>>(User::class, userStore)
    bindObject<Store<Article, String>>(Article::class, articleStore)
}

internal val router: Router = Router {

    val jwt: Jwt = injector.inject()

    val users: Store<User, String> = injector.inject<Store<User, String>>(User::class)
    val articles: Store<Article, String> = injector.inject<Store<Article, String>>(Article::class)

    cors()

    path("/users") {
        // TODO Authenticate and require 'root' user or owner
        delete("/{username}") { users.deleteOne(pathParameters["username"]) }

        post("/login") {
            val bodyUser = request.body<WrappedLoginRequest>().user
            val filter = mapOf(User::email.name to bodyUser.email)
            val user = users.findOne(filter) ?: halt(404, "Not Found")
            if (user.password == bodyUser.password) {
                val content = WrappedUserResponse(
                    UserResponse(
                        email = user.email,
                        username = user.username,
                        bio = user.bio ?: "",
                        image = user.image?.toString() ?: "",
                        token = jwt.sign(user.username)
                    )
                )

                ok(content, charset = UTF_8)
            }
            else {
                send(401, "Bad credentials")
            }
        }

        post {
            val user = request.body<WrappedRegistrationRequest>().user
            val key = users.insertOne(User(user.username, user.email, user.password))
            val content = WrappedUserResponse(
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
    }

    path("/user") {
        authenticate(jwt)

        get {
            val principal = attributes["principal"] as DecodedJWT
            val user = users.findOne(principal.subject) ?: halt(404, "Not Found")
            val content = WrappedUserResponse(
                UserResponse(
                    email = user.email,
                    username = user.username,
                    bio = user.bio ?: "",
                    image = user.image?.toString() ?: "",
                    token = jwt.sign(user.username)
                )
            )

            ok(content, charset = UTF_8)
        }

        put {
            val principal = attributes["principal"] as DecodedJWT
            val body = request.body<WrappedPutUserRequest>().user
            val updates = body.convertToMap().mapKeys { it.key.toString() }
            val updated = users.updateOne(principal.subject, updates)

            if (updated) {
                val user = users.findOne(principal.subject) ?: halt(500)
                val content = WrappedUserResponse(
                    UserResponse(
                        email = user.email,
                        username = user.username,
                        bio = user.bio ?: "",
                        image = user.image?.toString() ?: "",
                        token = jwt.sign(user.username)
                    )
                )

                ok(content, charset = UTF_8)
            }
            else {
                send(500, "Username ${principal.subject} not updated")
            }
        }
    }

    path("/profiles/{username}") {
        authenticate(jwt)

        post("/follow") {
            val principal = attributes["principal"] as DecodedJWT
            val user = users.findOne(principal.subject) ?: halt(404, "Not Found")
            val updated = users.updateOne(principal.subject, mapOf("following" to user.following + pathParameters["username"]))
            val profile = users.findOne(pathParameters["username"]) ?: halt(404, "Not Found")
            val content = WrappedProfileResponse(
                ProfileResponse(
                    username = profile.username,
                    bio = profile.bio ?: "",
                    image = profile.image?.toString() ?: "",
                    following = updated
                )
            )

            ok(content, charset = UTF_8)
        }

        delete("/follow") {
            val principal = attributes["principal"] as DecodedJWT
            val user = users.findOne(principal.subject) ?: halt(404, "Not Found")
            val updated = users.updateOne(principal.subject, mapOf("following" to user.following - pathParameters["username"]))
            val profile = users.findOne(pathParameters["username"]) ?: halt(404, "Not Found")
            val content = WrappedProfileResponse(
                ProfileResponse(
                    username = profile.username,
                    bio = profile.bio ?: "",
                    image = profile.image?.toString() ?: "",
                    following = !updated
                )
            )

            ok(content, charset = UTF_8)
        }

        get {
            val principal = attributes["principal"] as DecodedJWT
            val user = users.findOne(principal.subject) ?: halt(404, "Not Found")
            val profile = users.findOne(pathParameters["username"]) ?: halt(404, "Not Found")
            val content = WrappedProfileResponse(
                ProfileResponse(
                    username = profile.username,
                    bio = profile.bio ?: "",
                    image = profile.image?.toString() ?: "",
                    following = user.following.contains(profile.username)
                )
            )

            ok(content, charset = UTF_8)
        }
    }

    path("/articles") {
        authenticate(jwt)

        get("/feed") {
            val principal = attributes["principal"] as DecodedJWT
            empty()
        }

        path("/{slug}") {
            delete {
                if (!articles.deleteOne(pathParameters["slug"]))
                    halt(500)
            }

            put {
                val principal = attributes["principal"] as DecodedJWT
                val body = request.body<WrappedPutArticleRequest>().article
                val slug = pathParameters["slug"]
                val updatedAt = LocalDateTime.now().format(ISO_LOCAL_DATE_TIME) // TODO Fails if not formatted as string
                val requestUpdates = body.convertToMap().mapKeys { it.key.toString() } + (Article::updatedAt.name to updatedAt)

                val updates =
                    if (body.title != null) requestUpdates + (Article::slug.name to body.title.toSlug())
                    else requestUpdates

                val updated = articles.updateOne(slug, updates)

                if (updated) {
                    val article = articles.findOne(slug) ?: halt(500)
                    val content = WrappedArticleCreationResponse(
                        ArticleCreationResponse(
                            slug = article.slug,
                            title = article.title,
                            description = article.description,
                            body = article.body,
                            tagList = article.tagList,
                            createdAt = article.createdAt.toIso8601(),
                            updatedAt = article.updatedAt.toIso8601(),
                            favorited = article.favoritedBy.contains(principal.subject),
                            favoritesCount = article.favoritedBy.size,
                            author = article.author
                        )
                    )

                    ok(content, charset = UTF_8)
                }
                else {
                    send(500, "Article $slug not updated")
                }
            }

            get { empty() }

            path("/favorite") {
                post { empty() }
                delete { empty() }
            }

            path("/comments") {
                post { empty() }
                get { empty() }
                delete("/{id}") { empty() }
            }
        }

        post {
            val principal = attributes["principal"] as DecodedJWT
            val bodyArticle = request.body<WrappedArticleRequest>().article
            val article = Article(
                slug = bodyArticle.title.toSlug(),
                author = principal.subject,
                title = bodyArticle.title,
                description = bodyArticle.description,
                body = bodyArticle.body,
                tagList = bodyArticle.tagList
            )

            articles.insertOne(article)

            val content = WrappedArticleCreationResponse(
                ArticleCreationResponse(
                    slug = article.slug,
                    title = article.title,
                    description = article.description,
                    body = article.body,
                    tagList = article.tagList,
                    createdAt = article.createdAt.toIso8601(),
                    updatedAt = article.updatedAt.toIso8601(),
                    favorited = false,
                    favoritesCount = 0,
                    author = principal.subject
                )
            )

            ok(content, charset = UTF_8)
        }

        get {
            val principal = attributes["principal"] as DecodedJWT

            // Get user

            // Get query params

            val all = articles.findAll()
            val responses = all.map {
                ArticleResponse(
                    slug = it.slug,
                    title = it.title,
                    description = it.description,
                    body = it.body,
                    tagList = it.tagList,
                    createdAt = it.createdAt.toIso8601(),
                    updatedAt = it.updatedAt.toIso8601(),
                    favorited = it.favoritedBy.contains(principal.subject),
                    favoritesCount = it.favoritedBy.size,
                    author = AuthorResponse(
                        username = it.author,
                        bio = "",
                        image = "",
                        following = false
                    )
                )
            }

            ok(WrappedArticlesResponse(responses, articles.count()), charset = UTF_8)
        }
    }

    get("/tags") { empty() }
}

private fun Router.authenticate(jwt: Jwt) {
    before("/") { parsePrincipal(jwt) }
    before("/*") { parsePrincipal(jwt) }
}

private fun Call.parsePrincipal(jwt: Jwt) {
    val token = request.headers["Authorization"]?.firstOrNull() ?: halt(401, "Unauthorized")
    val principal = jwt.verify(token.removePrefix("Token").trim())
    attributes["principal"] = principal
}

private fun String.toSlug() =
    this.toLowerCase().replace(' ', '-')

private fun LocalDateTime.toIso8601() =
    this.withZone().withZoneSameInstant(UTC).format(ISO_LOCAL_DATE_TIME) + "Z"

private fun Call.empty() {
    ok("${request.method} ${request.path}", charset = UTF_8)
}

@WebListener
@Suppress("unused")
class WebApplication : ServletServer(router)

internal val server: Server = Server(injector.inject(), router, settings)

internal fun main() {
    server.start()
}
