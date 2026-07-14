package io.github.nomisrev.users

import arrow.core.nonEmptyListOf
import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.InvalidEmail
import io.github.nomisrev.InvalidPassword
import io.github.nomisrev.InvalidUsername
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.assertRaised
import io.github.nomisrev.dependencies
import io.github.nomisrev.loginInput
import io.github.nomisrev.registerInput
import io.github.nomisrev.registerUser
import io.github.nomisrev.testDependencies
import io.github.nomisrev.updateInput
import io.github.nomisrev.userFixture
import org.junit.Assert.assertEquals

@Suppress("RETURN_VALUE_NOT_USED_COERCION")
val UserServiceSuite by testSuite {
    val validPw = "Aa123456!"

    testDependencies("username cannot be empty") {
        val validEmail = "valid@domain.com"
        val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")

        val error = assertRaised {
            dependencies.userService.register(registerInput("", validEmail, validPw))
        }

        assertEquals(IncorrectInput(InvalidUsername(errors)), error)
    }

    testDependencies("username longer than 25 chars") {
        val validEmail = "valid@domain.com"
        val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val errors = nonEmptyListOf("is too long (maximum is 25 characters)")

        val error = assertRaised {
            dependencies.userService.register(registerInput(name, validEmail, validPw))
        }

        assertEquals(IncorrectInput(InvalidUsername(errors)), error)
    }

    testDependencies("email cannot be empty") {
        val validUsername = userFixture().username
        val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")

        val error = assertRaised {
            dependencies.userService.register(registerInput(validUsername, "", validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email too long") {
        val validUsername = userFixture().username
        val email = "${(0..340).joinToString("") { "A" }}@domain.com"
        val errors = nonEmptyListOf("is too long (maximum is 350 characters)")

        val error = assertRaised {
            dependencies.userService.register(registerInput(validUsername, email, validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email is not valid") {
        val validUsername = userFixture().username
        val email = "AAAA"
        val errors = nonEmptyListOf("'$email' is invalid email")

        val error = assertRaised {
            dependencies.userService.register(registerInput(validUsername, email, validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("password cannot be empty") {
        val validUsername = userFixture().username
        val validEmail = "valid@domain.com"
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
            dependencies.userService.register(registerInput(validUsername, validEmail, ""))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("password can be max 100") {
        val validUsername = userFixture().username
        val validEmail = "valid@domain.com"
        val password = "A" + "a".repeat(98) + "1!"
        val errors = nonEmptyListOf("is too long (maximum is 100 characters)")

        val error = assertRaised {
            dependencies.userService.register(registerInput(validUsername, validEmail, password))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("all valid returns a token") {
        val user = userFixture(password = validPw)
        val token =
            dependencies.userService.register(
                registerInput(user.username, user.email, user.password)
            )

        assertEquals(true, token.value.isNotBlank())
    }

    testDependencies("register with duplicate username results in") {
        val first = userFixture(password = validPw)
        val second = userFixture(password = validPw)
        dependencies.userService.register(
            registerInput(first.username, first.email, first.password)
        )

        val error = assertRaised {
            dependencies.userService.register(
                registerInput(first.username, second.email, second.password)
            )
        }

        assertEquals(UsernameAlreadyExists(first.username), error)
    }

    testDependencies("register with duplicate email results in") {
        val first = userFixture(password = validPw)
        val second = userFixture(password = validPw)
        dependencies.userService.register(
            registerInput(first.username, first.email, first.password)
        )

        val error = assertRaised {
            dependencies.userService.register(
                registerInput(second.username, first.email, second.password)
            )
        }

        assertEquals(EmailAlreadyExists(first.email), error)
    }

    testDependencies("email cannot be empty on login") {
        val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")

        val error = assertRaised {
            dependencies.userService.login(loginInput("", validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email too long on login") {
        val email = "${(0..340).joinToString("") { "A" }}@domain.com"
        val errors = nonEmptyListOf("is too long (maximum is 350 characters)")

        val error = assertRaised {
            dependencies.userService.login(loginInput(email, validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email is not valid on login") {
        val email = "AAAA"
        val errors = nonEmptyListOf("'$email' is invalid email")

        val error = assertRaised {
            dependencies.userService.login(loginInput(email, validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("password cannot be empty on login") {
        val validEmail = "valid@domain.com"
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
            dependencies.userService.login(loginInput(validEmail, ""))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("password can be max 100 on login") {
        val validEmail = "valid@domain.com"
        val password = "A" + "a".repeat(98) + "1!"
        val errors = nonEmptyListOf("is too long (maximum is 100 characters)")

        val error = assertRaised {
            dependencies.userService.login(loginInput(validEmail, password))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("all valid login returns a token") {
        val user = userFixture(password = validPw)
        dependencies.userService.register(registerInput(user.username, user.email, user.password))
        val (token) = dependencies.userService.login(loginInput(user.email, user.password))

        assertEquals(true, token.value.isNotBlank())
    }

    testDependencies("update with all null") {
        val (userId) = registerUser(userFixture(password = validPw))

        val error = assertRaised {
            dependencies.userService.update(updateInput(userId, null, null, null, null, null))
        }

        assertEquals(
            EmptyUpdate("Cannot update user with $userId with only null values"),
            error,
        )
    }

    testDependencies("update password rotates credentials and keeps public profile data") {
        val (user, userId) = registerUser(userFixture(password = validPw))
        val newPassword = "Bb987654!"

        val updated =
            dependencies.userService.update(
                updateInput(userId, null, null, newPassword, null, null)
            )

        assertEquals(user.email, updated.email)
        assertEquals(user.username, updated.username)
        assertEquals("", updated.bio)
        assertEquals("", updated.image)

        val oldPasswordError = assertRaised {
            dependencies.userService.login(loginInput(user.email, user.password))
        }
        assertEquals(PasswordNotMatched, oldPasswordError)

        val (token) = dependencies.userService.login(loginInput(user.email, newPassword))
        assertEquals(true, token.value.isNotBlank())
    }
}
