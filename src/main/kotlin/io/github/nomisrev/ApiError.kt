package io.github.nomisrev

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf

sealed interface ApiError

sealed interface ValidationError : ApiError
data class EmptyUpdate(val description: String) : ValidationError
data class IncorrectInput(val errors: NonEmptyList<InvalidField>) : ValidationError {
  constructor(head: InvalidField) : this(nonEmptyListOf(head))
}

sealed interface UserError : ApiError
data class UserNotFound(val property: String) : UserError
data class EmailAlreadyExists(val email: String) : UserError
data class UsernameAlreadyExists(val username: String) : UserError
object PasswordNotMatched : UserError

sealed interface JwtError : ApiError
data class JwtGeneration(val description: String) : JwtError
data class JwtInvalid(val description: String) : JwtError

data class Unexpected(val description: String, val error: Throwable) : UserError
