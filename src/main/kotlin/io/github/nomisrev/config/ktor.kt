package io.github.nomisrev.config

import io.github.nomisrev.routes.LoginUser
import io.github.nomisrev.routes.UserWrapper
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.CORS
import io.ktor.server.plugins.cors.maxAgeDuration
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.routing
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

val kotlinXSerializersModule = SerializersModule {
  contextual(UserWrapper::class) { args -> UserWrapper.serializer(LoginUser.serializer()) }
  polymorphic(Any::class) {
    //            subclass(UserWrapper::class,
    // UserWrapper.serializer(PolymorphicSerializer(Any::class)).nullable as
    // KSerializer<UserWrapper<*>>)
    //            subclass(UserWrapper::class,
    // UserWrapper.serializer(PolymorphicSerializer(Any::class).nullable))
    //            subclass(NewUser::class, NewUser.serializer())
    //            subclass(User::class, User.serializer())
    subclass(LoginUser::class, LoginUser.serializer())
    //            subclass(UpdateUser::class, UpdateUser.serializer())
  }
}

fun Application.configure() {
  routing { trace { application.log.trace(it.buildText()) } }
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
    allowNonSimpleContentTypes = true
    maxAgeDuration = 3.days
  }
}
