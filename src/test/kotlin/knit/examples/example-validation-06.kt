// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation06

import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.accumulating
import arrow.core.raise.context.withError
import kotlinx.serialization.Serializable

sealed interface InvalidField {
    val errors: NonEmptyList<String>
    val field: String
}

data class InvalidUsername(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "username"
}

data class InvalidEmail(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "email"
}

data class InvalidPassword(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "password"
}

data class IncorrectInput(val errors: NonEmptyList<InvalidField>)

@Serializable
data class NewUser(val username: String, val email: String, val password: String) {
    context(_: Raise<IncorrectInput>)
    fun toRegisterUser(): RegisterUser = withError(::IncorrectInput) {
        accumulate {
            val username by accumulating { Username(username) }
            val email by accumulating { Email(email) }
            val password by accumulating { Password(password) }
            RegisterUser(username, email, password)
        }
    }
}
