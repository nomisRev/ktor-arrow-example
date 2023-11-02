@file:Suppress("TooManyFunctions")

package io.github.nomisrev

import arrow.core.Either
import arrow.core.Either.Companion.zipOrAccumulate
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import arrow.core.leftNel
import arrow.core.mapOrAccumulate
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.routes.ArticleResource
import io.github.nomisrev.routes.FeedLimit
import io.github.nomisrev.routes.FeedOffset
import io.github.nomisrev.routes.NewArticle
import io.github.nomisrev.service.GetFeed
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
private fun String.validTitle(): Either<InvalidTitle, String> =
  trim().notBlank().mapLeft(::InvalidTitle)

@Suppress("UnusedPrivateMember")
private fun String.validDescription(): Either<InvalidDescription, String> =
  trim().notBlank().mapLeft(::InvalidDescription)

@Suppress("UnusedPrivateMember")
private fun String.validBody(): Either<InvalidBody, String> =
  trim().notBlank().mapLeft(::InvalidBody)

@Suppress("UnusedPrivateMember")
private fun validTags(tags: List<String>): Either<InvalidTag, Set<String>> =
  tags.mapOrAccumulate { it.trim().notBlank().bindNel() }.mapLeft(::InvalidTag).map { it.toSet() }

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

fun NewArticle.validate(): Either<IncorrectInput, NewArticle> =
  zipOrAccumulate(
      title.validTitle(),
      description.validDescription(),
      body.validBody(),
      validTags(tagList).map { it.toList() },
      ::NewArticle
    )
    .mapLeft(::IncorrectInput)

const val MIN_FEED_LIMIT = 1
const val MIN_FEED_OFFSET = 0

data class InvalidFeedOffset(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "feed offset"
}

data class InvalidFeedLimit(override val errors: NonEmptyList<String>) : InvalidField {
  override val field: String = "feed limit"
}

private fun Int.minSize(size: Int): EitherNel<String, Int> =
  if (this >= size) right() else "too small, minimum is $size, and found $this".leftNel()

fun Int.validFeedOffset(): Either<InvalidFeedOffset, FeedOffset> =
  minSize(MIN_FEED_OFFSET).map { FeedOffset(it) }.mapLeft { InvalidFeedOffset(it) }

fun Int.validFeedLimit(): Either<InvalidFeedLimit, FeedLimit> =
  minSize(MIN_FEED_LIMIT).map { FeedLimit(it) }.mapLeft { InvalidFeedLimit(it) }

fun ArticleResource.Feed.validate(userId: UserId): Either<IncorrectInput, GetFeed> =
  zipOrAccumulate(offsetParam.validFeedOffset(), limitParam.validFeedLimit()) { offset, limit ->
      GetFeed(userId, limit.limit, offset.offset)
    }
    .mapLeft(::IncorrectInput)
