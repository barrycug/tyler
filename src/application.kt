package io.marauder.tyler

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.content.*
import io.ktor.locations.*
import io.ktor.features.*
import org.slf4j.event.*
import java.time.*
import io.ktor.gson.*
import io.marauder.tyler.models.FeatureCollection
import io.marauder.tyler.parser.Tiler
import io.marauder.tyler.parser.projectFeatures
import io.marauder.tyler.store.StoreClient
import io.marauder.tyler.store.StoreClientSQLite
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import java.io.InputStreamReader

fun main(args: Array<String>): Unit = io.ktor.server.netty.DevelopmentEngine.main(args)

lateinit var store: StoreClient;

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    store = StoreClientSQLite("./storage")

    install(Locations) {
    }

    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(DataConversion)

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(ContentNegotiation) {
        gson {
        }
    }


    routing {
        post("/") {
            val input = Gson().fromJson(InputStreamReader(call.receiveStream()), FeatureCollection::class.java)
            val job = launch(newSingleThreadContext("tiling-process-1")) {
                //TODO: start tiling in independent thread
                val tyler = Tiler(store, 0, 15)
                tyler.tiler(projectFeatures(input))
            }

            call.respondText("tiling started", contentType = ContentType.Text.Plain)
        }

        get("/{z}/{x}/{y_type}") {
            println(call.parameters)
            val y_list = call.parameters["y_type"]!!.split('.')

            call.respondBytes(contentType = ContentType.Application.GZip) {
                store.serveTile(call.parameters["x"]!!.toInt(), y_list[0].toInt(), call.parameters["z"]!!.toInt()) ?: ByteArray(0)
            }
        }


        static("/static") {
            resources("static")
        }

        install(StatusPages) {
            exception<AuthenticationException> {  cause ->
                call.respond(HttpStatusCode.Unauthorized)
            }
            exception<AuthorizationException> {  cause ->
                call.respond(HttpStatusCode.Forbidden)
            }

        }

        /*webSocket("/myws/echo") {
            send(Frame.Text("Hi from server"))
            while (true) {
                val frame = incoming.receive()
                if (frame is Frame.Text) {
                    send(Frame.Text("Client said: " + frame.readText()))
                }
            }
        }*/


    }
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
