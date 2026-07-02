@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.auth

import com.auth0.jwt.interfaces.JWTVerifier
import io.github.nomisrev.users.UserId
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route

/**
 * TODO: This is a tiny layer that mimics the new Ktor Typed DSL will be removed when it is released
 *   https://github.com/ktorio/ktor-klip/pull/6 for details
 */
class JwtConfig<T>(
    val verifier: JWTVerifier,
    val validate: suspend ApplicationCall.(JWTCredential) -> T?,
) {
    val name: String = "JWT"

    fun orAnonymous(): JwtConfig<T?> = this as JwtConfig<T?>
}

interface AuthenticateContext<T> {
    fun principal(call: ApplicationCall): T
}

context(ctx: AuthenticateContext<T>)
val <T> ApplicationCall.principal: T
    get() = ctx.principal(this)

inline fun <reified T : Any> Route.authenticateWith(
    jwt: JwtConfig<T>,
    crossinline block: context(AuthenticateContext<T>) Route.() -> Unit,
): Route =
    authenticate(jwt.name) {
        block.invoke(
            object : AuthenticateContext<T> {
                override fun principal(call: ApplicationCall): T = call.principal<T>()!!
            },
            this,
        )
    }

@JvmName("authenticateOptionallyWith")
inline fun <reified T : Any> Route.authenticateWith(
    jwt: JwtConfig<T?>,
    crossinline block: context(AuthenticateContext<T?>) Route.() -> Unit,
): Route =
    authenticate(jwt.name, optional = true) {
        block.invoke(
            object : AuthenticateContext<T?> {
                override fun principal(call: ApplicationCall): T? = call.principal<T>()
            },
            this,
        )
    }

@JvmInline value class JwtToken(val value: String)

data class JwtContext(val token: JwtToken, val userId: UserId)
