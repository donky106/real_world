package com.hexagonkt.realworld.routes

import com.hexagonkt.http.server.Router
import com.hexagonkt.realworld.injector
import com.hexagonkt.realworld.services.Article
import com.hexagonkt.store.Store

internal val tagsRouter = Router {
    val articles: Store<Article, String> = injector.inject<Store<Article, String>>(Article::class)

    get {}
}