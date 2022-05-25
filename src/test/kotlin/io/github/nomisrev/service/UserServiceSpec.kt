package io.github.nomisrev.service

import arrow.core.nonEmptyListOf
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.InvalidEmail
import io.github.nomisrev.InvalidPassword
import io.github.nomisrev.InvalidUsername
import io.github.nomisrev.PostgreSQLContainer
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.env.hikari
import io.github.nomisrev.resource
import io.github.nomisrev.utils.query
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FreeSpec

class UserServiceSpec :
  FreeSpec({
    val config = Env().copy(dataSource = PostgreSQLContainer.config())
    val dataSource by resource(hikari(config.dataSource))
    val userService by resource(dependencies(config).map { it.userService })

    val validUsername = "username"
    val validEmail = "valid@domain.com"
    val validPw = "123456789"

    afterTest { dataSource.query("TRUNCATE users CASCADE") }

    "register" -
      {
        "username cannot be empty" {
          val res = userService.register(RegisterUser("", validEmail, validPw))
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          res shouldBeLeft expected
        }

        "username longer than 25 chars" {
          val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          val res = userService.register(RegisterUser(name, validEmail, validPw))
          val errors = nonEmptyListOf("is too long (maximum is 25 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          res shouldBeLeft expected
        }

        "email cannot be empty" {
          val res = userService.register(RegisterUser(validUsername, "", validPw))
          val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          res shouldBeLeft expected
        }

        "email too long" {
          val email = "${(0..340).joinToString("") { "A" }}@domain.com"
          val res = userService.register(RegisterUser(validUsername, email, validPw))
          val errors = nonEmptyListOf("is too long (maximum is 350 characters)")
          val expected = IncorrectInput(InvalidEmail(errors))
          res shouldBeLeft expected
        }

        "email is not valid" {
          val email = "AAAA"
          val res = userService.register(RegisterUser(validUsername, email, validPw))
          val errors = nonEmptyListOf("'$email' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          res shouldBeLeft expected
        }

        "password cannot be empty" {
          val res = userService.register(RegisterUser(validUsername, validEmail, ""))
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          res shouldBeLeft expected
        }

        "password can be max 100" {
          val password = (0..100).joinToString("") { "A" }
          val res = userService.register(RegisterUser(validUsername, validEmail, password))
          val errors = nonEmptyListOf("is too long (maximum is 100 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          res shouldBeLeft expected
        }

        "All valid returns a token" {
          userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
        }

        "Register twice results in" {
          userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
          val res = userService.register(RegisterUser(validUsername, validEmail, validPw))
          res shouldBeLeft UsernameAlreadyExists(validUsername)
        }
      }
  })
