package io.github.nomisrev.service

import arrow.core.continuations.EffectScope
import arrow.core.continuations.either
import arrow.core.nonEmptyListOf
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.*
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.env.hikari
import io.github.nomisrev.persistence.UserId
import io.github.nomisrev.persistence.UserPersistence
import io.github.nomisrev.utils.query
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.core.spec.style.FreeSpec

class UserServiceSpec :
  FreeSpec({
    val validUsername = "username"
    val validEmail = "valid@domain.com"
    val validPw = "123456789"

    "register" -
      {
        "username cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          either<DomainError, JwtToken> {
            register(RegisterUser("", validEmail, validPw))
          } shouldBeLeft expected
        }

        "username longer than 25 chars" {
          val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          val errors = nonEmptyListOf("is too long (maximum is 25 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          either<DomainError, JwtToken> {
            register(RegisterUser(name, validEmail, validPw))
          } shouldBeLeft expected
        }

        "email cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          either<DomainError, JwtToken> {
            register(RegisterUser(validUsername, "", validPw))
          } shouldBeLeft expected
        }

        "email too long" {
          val email = "${(0..340).joinToString("") { "A" }}@domain.com"
          val errors = nonEmptyListOf("is too long (maximum is 350 characters)")
          val expected = IncorrectInput(InvalidEmail(errors))
          either<DomainError, JwtToken> {
            register(RegisterUser(validUsername, email, validPw))
          } shouldBeLeft expected
        }

        "email is not valid" {
          val email = "AAAA"
          val errors = nonEmptyListOf("'$email' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          either<DomainError, JwtToken> {
            register(RegisterUser(validUsername, email, validPw))
          } shouldBeLeft expected
        }

        "password cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          either<DomainError, JwtToken> {
            register(RegisterUser(validUsername, validEmail, ""))
          } shouldBeLeft expected
        }

        "password can be max 100" {
          val password = (0..100).joinToString("") { "A" }
          val errors = nonEmptyListOf("is too long (maximum is 100 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          either<DomainError, JwtToken> {
            register(RegisterUser(validUsername, validEmail, password))
          } shouldBeLeft expected
        }

        "All valid returns a token" {
          either<DomainError, JwtToken> {
            register(RegisterUser(validUsername, validEmail, validPw))
          }.shouldBeRight()
        }

        "Register twice results in UsernameAlreadyExists" {
          either<DomainError, JwtToken> {
            register(RegisterUser(validUsername, validEmail, validPw))
            register(RegisterUser(validUsername, validEmail, validPw))
          } shouldBeLeft UsernameAlreadyExists(validUsername)
        }
      }

    "update" -
      {
        "Update with all null" {
          val token =
            either<DomainError, JwtToken> {
              register(RegisterUser(validUsername, validEmail, validPw))
            }.shouldBeRight()

          either<DomainError, UserInfo> {
            update(Update(token.id(), null, null, null, null, null))
          } shouldBeLeft EmptyUpdate("Cannot update user with ${token.id()} with only null values")
        }
      }
  })

fun userPersistence(
  insert_: suspend context(EffectScope<UserError>) (username: String, email: String, password: String) -> UserId
) = object : UserPersistence {
  context(EffectScope<UserError>) override suspend fun insert(
    username: String,
    email: String,
    password: String
  ): UserId = insert_(this@insert, username, email, password)

  context(EffectScope<UserError>) override suspend fun verifyPassword(
    email: String,
    password: String
  ): Pair<UserId, UserInfo> {
    TODO("Not yet implemented")
  }

  context(EffectScope<UserError>) override suspend fun select(userId: UserId): UserInfo {
    TODO("Not yet implemented")
  }

  context(EffectScope<UserError>) override suspend fun select(username: String): UserInfo {
    TODO("Not yet implemented")
  }

  context(EffectScope<UserNotFound>) override suspend fun update(
    userId: UserId,
    email: String?,
    username: String?,
    password: String?,
    bio: String?,
    image: String?
  ): UserInfo {
    TODO("Not yet implemented")
  }

}

private fun JwtToken.id(): UserId =
  JWT
    .decodeT(value, JWSHMAC512Algorithm)
    .shouldBeRight { "JWToken $value should be valid JWT but found $it" }
    .jwt
    .claimValueAsLong("id")
    .shouldBeSome { "JWTToken $value should have id but found None" }
    .let(::UserId)
