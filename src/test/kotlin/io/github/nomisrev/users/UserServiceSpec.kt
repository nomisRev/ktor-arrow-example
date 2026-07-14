package io.github.nomisrev.users

import arrow.core.nonEmptyListOf
import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.Email
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.InvalidEmail
import io.github.nomisrev.InvalidPassword
import io.github.nomisrev.InvalidUsername
import io.github.nomisrev.Password
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.Username
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.assertRaised
import io.github.nomisrev.dependencies
import io.github.nomisrev.registerUser
import io.github.nomisrev.testDependencies
import io.github.nomisrev.userFixture
import org.junit.Assert.assertEquals

@Suppress("RETURN_VALUE_NOT_USED_COERCION")
val UserServiceSuite by testSuite {
    testDependencies("username cannot be empty") {
        val validEmail = Email("valid@domain.com")
        val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")

        val error = assertRaised {
            dependencies.userService.register(
                RegisterUser(Username(""), validEmail, Password("Aa123456!"))
            )
        }

        assertEquals(InvalidUsername(errors), error)
    }

    testDependencies("username longer than 25 chars") {
        val validEmail = Email("valid@domain.com")
        val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val errors = nonEmptyListOf("is too long (maximum is 25 characters)")

        val error = assertRaised {
            dependencies.userService.register(
                RegisterUser(Username(name), validEmail, Password("Aa123456!"))
            )
        }

        assertEquals(InvalidUsername(errors), error)
    }

    testDependencies("email cannot be empty") {
        val validUsername = userFixture().username
        val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")

        val error = assertRaised {
            dependencies.userService.register(
                RegisterUser(validUsername, Email(""), Password("Aa123456!"))
            )
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email too long") {
        val validUsername = userFixture().username
        val email = "${(0..340).joinToString("") { "A" }}@domain.com"
        val errors = nonEmptyListOf("is too long (maximum is 350 characters)")

        val error = assertRaised {
            dependencies.userService.register(
                RegisterUser(validUsername, Email(email), Password("Aa123456!"))
            )
        }

        assertEquals(InvalidEmail(errors), error)
    }

    testDependencies("email is not valid") {
        val validUsername = userFixture().username
        val email = Email("AAAA")
        val errors = nonEmptyListOf("'$email' is invalid email")

        val error = assertRaised {
            dependencies.userService.register(
                RegisterUser(validUsername, email, Password("Aa123456!"))
            )
        }

        assertEquals(InvalidEmail(errors), error)
    }

    testDependencies("password cannot be empty") {
        val validUsername = userFixture().username
        val validEmail = Email("valid@domain.com")
        val errors =
            nonEmptyListOf(
                "Cannot be blank",
                "is too short (minimum is 8 characters)",
                "At least one uppercase letter",
                "At least one lowercase letter",
                "At least one number",
                "At least one special character",
            )

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(validUsername, validEmail, Password("")))
        }

        assertEquals(InvalidPassword(errors), error)
    }

    testDependencies("password can be max 100") {
        val validUsername = userFixture().username
        val validEmail = Email("valid@domain.com")
        val password = Password("A" + "a".repeat(98) + "1!")
        val errors = nonEmptyListOf("is too long (maximum is 100 characters)")

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(validUsername, validEmail, password))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("all valid returns a token") {
        val user = userFixture()
        val token =
            dependencies.userService.register(
                RegisterUser(user.username, user.email, user.password)
            )

        assertEquals(true, token.value.isNotBlank())
    }

    testDependencies("register with duplicate username results in") {
        val first = userFixture()
        val second = userFixture()
        dependencies.userService.register(RegisterUser(first.username, first.email, first.password))

        val error = assertRaised {
            dependencies.userService.register(
                RegisterUser(first.username, second.email, second.password)
            )
        }

        assertEquals(UsernameAlreadyExists(first.username), error)
    }

    testDependencies("register with duplicate email results in") {
        val first = userFixture()
        val second = userFixture()
        dependencies.userService.register(RegisterUser(first.username, first.email, first.password))

        val error = assertRaised {
            dependencies.userService.register(
                RegisterUser(second.username, first.email, second.password)
            )
        }

        assertEquals(EmailAlreadyExists(first.email), error)
    }

    testDependencies("email cannot be empty on login") {
        val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")

        val error = assertRaised {
            dependencies.userService.login(Login(Email(""), Password("Aa123456!")))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email too long on login") {
        val email = "${(0..340).joinToString("") { "A" }}@domain.com"
        val errors = nonEmptyListOf("is too long (maximum is 350 characters)")

        val error = assertRaised {
            dependencies.userService.login(Login(Email(email), Password("Aa123456!")))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email is not valid on login") {
        val email = "AAAA"
        val errors = nonEmptyListOf("'$email' is invalid email")

        val error = assertRaised {
            dependencies.userService.login(Login(Email(email), Password("Aa123456!")))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("password cannot be empty on login") {
        val validEmail = Email("valid@domain.com")
        val errors =
            nonEmptyListOf(
                "Cannot be blank",
                "is too short (minimum is 8 characters)",
                "At least one uppercase letter",
                "At least one lowercase letter",
                "At least one number",
                "At least one special character",
            )

        val error = assertRaised {
            dependencies.userService.login(Login(validEmail, Password("")))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("password can be max 100 on login") {
        val validEmail = Email("valid@domain.com")
        val password = Password("A" + "a".repeat(98) + "1!")
        val errors = nonEmptyListOf("is too long (maximum is 100 characters)")

        val error = assertRaised {
            dependencies.userService.login(Login(validEmail, password))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("all valid login returns a token") {
        val user = userFixture()
        dependencies.userService.register(RegisterUser(user.username, user.email, user.password))
        val (token) = dependencies.userService.login(Login(user.email, user.password))

        assertEquals(true, token.value.isNotBlank())
    }

    testDependencies("update with all null") {
        val (userId) = registerUser(userFixture())

        val error = assertRaised {
            dependencies.userService.update(Update(userId, null, null, null, null, null))
        }

        assertEquals(
            EmptyUpdate("Cannot update user with $userId with only null values"),
            error,
        )
    }

    testDependencies("update password rotates credentials and keeps public profile data") {
        val (user, userId) = registerUser(userFixture())
        val newPassword = Password("Bb987654!")

        val updated =
            dependencies.userService.update(Update(userId, null, null, newPassword, null, null))

        assertEquals(user.email, updated.email)
        assertEquals(user.username, updated.username)
        assertEquals("", updated.bio)
        assertEquals("", updated.image)

        val oldPasswordError = assertRaised {
            dependencies.userService.login(Login(user.email, user.password))
        }
        assertEquals(PasswordNotMatched, oldPasswordError)

        val (token) = dependencies.userService.login(Login(user.email, newPassword))
        assertEquals(true, token.value.isNotBlank())
    }
}
