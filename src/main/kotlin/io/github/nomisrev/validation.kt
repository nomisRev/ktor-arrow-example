@file:Suppress("TooManyFunctions", "WildcardImport")

package io.github.nomisrev

import arrow.core.*
import arrow.core.Either.Companion.zipOrAccumulate
import arrow.core.mapOrAccumulate
import io.github.nomisrev.service.Login
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.Update

sealed interface InvalidField {
  val errors: NonEmptyList<String>
  val field: String
}

data class InvalidEmail(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "email"
}

data class InvalidPassword(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "password"
}

data class InvalidTag(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "tag"
}

data class InvalidUsername(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "username"
}

data class InvalidTitle(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "title"
}

data class InvalidDescription(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "description"
}

data class InvalidBody(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "body"
}

data class InvalidEmailOrPassword(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "email or password"
}

fun Login.validate(): Either<IncorrectInput, Login> =
  zipOrAccumulate(email.validEmail(), password.validPassword(), ::Login).mapLeft(::IncorrectInput)

fun RegisterUser.validate(): Either<IncorrectInput, RegisterUser> =
  zipOrAccumulate(
      username.validUsername(),
      email.validEmail(),
      password.validPassword(),
      ::RegisterUser
    )
    .mapLeft(::IncorrectInput)

fun Update.validate(): Either<IncorrectInput, Update> =
  zipOrAccumulate(
      username.mapOrAccumulate(String::validUsername),
      email.mapOrAccumulate(String::validEmail),
      password.mapOrAccumulate(String::validPassword)
    ) { username, email, password ->
      Update(userId, username, email, password, bio, image)
    }
    .mapLeft(::IncorrectInput)

private fun <E, A, B> A?.mapOrAccumulate(f: (A) -> EitherNel<E, B>): EitherNel<E, B?> =
  this?.let(f) ?: null.right()

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 100
private const val MAX_EMAIL_LENGTH = 350
private const val MIN_USERNAME_LENGTH = 1
private const val MAX_USERNAME_LENGTH = 25

private fun String.validPassword(): EitherNel<InvalidPassword, String> =
  zipOrAccumulate(notBlank(), minSize(MIN_PASSWORD_LENGTH), maxSize(MAX_PASSWORD_LENGTH)) { a, _, _
      ->
      a
    }
    .mapLeft(toInvalidField(::InvalidPassword))

private fun String.validEmail(): EitherNel<InvalidEmail, String> {
  val trimmed = trim()
  return zipOrAccumulate(
      trimmed.notBlank(),
      trimmed.maxSize(MAX_EMAIL_LENGTH),
      trimmed.looksLikeEmail()
    ) { a, _, _ ->
      a
    }
    .mapLeft(toInvalidField(::InvalidEmail))
}

private fun String.validUsername(): EitherNel<InvalidUsername, String> {
  val trimmed = trim()
  return zipOrAccumulate(
      trimmed.notBlank(),
      trimmed.minSize(MIN_USERNAME_LENGTH),
      trimmed.maxSize(MAX_USERNAME_LENGTH)
    ) { a, _, _ ->
      a
    }
    .mapLeft(toInvalidField(::InvalidUsername))
}

@Suppress("UnusedPrivateMember")
private fun String.validTitle(): EitherNel<InvalidTitle, String> =
  trim().notBlank().mapLeft(toInvalidField(::InvalidTitle))

@Suppress("UnusedPrivateMember")
private fun String.validDescription(): EitherNel<InvalidDescription, String> =
  trim().notBlank().mapLeft(toInvalidField(::InvalidDescription))

@Suppress("UnusedPrivateMember")
private fun String.validBody(): EitherNel<InvalidBody, String> =
  trim().notBlank().mapLeft(toInvalidField(::InvalidBody))

@Suppress("UnusedPrivateMember")
private fun validTags(tags: List<String>): EitherNel<InvalidTag, Set<String>> =
  tags
    .mapOrAccumulate { it.trim().notBlank().bindNel() }
    .mapLeft { errors: NonEmptyList<String> -> toInvalidField(::InvalidTag)(errors) }
    .map { it.toSet() }

private fun <A : InvalidField> toInvalidField(
  transform: (NonEmptyList<String>) -> A
): (NonEmptyList<String>) -> NonEmptyList<A> = { nel -> nonEmptyListOf(transform(nel)) }

private fun String.notBlank(): EitherNel<String, String> =
  if (isNotBlank()) right() else "Cannot be blank".leftNel()

private fun String.minSize(size: Int): EitherNel<String, String> =
  if (length >= size) right() else "is too short (minimum is $size characters)".leftNel()

private fun String.maxSize(size: Int): EitherNel<String, String> =
  if (length <= size) right() else "is too long (maximum is $size characters)".leftNel()

private val emailPattern = ".+@.+\\..+".toRegex()

private fun String.looksLikeEmail(): EitherNel<String, String> =
  if (emailPattern.matches(this)) right() else "'$this' is invalid email".leftNel()
