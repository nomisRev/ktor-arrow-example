@file:Suppress("TooManyFunctions")

package io.github.nomisrev

import arrow.core.NonEmptyList
import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.invalidNel
import arrow.core.nonEmptyListOf
import arrow.core.traverse
import arrow.core.validNel
import arrow.core.zip
import io.github.nomisrev.ApiError.IncorrectInput
import io.github.nomisrev.service.Login
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.UpdateUser

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

fun Login.validate(): Validated<IncorrectInput, Login> =
  email.validEmail().zip(password.validPassword(), ::Login).mapLeft(::IncorrectInput)

fun RegisterUser.validate(): Validated<IncorrectInput, RegisterUser> =
  username
    .validUsername()
    .zip(email.validEmail(), password.validPassword(), ::RegisterUser)
    .mapLeft(::IncorrectInput)

fun UpdateUser.validate(): Validated<IncorrectInput, UpdateUser> =
  username
    .traverse(String::validUsername)
    .zip(email.traverse(String::validEmail), password.traverse(String::validPassword)) {
      username,
      email,
      password ->
      UpdateUser(userId, username, email, password, bio, image)
    }
    .mapLeft(::IncorrectInput)

private fun <E, A, B> A?.traverse(f: (A) -> ValidatedNel<E, B>): ValidatedNel<E, B?> =
  this?.let(f) ?: null.validNel()

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 100
private const val MAX_EMAIL_LENGTH = 350
private const val MIN_USERNAME_LENGTH = 1
private const val MAX_USERNAME_LENGTH = 25

private fun String.validPassword(): ValidatedNel<InvalidPassword, String> =
  notBlank()
    .zip(minSize(MIN_PASSWORD_LENGTH), maxSize(MAX_PASSWORD_LENGTH)) { a, _, _ -> a }
    .mapLeft(toInvalidField(::InvalidPassword))

private fun String.validEmail(): ValidatedNel<InvalidEmail, String> {
  val trimmed = trim()
  return trimmed
    .notBlank()
    .zip(trimmed.maxSize(MAX_EMAIL_LENGTH), trimmed.looksLikeEmail()) { a, _, _ -> a }
    .mapLeft(toInvalidField(::InvalidEmail))
}

private fun String.validUsername(): ValidatedNel<InvalidUsername, String> {
  val trimmed = trim()
  return trimmed
    .notBlank()
    .zip(trimmed.minSize(MIN_USERNAME_LENGTH), trimmed.maxSize(MAX_USERNAME_LENGTH)) { a, _, _ ->
      a
    }
    .mapLeft(toInvalidField(::InvalidUsername))
}

@Suppress("UnusedPrivateMember")
private fun String.validTitle(): ValidatedNel<InvalidTitle, String> =
  trim().notBlank().mapLeft(toInvalidField(::InvalidTitle))

@Suppress("UnusedPrivateMember")
private fun String.validDescription(): ValidatedNel<InvalidDescription, String> =
  trim().notBlank().mapLeft(toInvalidField(::InvalidDescription))

@Suppress("UnusedPrivateMember")
private fun String.validBody(): ValidatedNel<InvalidBody, String> =
  trim().notBlank().mapLeft(toInvalidField(::InvalidBody))

@Suppress("UnusedPrivateMember")
private fun validTags(tags: List<String>): ValidatedNel<InvalidTag, Set<String>> =
  tags.traverse { it.trim().notBlank() }.bimap(toInvalidField(::InvalidTag)) { it.toSet() }

private fun <A : InvalidField> toInvalidField(
  transform: (NonEmptyList<String>) -> A
): (NonEmptyList<String>) -> NonEmptyList<A> = { nel -> nonEmptyListOf(transform(nel)) }

private fun String.notBlank(): ValidatedNel<String, String> =
  if (isNotBlank()) validNel() else "Cannot be blank".invalidNel()

private fun String.minSize(size: Int) =
  if (length >= size) validNel() else "is too short (minimum is $size characters)".invalidNel()

private fun String.maxSize(size: Int) =
  if (length <= size) validNel() else "is too long (maximum is $size characters)".invalidNel()

private val emailPattern = ".+@.+\\..+".toRegex()

private fun String.looksLikeEmail() =
  if (emailPattern.matches(this)) validNel() else "'$this' is invalid email".invalidNel()
