package io.github.nomisrev.tags

import arrow.core.raise.either
import io.github.nomisrev.Api
import io.github.nomisrev.Api.Tags
import io.github.nomisrev.Api.Tags.list
import io.github.nomisrev.articleFixture
import io.github.nomisrev.articles.CreateArticle
import io.github.nomisrev.registerUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.div
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

class TagRouteSpec :
    StringSpec({
        "Check for empty list retrieval" {
            withServer {
                val response = request(Api / Tags / list)

                assert(response.httpResponse.status == HttpStatusCode.OK)
                assert(response.bodyOrThrow().tags == emptyList<String>())
            }
        }

        "Can get all tags" {
            withServer { dependencies ->
                val (_, _, userId) = dependencies.registerUser()

                val article = articleFixture()
                either {
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                userId,
                                article.title,
                                article.description,
                                article.body,
                                article.tags,
                            )
                        )
                    }
                    .shouldBeRight()

                val response = request(Api / Tags / list)

                assert(response.httpResponse.status == HttpStatusCode.OK)
                assert(response.bodyOrThrow().tags.toSet().containsAll(article.tags))
            }
        }
    })
