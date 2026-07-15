package io.github.nomisrev.articles

import arrow.core.raise.context.ensureNotNull
import io.github.nomisrev.Api.Articles
import io.github.nomisrev.MissingParameter
import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.auth.authenticateWith
import io.github.nomisrev.auth.principal
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.route
import io.github.nomisrev.users.UserId
import io.github.nomisrev.users.UserService
import io.ktor.server.routing.Route
import opensavvy.spine.server.respond

fun Route.commentRoutes(
    userService: UserService,
    articleService: ArticleService,
    jwtService: JwtConfig<JwtContext>,
) {
    route(Articles.Slug.Comments.list) {
        val comments = articleService.getCommentsForSlug(Slug(idOf(Articles.Slug)))
        respond(MultipleCommentsResponse(comments))
    }

    authenticateWith(jwtService) {
        route(Articles.Slug.Comments.create) {
            val comment =
                articleService.insertComment(
                    body.comment.toCreateComment(
                        slug = Slug(idOf(Articles.Slug)),
                        userId = call.principal.userId,
                    )
                )
            val user = userService.getUser(UserId(comment.author))

            respond(
                SingleCommentResponse(
                    Comment(
                        id = comment.id,
                        createdAt = comment.createdAt,
                        updatedAt = comment.updatedAt,
                        body = comment.body,
                        author =
                            Profile(
                                username = user.username.value,
                                bio = user.bio,
                                image = user.image,
                                following = false,
                            ),
                    )
                )
            )
        }

        route(Articles.Slug.Comments.Id.delete) {
            val commentId =
                ensureNotNull(idOf(Articles.Slug.Comments.Id).toLongOrNull()) {
                    MissingParameter("commentId must be a number")
                }
            articleService.deleteComment(commentId = commentId, userId = call.principal.userId)
            respond(code = io.ktor.http.HttpStatusCode.OK)
        }
    }
}
