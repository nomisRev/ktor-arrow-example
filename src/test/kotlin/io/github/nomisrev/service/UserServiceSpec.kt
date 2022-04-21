package io.github.nomisrev.service

import arrow.core.continuations.either
import arrow.core.nonEmptyListOf
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.EmptyUpdate
import io.github.nomisrev.ApiError.IncorrectInput
import io.github.nomisrev.ApiError.UsernameAlreadyExists
import io.github.nomisrev.InvalidEmail
import io.github.nomisrev.InvalidPassword
import io.github.nomisrev.InvalidUsername
import io.github.nomisrev.PostgreSQLContainer
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.config.Config
import io.github.nomisrev.config.dependencies
import io.github.nomisrev.config.hikari
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.resource
import io.github.nomisrev.utils.query
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.core.spec.style.FreeSpec

class UserServiceSpec :
  FreeSpec({
    val config = Config().copy(dataSource = PostgreSQLContainer.config())
    val dataSource by resource(hikari(config.dataSource))
    val userService by resource(dependencies(config).map { it.userService })

    val validUsername = "username"
    val validEmail = "valid@domain.com"
    val validPw = "123456789"

    afterTest { dataSource.query("TRUNCATE users CASCADE") }

    "register" -
      {
        "username cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          either<ApiError, JwtToken> {
            userService.register(RegisterUser("", validEmail, validPw))
          } shouldBeLeft expected
        }

        "username longer than 25 chars" {
          val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          val errors = nonEmptyListOf("is too long (maximum is 25 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          either<ApiError, JwtToken> {
            userService.register(RegisterUser(name, validEmail, validPw))
          } shouldBeLeft expected
        }

        "email cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          either<ApiError, JwtToken> {
            userService.register(RegisterUser(validUsername, "", validPw))
          } shouldBeLeft expected
        }

        "email too long" {
          val email = "${(0..340).joinToString("") { "A" }}@domain.com"
          val errors = nonEmptyListOf("is too long (maximum is 350 characters)")
          val expected = IncorrectInput(InvalidEmail(errors))
          either<ApiError, JwtToken> {
            userService.register(RegisterUser(validUsername, email, validPw))
          } shouldBeLeft expected
        }

        "email is not valid" {
          val email = "AAAA"
          val errors = nonEmptyListOf("'$email' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          either<ApiError, JwtToken> {
            userService.register(RegisterUser(validUsername, email, validPw))
          } shouldBeLeft expected
        }

        "password cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          either<ApiError, JwtToken> {
            userService.register(RegisterUser(validUsername, validEmail, ""))
          } shouldBeLeft expected
        }

        "password can be max 100" {
          val password = (0..100).joinToString("") { "A" }
          val errors = nonEmptyListOf("is too long (maximum is 100 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          either<ApiError, JwtToken> {
            userService.register(RegisterUser(validUsername, validEmail, password))
          } shouldBeLeft expected
        }

        "All valid returns a token" {
          either<ApiError, JwtToken> {
            userService.register(RegisterUser(validUsername, validEmail, validPw))
          }.shouldBeRight()
        }

        "Register twice results in UsernameAlreadyExists" {
          either<ApiError, JwtToken> {
            userService.register(RegisterUser(validUsername, validEmail, validPw))
            userService.register(RegisterUser(validUsername, validEmail, validPw))
          } shouldBeLeft UsernameAlreadyExists(validUsername)
        }
      }

    "update" -
      {
        "Update with all null" {
          val token =
            either<ApiError, JwtToken> {
              userService.register(RegisterUser(validUsername, validEmail, validPw))
            }.shouldBeRight()

          either<ApiError, UserInfo> {
            userService.update(Update(token.id(), null, null, null, null, null))
          } shouldBeLeft EmptyUpdate("Cannot update user with ${token.id()} with only null values")
        }
      }
  })

private fun JwtToken.id(): UserId =
  JWT
    .decodeT(value, JWSHMAC512Algorithm)
    .shouldBeRight { "JWToken $value should be valid JWT but found $it" }
    .jwt
    .claimValueAsLong("id")
    .shouldBeSome { "JWTToken $value should have id but found None" }
    .let(::UserId)
