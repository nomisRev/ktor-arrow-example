package io.github.nomisrev.users

import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.EmptyUpdate
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
    testDependencies("all valid returns a token") {
        val user = userFixture()
        val token = dependencies.userService.register(RegisterUser(
            user.username,
            user.email,
            user.password,
        ))

        assertEquals(true, token.value.isNotBlank())
    }

    testDependencies("register with duplicate username results in") {
        val first = userFixture()
        val second = userFixture()
        dependencies.userService.register(RegisterUser(first.username, first.email, first.password))

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(
                first.username,
                second.email,
                second.password,
            ))
        }

        assertEquals(UsernameAlreadyExists(first.username), error)
    }

    testDependencies("register with duplicate email results in") {
        val first = userFixture()
        val second = userFixture()
        dependencies.userService.register(RegisterUser(first.username, first.email, first.password))

        val error = assertRaised {
            dependencies.userService.register(RegisterUser(
                second.username,
                first.email,
                second.password,
            ))
        }

        assertEquals(EmailAlreadyExists(first.email), error)
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

        val updated = dependencies.userService.update(Update(
            userId,
            null,
            null,
            newPassword,
            null,
            null,
        ))

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
