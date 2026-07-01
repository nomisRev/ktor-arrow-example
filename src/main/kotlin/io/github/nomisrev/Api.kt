package io.github.nomisrev

import io.github.nomisrev.articles.ArticleResponse
import io.github.nomisrev.articles.ArticleWrapper
import io.github.nomisrev.articles.CommentWrapper
import io.github.nomisrev.articles.MultipleArticlesResponse
import io.github.nomisrev.articles.MultipleCommentsResponse
import io.github.nomisrev.articles.NewArticle
import io.github.nomisrev.articles.NewComment
import io.github.nomisrev.articles.SingleArticleResponse
import io.github.nomisrev.articles.SingleCommentResponse
import io.github.nomisrev.articles.UpdateArticle
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.profiles.ProfileWrapper
import io.github.nomisrev.tags.TagsResponse
import io.github.nomisrev.users.LoginUser
import io.github.nomisrev.users.NewUser
import io.github.nomisrev.users.UpdateUser
import io.github.nomisrev.users.User
import io.github.nomisrev.users.UserWrapper
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.DynamicResource
import opensavvy.spine.api.RootResource as SpineRootResource
import opensavvy.spine.api.StaticResource

object Api : SpineRootResource("api") {
    object Tags : StaticResource<Api>("tags", Api) {
        val list by
            get()
                .response<TagsResponse>()
                .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
    }

    object Articles : StaticResource<Api>("articles", Api) {
        val list by
            get()
                .response<MultipleArticlesResponse>()
                .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

        val create by
            post()
                .request<ArticleWrapper<NewArticle>>()
                .response<SingleArticleResponse>()
                .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

        object Slug : DynamicResource<Articles>("slug", Articles) {
            val get by
                get()
                    .response<SingleArticleResponse>()
                    .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

            val update by
                put()
                    .request<ArticleWrapper<UpdateArticle>>()
                    .response<ArticleResponse>()
                    .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

            val delete by
                delete()
                    .response<Unit>()
                    .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

            object Favorite : StaticResource<Slug>("favorite", Slug) {
                val add by
                    post()
                        .response<SingleArticleResponse>()
                        .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

                val remove by
                    delete()
                        .response<SingleArticleResponse>()
                        .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
            }

            object Comments : StaticResource<Slug>("comments", Slug) {
                val create by
                    post()
                        .request<CommentWrapper<NewComment>>()
                        .response<SingleCommentResponse>()
                        .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

                val list by
                    get()
                        .response<MultipleCommentsResponse>()
                        .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

                object Id : DynamicResource<Comments>("id", Comments) {
                    val delete by
                        delete()
                            .response<Unit>()
                            .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
                }
            }
        }
    }

    object Article : StaticResource<Api>("article", Api) {
        val feed by
            get("feed")
                .response<MultipleArticlesResponse>()
                .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
    }

    object Profiles : StaticResource<Api>("profiles", Api) {
        object Username : DynamicResource<Profiles>("username", Profiles) {
            val get by
                get()
                    .response<Profile>()
                    .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

            object Follow : StaticResource<Username>("follow", Username) {
                val add by
                    post()
                        .response<ProfileWrapper<Profile>>()
                        .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

                val remove by
                    delete()
                        .response<ProfileWrapper<Profile>>()
                        .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
            }
        }
    }

    object Users : StaticResource<Api>("users", Api) {
        val register by
            post()
                .request<UserWrapper<NewUser>>()
                .response<UserWrapper<User>>()
                .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

        object Login : StaticResource<Users>("login", Users) {
            val authenticate by
                post()
                    .request<UserWrapper<LoginUser>>()
                    .response<UserWrapper<User>>()
                    .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
        }
    }

    object CurrentUser : StaticResource<Api>("user", Api) {
        val get by
            get()
                .response<UserWrapper<User>>()
                .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

        val update by
            put()
                .request<UserWrapper<UpdateUser>>()
                .response<UserWrapper<User>>()
                .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
    }
}
