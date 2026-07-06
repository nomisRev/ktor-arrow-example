package io.github.nomisrev.env

import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.users.LoginUser
import io.github.nomisrev.users.UserWrapper
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.maxAgeDuration
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

val kotlinXSerializersModule = SerializersModule {
    contextual(UserWrapper::class) { UserWrapper.serializer(LoginUser.serializer()) }
    polymorphic(Any::class) { subclass(LoginUser::class, LoginUser.serializer()) }
}

fun Application.configure(jwtConfig: JwtConfig<JwtContext>) {
    install(DefaultHeaders)
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
    authentication {
        jwt(jwtConfig.name) {
            authSchemes("Token")
            verifier(jwtConfig.verifier)
            validate(jwtConfig.validate)
        }
    }
}
