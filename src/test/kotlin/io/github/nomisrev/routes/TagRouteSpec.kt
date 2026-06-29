package io.github.nomisrev.routes

import arrow.core.flatMap
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.articleFixture
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.service.CreateArticle
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.userFixture
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class TagRouteSpec :
    StringSpec({
        

        "Check for empty list retrieval" {
            withServer {
                val response = get(TagsResource()) { contentType(ContentType.Application.Json) }

                assert(response.status == HttpStatusCode.OK)
                assert(response.body<TagsResponse>().tags == emptyList<String>())
            }
        }

        "Can get all tags" {
            withServer { dependencies ->
                val user = userFixture()
                val userId =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
                        .map { it.claimValueAsLong("id").shouldBeSome() }
                        .shouldBeRight()

                val article = articleFixture()
                dependencies.articleService
                    .createArticle(
                        CreateArticle(
                            UserId(userId),
                            article.title,
                            article.description,
                            article.body,
                            article.tags,
                        )
                    )
                    .shouldBeRight()
                val response = get(TagsResource()) { contentType(ContentType.Application.Json) }

                assert(response.status == HttpStatusCode.OK)
                assert(response.body<TagsResponse>().tags.toSet().containsAll(article.tags))
            }
        }
    })
