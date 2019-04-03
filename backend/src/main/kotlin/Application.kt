package com.hexagonkt.realworld

import com.hexagonkt.helpers.require
import com.hexagonkt.serialization.Json
import com.hexagonkt.http.server.*
import com.hexagonkt.http.server.jetty.JettyServletAdapter
import com.hexagonkt.http.server.servlet.ServletServer
import com.hexagonkt.http.urlDecode
import com.hexagonkt.injection.InjectionManager
import java.time.LocalDateTime
import javax.servlet.annotation.WebListener

internal val injector = InjectionManager.apply {
    bindObject<ServerPort>(JettyServletAdapter())
    bindObject<RoutesService>(DefaultRoutesService())
    bindObject<InterconnectionsService>(DefaultInterconnectionsService())
}

internal val routesService: RoutesService = injector.inject()
internal val interconnectionsService: InterconnectionsService = injector.inject()

internal val router: Router = Router {
    get("/interconnections") {
        val airportFrom: String = parameters.require("departure").first()
        val airportTo: String = parameters.require("arrival").first()
        val departureText: String = parameters.require("departureDateTime").first()
        val arrivalText: String = parameters.require("arrivalDateTime").first()

        val departure = LocalDateTime.parse(departureText.urlDecode())
        val arrival = LocalDateTime.parse(arrivalText.urlDecode())

        val routes = routesService.findRoutes(airportFrom, airportTo)

        val interconnections = routes
            .flatMap { interconnectionsService.findInterconnections(it, departure, arrival) }

        ok(interconnections, Json)
    }
}

@WebListener class WebApplication : ServletServer(router)

internal val server: Server = Server(injector.inject(), router)

internal fun main() {
    server.start()
}