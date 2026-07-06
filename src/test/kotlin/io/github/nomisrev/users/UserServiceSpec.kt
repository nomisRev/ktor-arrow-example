package io.github.nomisrev.users

import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.InvalidEmail
import io.github.nomisrev.InvalidPassword
import io.github.nomisrev.InvalidUsername
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.registerUser
import io.github.nomisrev.userFixture
import io.github.nomisrev.withTestDependencies
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.string.shouldNotBeBlank

class UserServiceSpec :
    SuspendFun({
        val validPw = "123456789"

        "register" -
            {
                "username cannot be empty" {
                    val validEmail = "valid@domain.com"
                    val res = withTestDependencies { dependencies ->
                        either {
                            dependencies.userService.register(RegisterUser("", validEmail, validPw))
                        }
                    }
                    val errors =
                        nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")
                    val expected = IncorrectInput(InvalidUsername(errors))
                    res shouldBeLeft expected
                }

                "username longer than 25 chars" {
                    val validEmail = "valid@domain.com"
                    val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    val res = withTestDependencies { dependencies ->
                        either {
                            dependencies.userService.register(
                                RegisterUser(name, validEmail, validPw)
                            )
                        }
                    }
                    val errors = nonEmptyListOf("is too long (maximum is 25 characters)")
                    val expected = IncorrectInput(InvalidUsername(errors))
                    res shouldBeLeft expected
                }

                "email cannot be empty" {
                    val validUsername = userFixture().username
                    val res = withTestDependencies { dependencies ->
                        either {
                            dependencies.userService.register(
                                RegisterUser(validUsername, "", validPw)
                            )
                        }
                    }
                    val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")
                    val expected = IncorrectInput(InvalidEmail(errors))
                    res shouldBeLeft expected
                }

                "email too long" {
                    val validUsername = userFixture().username
                    val email = "${(0..340).joinToString("") { "A" }}@domain.com"
                    val res = withTestDependencies { dependencies ->
                        either {
                            dependencies.userService.register(
                                RegisterUser(validUsername, email, validPw)
                            )
                        }
                    }
                    val errors = nonEmptyListOf("is too long (maximum is 350 characters)")
                    val expected = IncorrectInput(InvalidEmail(errors))
                    res shouldBeLeft expected
                }

                "email is not valid" {
                    val validUsername = userFixture().username
                    val email = "AAAA"
                    val res = withTestDependencies { dependencies ->
                        either {
                            dependencies.userService.register(
                                RegisterUser(validUsername, email, validPw)
                            )
                        }
                    }
                    val errors = nonEmptyListOf("'$email' is invalid email")
                    val expected = IncorrectInput(InvalidEmail(errors))
                    res shouldBeLeft expected
                }

                "password cannot be empty" {
                    val validUsername = userFixture().username
                    val validEmail = "valid@domain.com"
                    val res = withTestDependencies { dependencies ->
                        either {
                            dependencies.userService.register(
                                RegisterUser(validUsername, validEmail, "")
                            )
                        }
                    }
                    val errors =
                        nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")
                    val expected = IncorrectInput(InvalidPassword(errors))
                    res shouldBeLeft expected
                }

                "password can be max 100" {
                    val validUsername = userFixture().username
                    val validEmail = "valid@domain.com"
                    val password = (0..100).joinToString("") { "A" }
                    val res = withTestDependencies { dependencies ->
                        either {
                            dependencies.userService.register(
                                RegisterUser(validUsername, validEmail, password)
                            )
                        }
                    }
                    val errors = nonEmptyListOf("is too long (maximum is 100 characters)")
                    val expected = IncorrectInput(InvalidPassword(errors))
                    res shouldBeLeft expected
                }

                "All valid returns a token" {
                    withTestDependencies { dependencies ->
                        val user = userFixture(password = validPw)
                        either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                            .shouldBeRight()
                    }
                }

                "Register with duplicate username results in" {
                    withTestDependencies { dependencies ->
                        val first = userFixture(password = validPw)
                        val second = userFixture(password = validPw)
                        either {
                            dependencies.userService.register(
                                RegisterUser(first.username, first.email, first.password)
                            )
                        }
                            .shouldBeRight()

                        either {
                            dependencies.userService.register(
                                RegisterUser(first.username, second.email, second.password)
                            )
                        } shouldBeLeft UsernameAlreadyExists(first.username)
                    }
                }

                "Register with duplicate email results in" {
                    withTestDependencies { dependencies ->
                        val first = userFixture(password = validPw)
                        val second = userFixture(password = validPw)
                        either {
                            dependencies.userService.register(
                                RegisterUser(first.username, first.email, first.password)
                            )
                        }
                            .shouldBeRight()

                        either {
                            dependencies.userService.register(
                                RegisterUser(second.username, first.email, second.password)
                            )
                        } shouldBeLeft EmailAlreadyExists(first.email)
                    }
                }
            }

        "login" -
            {
                "email cannot be empty" {
                    val res = withTestDependencies { dependencies ->
                        either { dependencies.userService.login(Login("", validPw)) }
                    }
                    val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")
                    val expected = IncorrectInput(InvalidEmail(errors))
                    res shouldBeLeft expected
                }

                "email too long" {
                    val email = "${(0..340).joinToString("") { "A" }}@domain.com"
                    val res = withTestDependencies { dependencies ->
                        either { dependencies.userService.login(Login(email, validPw)) }
                    }
                    val errors = nonEmptyListOf("is too long (maximum is 350 characters)")
                    val expected = IncorrectInput(InvalidEmail(errors))
                    res shouldBeLeft expected
                }

                "email is not valid" {
                    val email = "AAAA"
                    val res = withTestDependencies { dependencies ->
                        either { dependencies.userService.login(Login(email, validPw)) }
                    }
                    val errors = nonEmptyListOf("'$email' is invalid email")
                    val expected = IncorrectInput(InvalidEmail(errors))
                    res shouldBeLeft expected
                }

                "password cannot be empty" {
                    val validEmail = "valid@domain.com"
                    val res = withTestDependencies { dependencies ->
                        either { dependencies.userService.login(Login(validEmail, "")) }
                    }
                    val errors =
                        nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")
                    val expected = IncorrectInput(InvalidPassword(errors))
                    res shouldBeLeft expected
                }

                "password can be max 100" {
                    val validEmail = "valid@domain.com"
                    val password = (0..100).joinToString("") { "A" }
                    val res = withTestDependencies { dependencies ->
                        either { dependencies.userService.login(Login(validEmail, password)) }
                    }
                    val errors = nonEmptyListOf("is too long (maximum is 100 characters)")
                    val expected = IncorrectInput(InvalidPassword(errors))
                    res shouldBeLeft expected
                }

                "All valid returns a token" {
                    withTestDependencies { dependencies ->
                        val user = userFixture(password = validPw)
                        val (token) =
                            either {
                                val _ =
                                    dependencies.userService.register(
                                        RegisterUser(user.username, user.email, user.password)
                                    )

                                dependencies.userService.login(Login(user.email, user.password))
                            }
                                .shouldBeRight()

                        token.value.shouldNotBeBlank()
                    }
                }
            }

        "update" -
            {
                "Update with all null" {
                    withTestDependencies { dependencies ->
                        val (userId) = dependencies.registerUser(userFixture(password = validPw))

                        val res = either {
                            dependencies.userService.update(
                                Update(userId, null, null, null, null, null)
                            )
                        }

                        res shouldBeLeft
                            EmptyUpdate("Cannot update user with $userId with only null values")
                    }
                }

                "Update password rotates credentials and keeps public profile data" {
                    withTestDependencies { dependencies ->
                        val (user, userId) =
                            dependencies.registerUser(userFixture(password = validPw))
                        val newPassword = "987654321"

                        val updated = either {
                            dependencies.userService.update(
                                Update(userId, null, null, newPassword, null, null)
                            )
                        }
                            .shouldBeRight()

                        assert(updated.email == user.email)
                        assert(updated.username == user.username)
                        assert(updated.bio == "")
                        assert(updated.image == "")

                        either {
                            dependencies.userService.login(Login(user.email, user.password))
                        } shouldBeLeft PasswordNotMatched

                        either {
                            dependencies.userService.login(Login(user.email, newPassword))
                        }
                            .shouldBeRight()
                    }
                }
            }
    })
