package io.github.nomisrev

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf

sealed interface ApiError {
  object PasswordNotMatched : ApiError
  data class IncorrectInput(val errors: NonEmptyList<InvalidField>) : ApiError {
    constructor(head: InvalidField): this(nonEmptyListOf(head))
  }
  data class EmptyUpdate(val description: String) : ApiError
  data class UserNotFound(val property: String) : ApiError
  data class EmailAlreadyExists(val email: String) : ApiError
  data class UsernameAlreadyExists(val username: String) : ApiError
  data class JwtGeneration(val description: String) : ApiError
  data class JwtInvalid(val description: String) : ApiError
  data class Unexpected(val description: String, val error: Throwable) : ApiError
}
