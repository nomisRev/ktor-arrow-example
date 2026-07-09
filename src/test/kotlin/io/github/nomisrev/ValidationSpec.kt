package io.github.nomisrev

import arrow.core.nonEmptyListOf
import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.articles.ArticlesParameters
import io.github.nomisrev.articles.FeedLimit
import io.github.nomisrev.articles.FeedOffset
import io.github.nomisrev.articles.FeedParameters
import io.github.nomisrev.articles.GetArticles
import io.github.nomisrev.articles.GetFeed
import io.github.nomisrev.articles.NewArticle
import io.github.nomisrev.articles.NewComment
import io.github.nomisrev.users.Login
import io.github.nomisrev.users.RegisterUser
import io.github.nomisrev.users.Update
import io.github.nomisrev.users.UserId
import org.junit.Assert.assertEquals

fun IncorrectInput(head: InvalidField, vararg tail: InvalidField) =
    IncorrectInput(nonEmptyListOf(head, *tail))

@Suppress("RETURN_VALUE_NOT_USED_COERCION")
val Validation by testSuite {
    test("accumulates all invalid fields and all errors per field") {
        val input = RegisterUser(username = "", email = "not-an-email", password = "")

        val error = assertRaised { input.validate() }

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
                    )
                ),
            ),
            error
        )
    }

    test("accumulates email and password validation errors") {
        val input = Login(email = "", password = "")

        val error = assertRaised { input.validate() }

        assertEquals(
            IncorrectInput(
                InvalidEmail(nonEmptyListOf("Cannot be blank", "'' is invalid email")),
                InvalidPassword(
                    nonEmptyListOf(
                        "Cannot be blank",
                        "is too short (minimum is 8 characters)",
                    )
                ),
            ),
            error
        )
    }

    test("accumulates errors for every provided invalid nullable field") {
        val input =
            Update(
                userId = UserId(1),
                username = "",
                email = "invalid-email",
                password = "short",
                bio = null,
                image = null,
            )

        val error = assertRaised { input.validate() }

        assertEquals(
            IncorrectInput(
                InvalidUsername(
                    nonEmptyListOf(
                        "Cannot be blank",
                        "is too short (minimum is 1 characters)",
                    )
                ),
                InvalidEmail(nonEmptyListOf("'invalid-email' is invalid email")),
                InvalidPassword(nonEmptyListOf("is too short (minimum is 8 characters)")),
            ),
            error
        )
    }

    testRaise("ignores null nullable fields") {
        val input =
            Update(
                userId = UserId(1),
                username = null,
                email = null,
                password = null,
                bio = null,
                image = null,
            )

        assertEquals(input, input.validate())
    }

    test("accumulates title description body and every invalid tag") {
        val input =
            NewArticle(
                title = "",
                description = " ",
                body = "",
                tagList = listOf("", "ok", " "),
            )

        val error = assertRaised { input.validate() }

        assertEquals(
            IncorrectInput(
                InvalidTitle(nonEmptyListOf("Cannot be blank")),
                InvalidDescription(nonEmptyListOf("Cannot be blank")),
                InvalidBody(nonEmptyListOf("Cannot be blank")),
                InvalidTag(nonEmptyListOf("Cannot be blank", "Cannot be blank")),
            ),
            error
        )
    }

    test("validates body") {
        val error = assertRaised { NewComment(body = " ").validate() }

        assertEquals(
            IncorrectInput(InvalidBody(nonEmptyListOf("Cannot be blank"))),
            error
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

        val error = assertRaised { input.validate(userId) }

        assertEquals(
            IncorrectInput(
                InvalidFeedOffset(nonEmptyListOf("too small, minimum is 0, and found -1")),
                InvalidFeedLimit(nonEmptyListOf("too small, minimum is 1, and found 0")),
            ),
            error
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

        val error = assertRaised { input.validate(currentUserId = null) }

        assertEquals(
            IncorrectInput(
                InvalidFeedOffset(nonEmptyListOf("too small, minimum is 0, and found -1")),
                InvalidFeedLimit(nonEmptyListOf("too small, minimum is 1, and found 0")),
            ),
            error
        )
    }

    testRaise("returns valid inputs unchanged or mapped to service input") {
        val register = RegisterUser("simon", "simon@example.com", "12345678")
        assertEquals(register, register.validate())

        val article = NewArticle("title", "description", "body", listOf(" kotlin ", "arrow"))
        assertEquals(
            NewArticle("title", "description", "body", listOf("kotlin", "arrow")),
            article.validate()
        )

        assertEquals(FeedOffset(0), 0.validFeedOffset())
        assertEquals(FeedLimit(1), 1.validFeedLimit())

        val userId = UserId(42)
        assertEquals(
            GetFeed(userId = userId, limit = 3, offset = 2),
            FeedParameters(
                mutableMapOf(
                    "offset" to listOf("2"),
                    "limit" to listOf("3"),
                )
            )
                .validate(userId)
        )

        assertEquals(
            GetArticles(
                limit = 5,
                offset = 4,
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
                .validate(userId)
        )
    }
}
