package io.github.nomisrev.articles

import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.NotArticleAuthor
import io.github.nomisrev.assertRaised
import io.github.nomisrev.createArticle
import io.github.nomisrev.dependencies
import io.github.nomisrev.registerUser
import io.github.nomisrev.testDependencies
import org.junit.Assert.assertEquals

@Suppress("RETURN_VALUE_NOT_USED_COERCION")
val ArticleServiceSuite by testSuite {
    testDependencies("get empty user feed when the user follows nobody") {
        val user = registerUser()
        val otherUser = registerUser()

        dependencies.articleService.createArticle(otherUser.userId)
        val feed =
            dependencies.articleService.getUserFeed(
                input = GetFeed(userId = user.userId, limit = 20, offset = 0)
            )

        assert(feed.articlesCount == 0)
    }

    testDependencies("get user feed when the user follows another user") {
        val user = registerUser()
        val followed = registerUser()
        val unrelated = registerUser()

        dependencies.userPersistence.followProfile(followed.user.username, user.userId)

        val createdFollowedArticle = dependencies.articleService.createArticle(followed.userId)

        dependencies.articleService.createArticle(unrelated.userId)

        val feed =
            dependencies.articleService.getUserFeed(
                input = GetFeed(userId = user.userId, limit = 20, offset = 0)
            )
        assert(feed.articlesCount == 1)
        assert(feed.articles.single().slug == createdFollowedArticle.slug)
    }

    testDependencies("allows the article author to update their own article") {
        val author = registerUser()
        val created = dependencies.articleService.createArticle(author.userId)

        val updated =
            dependencies.articleService.updateArticle(
                UpdateArticleInput(
                    slug = Slug(created.slug),
                    userId = author.userId,
                    title = "updated-title",
                    description = "updated description",
                    body = "updated body",
                )
            )

        assert(updated.slug == created.slug)
        assert(updated.title == "updated-title")
        assert(updated.description == "updated description")
        assert(updated.body == "updated body")
        assert(updated.author.username == author.user.username)
    }

    testDependencies("rejects users who are not the article author") {
        val author = registerUser()
        val nonAuthor = registerUser()

        val created = dependencies.articleService.createArticle(author.userId)

        val error = assertRaised {
            dependencies.articleService.updateArticle(
                UpdateArticleInput(
                    slug = Slug(created.slug),
                    userId = nonAuthor.userId,
                    title = "updated-title",
                    description = null,
                    body = null,
                )
            )
        }
        assertEquals(NotArticleAuthor(nonAuthor.userId.serial, created.slug), error)
    }
}
