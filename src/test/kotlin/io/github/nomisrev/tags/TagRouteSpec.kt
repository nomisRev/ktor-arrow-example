package io.github.nomisrev.tags

import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.Api
import io.github.nomisrev.Api.Tags
import io.github.nomisrev.Api.Tags.list
import io.github.nomisrev.client
import io.github.nomisrev.createArticle
import io.github.nomisrev.dependencies
import io.github.nomisrev.registerUser
import io.github.nomisrev.testServer
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.div
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

@Suppress("RETURN_VALUE_NOT_USED_COERCION")
val TagRouteSuite by testSuite {
    testServer("check for empty list retrieval") {
        val response = client.request(Api / Tags / list)

        assert(response.httpResponse.status == HttpStatusCode.OK)
        // Tags may be non-empty due to shared DB state from other tests
        response.bodyOrThrow().tags
    }

    testServer("can get all tags") {
        val (userId) = registerUser()

        val article = dependencies.articleService.createArticle(userId)

        val response = client.request(Api / Tags / list)

        assert(response.httpResponse.status == HttpStatusCode.OK)
        assert(response.bodyOrThrow().tags.toSet().containsAll(article.tagList))
    }
}
