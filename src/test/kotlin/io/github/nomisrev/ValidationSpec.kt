package io.github.nomisrev

import arrow.core.nonEmptyListOf
import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.articles.ArticlesParameters
import io.github.nomisrev.articles.CreateArticle
import io.github.nomisrev.articles.FeedLimit
import io.github.nomisrev.articles.FeedOffset
import io.github.nomisrev.articles.FeedParameters
import io.github.nomisrev.articles.GetArticles
import io.github.nomisrev.articles.GetFeed
import io.github.nomisrev.articles.NewArticle
import io.github.nomisrev.articles.NewComment
import io.github.nomisrev.articles.Slug
import io.github.nomisrev.users.LoginUser
import io.github.nomisrev.users.NewUser
import io.github.nomisrev.users.UpdateUser
import io.github.nomisrev.users.UserId
import org.junit.Assert.assertEquals

fun IncorrectInput(head: InvalidField, vararg tail: InvalidField) =
    IncorrectInput(nonEmptyListOf(head, *tail))

@Suppress("RETURN_VALUE_NOT_USED_COERCION")
val Validation by testSuite {
    test("accumulates all invalid fields and all errors per field") {
        val input = NewUser(username = "", email = "not-an-email", password = "")

        val error = assertRaised { input.toRegisterUser() }

        assertEquals(
            IncorrectInput(
                InvalidUsername(
                    nonEmptyListOf(
                        "Cannot be blank",
                        "is too short (minimum is 1 characters)",
                    )
                ),
                InvalidEmail(nonEmptyListOf("'not-an-email' is invalid email")),
                InvalidPassword(
                    nonEmptyListOf(
                        "Cannot be blank",
                        "is too short (minimum is 8 characters)",
                        "At least one uppercase letter",
                        "At least one lowercase letter",
                        "At least one number",
                        "At least one special character",
                    )
                ),
            ),
            error,
        )
    }

    test("accumulates size-limit validation errors") {
        val input =
            NewUser(
                username = "A".repeat(26),
                email = "${"A".repeat(341)}@domain.com",
                password = "A" + "a".repeat(98) + "1!",
            )

        val error = assertRaised { input.toRegisterUser() }

        assertEquals(
            IncorrectInput(
                InvalidUsername(nonEmptyListOf("is too long (maximum is 25 characters)")),
                InvalidEmail(nonEmptyListOf("is too long (maximum is 350 characters)")),
                InvalidPassword(nonEmptyListOf("is too long (maximum is 100 characters)")),
            ),
            error,
        )
    }

    test("accumulates email and password validation errors") {
        val input = LoginUser(email = "", password = "")

        val error = assertRaised { input.toLogin() }

        assertEquals(
            IncorrectInput(
                InvalidEmail(nonEmptyListOf("Cannot be blank", "'' is invalid email")),
                InvalidPassword(
                    nonEmptyListOf(
                        "Cannot be blank",
                        "is too short (minimum is 8 characters)",
                        "At least one uppercase letter",
                        "At least one lowercase letter",
                        "At least one number",
                        "At least one special character",
                    )
                ),
            ),
            error,
        )
    }

    test("accumulates non-blank invalid email and password errors on login") {
        val input =
            LoginUser(
                email = "AAAA",
                password = "A" + "a".repeat(98) + "1!",
            )

        val error = assertRaised { input.toLogin() }

        assertEquals(
            IncorrectInput(
                InvalidEmail(nonEmptyListOf("'AAAA' is invalid email")),
                InvalidPassword(nonEmptyListOf("is too long (maximum is 100 characters)")),
            ),
            error,
        )
    }

    test("accumulates errors for every provided invalid nullable field") {
        val input =
            UpdateUser(
                username = "",
                email = "invalid-email",
                password = "short",
                bio = null,
                image = null,
            )

        val error = assertRaised { input.toUpdate(UserId(1)) }

        assertEquals(
            IncorrectInput(
                InvalidUsername(
                    nonEmptyListOf(
                        "Cannot be blank",
                        "is too short (minimum is 1 characters)",
                    )
                ),
                InvalidEmail(nonEmptyListOf("'invalid-email' is invalid email")),
                InvalidPassword(
                    nonEmptyListOf(
                        "is too short (minimum is 8 characters)",
                        "At least one uppercase letter",
                        "At least one number",
                        "At least one special character",
                    )
                ),
            ),
            error,
        )
    }

    testRaise("ignores null nullable fields") {
        val input = UpdateUser()

        val update = input.toUpdate(UserId(1))
        assertEquals(UserId(1), update.userId)
        assertEquals(null, update.username)
        assertEquals(null, update.email)
        assertEquals(null, update.password)
    }

    testRaise("normalizes and maps valid user inputs at their length limits") {
        val username = "u".repeat(25)
        val email = "a".repeat(345) + "@a.co"
        val password = "A" + "a".repeat(97) + "1!"

        val register = NewUser(" $username ", " $email ", password).toRegisterUser()
        assertEquals(username, register.username.value)
        assertEquals(email, register.email.value)
        assertEquals(password, register.password.raw())

        val login = LoginUser(" $email ", password).toLogin()
        assertEquals(email, login.email.value)
        assertEquals(password, login.password.raw())

        val update =
            UpdateUser(
                    username = " $username ",
                    email = " $email ",
                    password = password,
                    bio = "bio",
                    image = "image",
                )
                .toUpdate(UserId(1))
        assertEquals(UserId(1), update.userId)
        assertEquals(username, update.username?.value)
        assertEquals(email, update.email?.value)
        assertEquals(password, update.password?.raw())
        assertEquals("bio", update.bio)
        assertEquals("image", update.image)
    }

    test("accumulates title description body and every invalid tag") {
        val input =
            NewArticle(
                title = "",
                description = " ",
                body = "",
                tagList = listOf("", "ok", " "),
            )

        val error = assertRaised { input.toCreateArticle(UserId(1)) }

        assertEquals(
            IncorrectInput(
                InvalidTitle(nonEmptyListOf("Cannot be blank")),
                InvalidDescription(nonEmptyListOf("Cannot be blank")),
                InvalidBody(nonEmptyListOf("Cannot be blank")),
                InvalidTag(nonEmptyListOf("Cannot be blank", "Cannot be blank")),
            ),
            error,
        )
    }

    test("validates body") {
        val error = assertRaised { NewComment(body = " ").toCreateComment(Slug("slug"), UserId(1)) }

        assertEquals(
            IncorrectInput(InvalidBody(nonEmptyListOf("Cannot be blank"))),
            error,
        )
    }

    test("accumulates offset and limit errors") {
        val userId = UserId(1)
        val input =
            FeedParameters(
                mutableMapOf(
                    "offset" to listOf("-1"),
                    "limit" to listOf("0"),
                )
            )

        val error = assertRaised { input.toGetFeed(userId) }

        assertEquals(
            IncorrectInput(
                InvalidFeedOffset(nonEmptyListOf("too small, minimum is 0, and found -1")),
                InvalidFeedLimit(nonEmptyListOf("too small, minimum is 1, and found 0")),
            ),
            error,
        )
    }

    test("accumulates offset and limit errors") {
        val input =
            ArticlesParameters(
                mutableMapOf(
                    "offset" to listOf("-1"),
                    "limit" to listOf("0"),
                )
            )

        val error = assertRaised { input.toGetArticles(currentUserId = null) }

        assertEquals(
            IncorrectInput(
                InvalidFeedOffset(nonEmptyListOf("too small, minimum is 0, and found -1")),
                InvalidFeedLimit(nonEmptyListOf("too small, minimum is 1, and found 0")),
            ),
            error,
        )
    }

    testRaise("returns valid inputs unchanged or mapped to service input") {
        val register = NewUser("simon", "simon@example.com", "Aa123456!").toRegisterUser()
        assertEquals("simon", register.username.value)
        assertEquals("simon@example.com", register.email.value)

        val article = NewArticle("title", "description", "body", listOf(" kotlin ", "arrow"))
        val userId = UserId(42)

        assertEquals(
            CreateArticle(
                userId,
                Title("title"),
                Description("description"),
                Body("body"),
                setOf("kotlin", "arrow"),
            ),
            article.toCreateArticle(userId),
        )

        assertEquals(
            GetFeed(userId = userId, limit = FeedLimit(3), offset = FeedOffset(2)),
            FeedParameters(
                    mutableMapOf(
                        "offset" to listOf("2"),
                        "limit" to listOf("3"),
                    )
                )
                .toGetFeed(userId),
        )

        assertEquals(
            GetArticles(
                limit = FeedLimit(5),
                offset = FeedOffset(4),
                author = null,
                favorited = null,
                tag = "kotlin",
                currentUserId = userId,
            ),
            ArticlesParameters(
                    mutableMapOf(
                        "tag" to listOf("kotlin"),
                        "offset" to listOf("4"),
                        "limit" to listOf("5"),
                    )
                )
                .toGetArticles(userId),
        )
    }
}
