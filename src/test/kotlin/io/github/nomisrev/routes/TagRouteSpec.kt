package io.github.nomisrev.routes

import arrow.core.flatMap
import arrow.core.raise.either
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.articleFixture
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.routes.Api.Tags
import io.github.nomisrev.routes.Api.Tags.list
import io.github.nomisrev.service.CreateArticle
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.userFixture
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
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
                val user = userFixture()
                val userId =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
                        .map { it.claimValueAsLong("id").shouldBeSome() }
                        .shouldBeRight()

                val article = articleFixture()
                either {
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                UserId(userId),
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
