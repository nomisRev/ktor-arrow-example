package io.github.nomisrev.articles

import io.github.nomisrev.Api.Articles
import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.auth.authenticateWith
import io.github.nomisrev.auth.principal
import io.github.nomisrev.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import opensavvy.spine.server.respond

fun Route.articleRoutes(articleService: ArticleService, jwtService: JwtConfig<JwtContext>) {
    authenticateWith(jwtService.orAnonymous()) {
        route(Articles.list) {
            val input = parameters.toGetArticles(call.principal?.userId)
            val articles = articleService.getAllArticles(input)
            respond(articles)
        }

        route(Articles.Slug.get) {
            val article = articleService.getArticleBySlug(
                Slug(idOf(Articles.Slug)),
                call.principal?.userId,
            )
            respond(SingleArticleResponse(article))
        }
    }

    authenticateWith(jwtService) {
        route(Articles.feed) {
            val input = parameters.toGetFeed(call.principal.userId)
            val feed = articleService.getUserFeed(input)
            respond(feed)
        }

        route(Articles.Slug.update) {
            val input = UpdateArticleInput(
                slug = Slug(idOf(Articles.Slug)),
                userId = call.principal.userId,
                title = body.article.title,
                description = body.article.description,
                body = body.article.body,
            )
            val updatedArticle = articleService.updateArticle(input)
            respond(SingleArticleResponse(updatedArticle))
        }

        route(Articles.Slug.delete) {
            articleService.deleteArticle(Slug(idOf(Articles.Slug)), call.principal.userId)
            respond(code = HttpStatusCode.OK)
        }

        route(Articles.Slug.Favorite.add) {
            val article = articleService.favoriteArticle(
                Slug(idOf(Articles.Slug)),
                call.principal.userId,
            )
            respond(SingleArticleResponse(article))
        }

        route(Articles.Slug.Favorite.remove) {
            val article = articleService.unfavoriteArticle(
                Slug(idOf(Articles.Slug)),
                call.principal.userId,
            )
            respond(SingleArticleResponse(article))
        }

        route(Articles.create) {
            val created = articleService.createArticle(
                body.article.toCreateArticle(call.principal.userId),
            )
            respond(SingleArticleResponse(created), HttpStatusCode.Created)
        }
    }
}
