package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import arrow.core.continuations.either
import arrow.core.nonEmptyListOf
import io.github.nomisrev.DomainError
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.InvalidUsername
import io.github.nomisrev.InvalidEmail
import io.github.nomisrev.InvalidPassword
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.UserError
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.env.Env
import io.github.nomisrev.persistence.UserId
import io.github.nomisrev.persistence.UserPersistence
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FreeSpec
import kotlin.time.Duration.Companion.days

class UserServiceUnitSpec :
  FreeSpec({
    val validUsername = "username"
    val validEmail = "valid@domain.com"
    val validPw = "123456789"

    suspend fun <A> userContext(
      stub: UserPersistence = StubUserPersistence(),
      auth: Env.Auth = Env.Auth("secret", "issuer", 2.days),
      block: suspend context(EffectScope<DomainError>, UserPersistence, Env.Auth) () -> A
    ): Either<DomainError, A> = either {
      block(this, stub, auth)
    }

    "register" -
      {
        "username cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          userContext {
            UserService.register(RegisterUser("", validEmail, validPw))
          } shouldBeLeft expected
        }

        "username longer than 25 chars" {
          val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          val errors = nonEmptyListOf("is too long (maximum is 25 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          userContext {
            UserService.register(RegisterUser(name, validEmail, validPw))
          } shouldBeLeft expected
        }

        "email cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          userContext {
            UserService.register(RegisterUser(validUsername, "", validPw))
          } shouldBeLeft expected
        }

        "email too long" {
          val email = "${(0..340).joinToString("") { "A" }}@domain.com"
          val errors = nonEmptyListOf("is too long (maximum is 350 characters)")
          val expected = IncorrectInput(InvalidEmail(errors))
          userContext {
            UserService.register(RegisterUser(validUsername, email, validPw))
          } shouldBeLeft expected
        }

        "email is not valid" {
          val email = "AAAA"
          val errors = nonEmptyListOf("'$email' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          userContext {
            UserService.register(RegisterUser(validUsername, email, validPw))
          } shouldBeLeft expected
        }

        "password cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          userContext {
            UserService.register(RegisterUser(validUsername, validEmail, ""))
          } shouldBeLeft expected
        }

        "password can be max 100" {
          val password = (0..100).joinToString("") { "A" }
          val errors = nonEmptyListOf("is too long (maximum is 100 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          userContext {
            UserService.register(RegisterUser(validUsername, validEmail, password))
          } shouldBeLeft expected
        }

        "All valid returns a token" {
          userContext(StubUserPersistence(insertStub = { _, _, _ -> UserId(1) })) {
            UserService.register(RegisterUser(validUsername, validEmail, validPw))
          }.shouldBeRight()
        }
      }

    "update" -
      {
        "Update with all null" {
          userContext {
            UserService.update(Update(UserId(1), null, null, null, null, null))
          } shouldBeLeft EmptyUpdate("Cannot update user with UserId(serial=1) with only null values")
        }
      }
  })

private class StubUserPersistence(
  val insertStub: suspend context(EffectScope<UserError>) (
    username: String, email: String, password: String
  ) -> UserId = { _, _, _ -> TODO() }
) : UserPersistence {
  context(EffectScope<UserError>)
    override suspend fun insert(
    username: String,
    email: String,
    password: String // We need to manually re-expose the context, due to `this` bug
  ): UserId = effect<UserError, UserId> {
    insertStub(this, username, email, password)
  }.bind()

  context(EffectScope<UserError>) override suspend fun verifyPassword(
    email: String,
    password: String
  ): Pair<UserId, UserInfo> = TODO()

  context(EffectScope<UserError>) override suspend fun select(userId: UserId): UserInfo = TODO()
  context(EffectScope<UserError>) override suspend fun select(username: String): UserInfo = TODO()
  context(EffectScope<UserNotFound>) override suspend fun update(
    userId: UserId,
    email: String?,
    username: String?,
    password: String?,
    bio: String?,
    image: String?
  ): UserInfo = TODO()
}
