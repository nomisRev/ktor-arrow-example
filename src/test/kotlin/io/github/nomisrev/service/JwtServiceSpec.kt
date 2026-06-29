package io.github.nomisrev.service

import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.JwtInvalid
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.userFixture
import io.github.nomisrev.withTestDependencies
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf

class JwtServiceSpec :
    SuspendFun({
        "generateJwtToken" -
            {
                "should generate a valid JWT token for a user ID" {
                    withTestDependencies { dependencies ->
                        val user = userFixture()
                        val token =
                            dependencies.userService
                                .register(RegisterUser(user.username, user.email, user.password))
                                .shouldBeRight()
                        withTestDependencies { dependencies ->
                            val user = userFixture()
                            val token =
                                dependencies.userService
                                    .register(
                                        RegisterUser(user.username, user.email, user.password)
                                    )
                                    .shouldBeRight()
                            val userId =
                                JWT.decodeT(token.value, JWSHMAC512Algorithm)
                                    .map { it.claimValueAsLong("id").shouldBeSome() }
                                    .shouldBeRight()

                            val result = dependencies.jwtService.generateJwtToken(UserId(userId))

                            val jwtToken = result.shouldBeRight()
                            jwtToken.value.shouldNotBeBlank()
                        }
                    }
                }

                "should be able to verify the generated token" {
                    withTestDependencies { dependencies ->
                        val user = userFixture()
                        val token =
                            dependencies.userService
                                .register(RegisterUser(user.username, user.email, user.password))
                                .shouldBeRight()
                        val userId =
                            JWT.decodeT(token.value, JWSHMAC512Algorithm)
                                .map { it.claimValueAsLong("id").shouldBeSome() }
                                .shouldBeRight()

                        val generatedToken =
                            dependencies.jwtService.generateJwtToken(UserId(userId)).shouldBeRight()

                        val verifiedUserId = dependencies.jwtService.verifyJwtToken(generatedToken)

                        val resultUserId = verifiedUserId.shouldBeRight()
                        resultUserId shouldBe UserId(userId)
                    }
                }
            }

        "verifyJwtToken" -
            {
                "should return the user ID for a valid token" {
                    withTestDependencies { dependencies ->
                        val user = userFixture()
                        val token =
                            dependencies.userService
                                .register(RegisterUser(user.username, user.email, user.password))
                                .shouldBeRight()

                        val userId = dependencies.jwtService.verifyJwtToken(token)

                        userId.shouldBeRight()
                    }
                }

                "should return JwtInvalid for an invalid token" {
                    withTestDependencies { dependencies ->
                        val error =
                            dependencies.jwtService
                                .verifyJwtToken(JwtToken("invalid.token.value"))
                                .shouldBeLeft()
                        error.shouldBeInstanceOf<JwtInvalid>()
                    }
                }
            }
    })
