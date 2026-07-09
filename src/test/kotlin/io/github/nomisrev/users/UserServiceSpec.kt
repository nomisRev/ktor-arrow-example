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
import io.github.nomisrev.registerUser
import io.github.nomisrev.testDependencies
import io.github.nomisrev.userFixture
import org.junit.Assert.assertEquals

@Suppress("RETURN_VALUE_NOT_USED_COERCION")
val UserServiceSuite by testSuite {
    val validPw = "123456789"

    testDependencies("username cannot be empty") {
        val validEmail = "valid@domain.com"
        val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")

        val error = assertRaised {
            dependencies.userService.register(RegisterUser("", validEmail, validPw))
        }

        assertEquals(IncorrectInput(InvalidUsername(errors)), error)
    }

    testDependencies("username longer than 25 chars") {
        val validEmail = "valid@domain.com"
        val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val errors = nonEmptyListOf("is too long (maximum is 25 characters)")

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(name, validEmail, validPw))
        }

        assertEquals(IncorrectInput(InvalidUsername(errors)), error)
    }

    testDependencies("email cannot be empty") {
        val validUsername = userFixture().username
        val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(validUsername, "", validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email too long") {
        val validUsername = userFixture().username
        val email = "${(0..340).joinToString("") { "A" }}@domain.com"
        val errors = nonEmptyListOf("is too long (maximum is 350 characters)")

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(validUsername, email, validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email is not valid") {
        val validUsername = userFixture().username
        val email = "AAAA"
        val errors = nonEmptyListOf("'$email' is invalid email")

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(validUsername, email, validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("password cannot be empty") {
        val validUsername = userFixture().username
        val validEmail = "valid@domain.com"
        val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(validUsername, validEmail, ""))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("password can be max 100") {
        val validUsername = userFixture().username
        val validEmail = "valid@domain.com"
        val password = (0..100).joinToString("") { "A" }
        val errors = nonEmptyListOf("is too long (maximum is 100 characters)")

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(validUsername, validEmail, password))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("all valid returns a token") {
        val user = userFixture(password = validPw)
        val token = dependencies.userService.register(RegisterUser(user.username, user.email, user.password))

        assertEquals(true, token.value.isNotBlank())
    }

    testDependencies("register with duplicate username results in") {
        val first = userFixture(password = validPw)
        val second = userFixture(password = validPw)
        dependencies.userService.register(RegisterUser(first.username, first.email, first.password))

        val error = assertRaised {
            dependencies.userService.register(
                RegisterUser(first.username, second.email, second.password)
            )
        }

        assertEquals(UsernameAlreadyExists(first.username), error)
    }

    testDependencies("register with duplicate email results in") {
        val first = userFixture(password = validPw)
        val second = userFixture(password = validPw)
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
            dependencies.userService.login(Login("", validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email too long on login") {
        val email = "${(0..340).joinToString("") { "A" }}@domain.com"
        val errors = nonEmptyListOf("is too long (maximum is 350 characters)")

        val error = assertRaised {
            dependencies.userService.login(Login(email, validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("email is not valid on login") {
        val email = "AAAA"
        val errors = nonEmptyListOf("'$email' is invalid email")

        val error = assertRaised {
            dependencies.userService.login(Login(email, validPw))
        }

        assertEquals(IncorrectInput(InvalidEmail(errors)), error)
    }

    testDependencies("password cannot be empty on login") {
        val validEmail = "valid@domain.com"
        val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")

        val error = assertRaised {
            dependencies.userService.login(Login(validEmail, ""))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("password can be max 100 on login") {
        val validEmail = "valid@domain.com"
        val password = (0..100).joinToString("") { "A" }
        val errors = nonEmptyListOf("is too long (maximum is 100 characters)")

        val error = assertRaised {
            dependencies.userService.login(Login(validEmail, password))
        }

        assertEquals(IncorrectInput(InvalidPassword(errors)), error)
    }

    testDependencies("all valid login returns a token") {
        val user = userFixture(password = validPw)
        dependencies.userService.register(RegisterUser(user.username, user.email, user.password))
        val (token) = dependencies.userService.login(Login(user.email, user.password))

        assertEquals(true, token.value.isNotBlank())
    }

    testDependencies("update with all null") {
        val (userId) = registerUser(userFixture(password = validPw))

        val error = assertRaised {
            dependencies.userService.update(Update(userId, null, null, null, null, null))
        }

        assertEquals(
            EmptyUpdate("Cannot update user with $userId with only null values"),
            error
        )
    }

    testDependencies("update password rotates credentials and keeps public profile data") {
        val (user, userId) = registerUser(userFixture(password = validPw))
        val newPassword = "987654321"

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
