package io.github.nomisrev.auth

import arrow.core.raise.either
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.JwtInvalid
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.userFixture
import io.github.nomisrev.users.RegisterUser
import io.github.nomisrev.users.UserId
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
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(user.username, user.email, user.password)
                                    )
                                }
                                .shouldBeRight()

                        val userId =
                            JWT.decodeT(token.value, JWSHMAC512Algorithm)
                                .map { it.claimValueAsLong("id").shouldBeSome() }
                                .shouldBeRight()

                        val result = either {
                            dependencies.jwtService.generateJwtToken(UserId(userId))
                        }

                        val jwtToken = result.shouldBeRight()
                        jwtToken.value.shouldNotBeBlank()
                    }
                }

                "should be able to verify the generated token" {
                    withTestDependencies { dependencies ->
                        val user = userFixture()
                        val token =
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(user.username, user.email, user.password)
                                    )
                                }
                                .shouldBeRight()

                        val userId =
                            JWT.decodeT(token.value, JWSHMAC512Algorithm)
                                .map { it.claimValueAsLong("id").shouldBeSome() }
                                .shouldBeRight()

                        val generatedToken =
                            either { dependencies.jwtService.generateJwtToken(UserId(userId)) }
                                .shouldBeRight()

                        val verifiedUserId = either {
                            dependencies.jwtService.verifyJwtToken(generatedToken)
                        }

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
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(user.username, user.email, user.password)
                                    )
                                }
                                .shouldBeRight()

                        val userId = either { dependencies.jwtService.verifyJwtToken(token) }

                        userId.shouldBeRight()
                    }
                }

                "should return JwtInvalid for an invalid token" {
                    withTestDependencies { dependencies ->
                        val error =
                            either {
                                    dependencies.jwtService.verifyJwtToken(
                                        JwtToken("invalid.token.value")
                                    )
                                }
                                .shouldBeLeft()
                        error.shouldBeInstanceOf<JwtInvalid>()
                    }
                }
            }
    })
