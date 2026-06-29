package io.github.nomisrev.service

import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.JwtInvalid
import io.github.nomisrev.KotestProject
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.random.Random

class JwtServiceSpec :
  SuspendFun({
    val jwtService: JwtService = KotestProject.dependencies.get().jwtService
    val userService: UserService = KotestProject.dependencies.get().userService

    "generateJwtToken" -
      {
        "should generate a valid JWT token for a user ID" {
          val username = "user_${Random.nextInt(1000, 9999)}"
          val email = "$username@example.com"
          val password = "password_${Random.nextInt(1000, 9999)}"

          val token = userService.register(RegisterUser(username, email, password)).shouldBeRight()
          val userId =
            JWT.decodeT(token.value, JWSHMAC512Algorithm)
              .map { it.claimValueAsLong("id").shouldBeSome() }
              .shouldBeRight()

          val result = jwtService.generateJwtToken(UserId(userId))

          val jwtToken = result.shouldBeRight()
          jwtToken.value.shouldNotBeBlank()
        }

        "should be able to verify the generated token" {
          val username = "user_${Random.nextInt(1000, 9999)}"
          val email = "$username@example.com"
          val password = "password_${Random.nextInt(1000, 9999)}"

          val token = userService.register(RegisterUser(username, email, password)).shouldBeRight()
          val userId =
            JWT.decodeT(token.value, JWSHMAC512Algorithm)
              .map { it.claimValueAsLong("id").shouldBeSome() }
              .shouldBeRight()

          val generatedToken = jwtService.generateJwtToken(UserId(userId)).shouldBeRight()

          val verifiedUserId = jwtService.verifyJwtToken(generatedToken)

          val resultUserId = verifiedUserId.shouldBeRight()
          resultUserId shouldBe UserId(userId)
        }
      }

    "verifyJwtToken" -
      {
        "should return the user ID for a valid token" {
          val username = "user_${Random.nextInt(1000, 9999)}"
          val email = "$username@example.com"
          val password = "password_${Random.nextInt(1000, 9999)}"

          val token = userService.register(RegisterUser(username, email, password)).shouldBeRight()

          val userId = jwtService.verifyJwtToken(token)

          userId.shouldBeRight()
        }

        "should return JwtInvalid for an invalid token" {
          val error = jwtService.verifyJwtToken(JwtToken("invalid.token.value")).shouldBeLeft()
          error.shouldBeInstanceOf<JwtInvalid>()
        }
      }
  })
