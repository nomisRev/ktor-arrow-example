package io.github.nomisrev

import arrow.core.nonEmptyListOf
import arrow.core.raise.either
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
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FreeSpec

fun IncorrectInput(head: InvalidField) = IncorrectInput(nonEmptyListOf(head))

class ValidationSpec :
    FreeSpec({
        "register accumulates all invalid fields and all errors per field" {
            val input = RegisterUser(username = "", email = "not-an-email", password = "")

            either { input.validate() } shouldBeLeft
                    IncorrectInput(
                        nonEmptyListOf(
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
                        )
                    )
        }

        "login accumulates email and password validation errors" {
            val input = Login(email = "", password = "")

            either { input.validate() } shouldBeLeft
                    IncorrectInput(
                        nonEmptyListOf(
                            InvalidEmail(nonEmptyListOf("Cannot be blank", "'' is invalid email")),
                            InvalidPassword(
                                nonEmptyListOf(
                                    "Cannot be blank",
                                    "is too short (minimum is 8 characters)",
                                )
                            ),
                        )
                    )
        }

        "update accumulates errors for every provided invalid nullable field" {
            val input =
                Update(
                    userId = UserId(1),
                    username = "",
                    email = "invalid-email",
                    password = "short",
                    bio = null,
                    image = null,
                )

            either { input.validate() } shouldBeLeft
                    IncorrectInput(
                        nonEmptyListOf(
                            InvalidUsername(
                                nonEmptyListOf(
                                    "Cannot be blank",
                                    "is too short (minimum is 1 characters)",
                                )
                            ),
                            InvalidEmail(nonEmptyListOf("'invalid-email' is invalid email")),
                            InvalidPassword(nonEmptyListOf("is too short (minimum is 8 characters)")),
                        )
                    )
        }

        "update ignores null nullable fields" {
            val input =
                Update(
                    userId = UserId(1),
                    username = null,
                    email = null,
                    password = null,
                    bio = null,
                    image = null,
                )

            either { input.validate() } shouldBeRight input
        }

        "new article accumulates title description body and every invalid tag" {
            val input =
                NewArticle(
                    title = "",
                    description = " ",
                    body = "",
                    tagList = listOf("", "ok", " "),
                )

            either { input.validate() } shouldBeLeft
                    IncorrectInput(
                        nonEmptyListOf(
                            InvalidTitle(nonEmptyListOf("Cannot be blank")),
                            InvalidDescription(nonEmptyListOf("Cannot be blank")),
                            InvalidBody(nonEmptyListOf("Cannot be blank")),
                            InvalidTag(nonEmptyListOf("Cannot be blank", "Cannot be blank")),
                        )
                    )
        }

        "new comment validates body" {
            either { NewComment(body = " ").validate() } shouldBeLeft
                    IncorrectInput(InvalidBody(nonEmptyListOf("Cannot be blank")))
        }

        "feed accumulates offset and limit errors" {
            val userId = UserId(1)
            val input = FeedParameters(mutableMapOf(
                "offset" to listOf("-1"),
                "limit" to listOf("0")
            ))

            either { input.validate(userId) } shouldBeLeft
                    IncorrectInput(
                        nonEmptyListOf(
                            InvalidFeedOffset(nonEmptyListOf("too small, minimum is 0, and found -1")),
                            InvalidFeedLimit(nonEmptyListOf("too small, minimum is 1, and found 0")),
                        )
                    )
        }

        "articles accumulates offset and limit errors" {
            val input = ArticlesParameters(
                mutableMapOf(
                    "offset" to listOf("-1"),
                    "limit" to listOf("0")
                )
            )

            either { input.validate(currentUserId = null) } shouldBeLeft
                    IncorrectInput(
                        nonEmptyListOf(
                            InvalidFeedOffset(nonEmptyListOf("too small, minimum is 0, and found -1")),
                            InvalidFeedLimit(nonEmptyListOf("too small, minimum is 1, and found 0")),
                        )
                    )
        }

        "valid inputs are returned unchanged or mapped to service input" {
            val register = RegisterUser("simon", "simon@example.com", "12345678")
            either { register.validate() } shouldBeRight register

            val article = NewArticle("title", "description", "body", listOf(" kotlin ", "arrow"))
            either { article.validate() } shouldBeRight
                    NewArticle("title", "description", "body", listOf("kotlin", "arrow"))

            either { 0.validFeedOffset() } shouldBeRight FeedOffset(0)
            either { 1.validFeedLimit() } shouldBeRight FeedLimit(1)

            val userId = UserId(42)
            either {
                FeedParameters(mutableMapOf(
                    "offset" to listOf("2"),
                    "limit" to listOf("3")
                )).validate(userId)
            } shouldBeRight GetFeed(userId = userId, limit = 3, offset = 2)

            either {
                ArticlesParameters(
                    mutableMapOf(
                        "tag" to listOf("kotlin"),
                        "offset" to listOf("4"),
                        "limit" to listOf("5")
                    )
                ).validate(userId)
            } shouldBeRight
                    GetArticles(
                        limit = 5,
                        offset = 4,
                        author = null,
                        favorited = null,
                        tag = "kotlin",
                        currentUserId = userId,
                    )
        }
    })
