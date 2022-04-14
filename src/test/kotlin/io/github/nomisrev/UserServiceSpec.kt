package io.github.nomisrev

import arrow.core.nonEmptyListOf
import io.github.nomisrev.ApiError.IncorrectInput
import io.github.nomisrev.config.Config
import io.github.nomisrev.config.dependencies
import io.github.nomisrev.service.RegisterUser
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FreeSpec

class UserServiceSpec :
  FreeSpec({
    val config = Config().copy(dataSource = PostgreSQLContainer.config())
    val userService by resource(dependencies(config).map { it.userService })

    val validUsername = "username"
    val validEmail = "valid@domain.com"
    val validPw = "123456789"

    "register" -
      {
        "username cannot be empty" {
          val res = userService.register(RegisterUser("", validEmail, validPw))
          val errors = nonEmptyListOf("Cannot be blank", "'' is too short (minimum is 1 character)")
          val expected = IncorrectInput(InvalidUsername(errors))
          res shouldBeLeft expected
        }

        "username longer than 25 chars" {
          val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          val res = userService.register(RegisterUser(name, validEmail, validPw))
          val errors = nonEmptyListOf("'$name' is too long (maximum is 25 character)")
          val expected = IncorrectInput(InvalidUsername(errors))
          res shouldBeLeft expected
        }

        "email cannot be empty" {
          val res = userService.register(RegisterUser(validUsername, "", validPw))
          val errors = nonEmptyListOf("Cannot be blank", "is invalid")
          val expected = IncorrectInput(InvalidEmail(errors))
          res shouldBeLeft expected
        }

        "email too long" {
          val email = "${(0..340).joinToString("") { "A" }}@domain.com"
          val res = userService.register(RegisterUser(validUsername, email, validPw))
          val errors = nonEmptyListOf("'$email' is too long (maximum is 350 character)")
          val expected = IncorrectInput(InvalidEmail(errors))
          res shouldBeLeft expected
        }

        "password cannot be empty" {
          val res = userService.register(RegisterUser(validUsername, validEmail, ""))
          val errors = nonEmptyListOf("Cannot be blank", "'' is too short (minimum is 8 character)")
          val expected = IncorrectInput(InvalidPassword(errors))
          res shouldBeLeft expected
        }

        "All valid returns a token" {
          userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
        }
      }
  })
