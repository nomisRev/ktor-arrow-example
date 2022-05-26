package io.github.nomisrev

import arrow.core.NonEmptyList
import arrow.core.continuations.EffectScope
import arrow.core.nonEmptyListOf

sealed interface DomainError

sealed interface ValidationError : DomainError
data class IncorrectInput(val errors: NonEmptyList<InvalidField>) : ValidationError {
  constructor(head: InvalidField) : this(nonEmptyListOf(head))
}

sealed interface UserError : DomainError
data class UserNotFound(val property: String) : UserError
data class EmailAlreadyExists(val email: String) : UserError
data class UsernameAlreadyExists(val username: String) : UserError

sealed interface JwtError : DomainError
data class JwtGeneration(val description: String) : JwtError
data class JwtInvalid(val description: String) : JwtError

data class Unexpected(val description: String, val error: Throwable) : UserError
