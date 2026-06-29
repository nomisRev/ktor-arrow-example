package io.github.nomisrev.env

import io.github.nomisrev.routes.LoginUser
import io.github.nomisrev.routes.UserWrapper
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.resources.Resources
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

val kotlinXSerializersModule = SerializersModule {
    contextual(UserWrapper::class) { UserWrapper.serializer(LoginUser.serializer()) }
    polymorphic(Any::class) { subclass(LoginUser::class, LoginUser.serializer()) }
}

fun Application.configure() {
    install(DefaultHeaders)
    install(Resources) { serializersModule = kotlinXSerializersModule }
    install(ContentNegotiation) {
        json(
            Json {
                serializersModule = kotlinXSerializersModule
                isLenient = true
                ignoreUnknownKeys = true
            }
        )
    }
    install(CORS) {
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost() // TODO fix
        anyMethod()
        allowNonSimpleContentTypes = true
        maxAgeDuration = 3.days
    }
}
