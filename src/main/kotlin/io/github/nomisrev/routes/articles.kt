package io.github.nomisrev.routes

import arrow.core.raise.either
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.service.ArticleService
import io.github.nomisrev.service.CreateArticle
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.service.Slug
import io.github.nomisrev.service.UserService
import io.github.nomisrev.validate
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import java.time.OffsetDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable data class ArticleWrapper<T : Any>(val article: T)

@Serializable
data class Article(
  val articleId: Long,
  val slug: String,
  val title: String,
  val description: String,
  val body: String,
  val author: Profile,
  val favorited: Boolean,
  val favoritesCount: Long,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
  val tagList: List<String>
)

@Serializable data class SingleArticleResponse(val article: Article)

@Serializable
data class MultipleArticlesResponse(
  val articles: List<Article>,
  val articlesCount: Int,
)

@JvmInline @Serializable value class FeedOffset(val offset: Int)

@JvmInline @Serializable value class FeedLimit(val limit: Int)

@Serializable data class NewComment(val body: String)

@Serializable data class SingleCommentResponse(val comment: Comment)

@Serializable
data class Comment(
  val commentId: Long,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
  val body: String,
  val author: Profile
)

@Serializable data class MultipleCommentsResponse(val comments: List<Comment>)

@Serializable
data class NewArticle(
  val title: String,
  val description: String,
  val body: String,
  val tagList: List<String> = emptyList()
)

@Serializable
data class ArticleResponse(
  val slug: String,
  val title: String,
  val description: String,
  val body: String,
  val author: Profile,
  val favorited: Boolean,
  val favoritesCount: Long,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
  val tagList: List<String>
)

@Resource("/article")
data class ArticleResource(val parent: RootResource = RootResource) {
  @Resource("/feed")
  data class Feed(
    val offsetParam: Int,
    val limitParam: Int = 20,
    val parent: ArticleResource = ArticleResource()
  )
}

@Resource("/articles")
data class ArticlesResource(val parent: RootResource = RootResource) {
  @Resource("{slug}")
  data class Slug(val parent: ArticlesResource = ArticlesResource(), val slug: String)

  @Resource("{slug}/comments")
  data class Comments(val parent: ArticlesResource = ArticlesResource(), val slug: String)
}

fun Route.articleRoutes(
  articleService: ArticleService,
  jwtService: JwtService,
) {

  get<ArticleResource.Feed> { feed ->
    jwtAuth(jwtService) { (_, userId) ->
      either {
          val getFeed = feed.validate(userId).also(::println).bind()

          val articlesFeed = articleService.getUserFeed(input = getFeed)
          ArticleWrapper(articlesFeed)
        }
        .also(::println)
        .respond(HttpStatusCode.OK)
    }
  }

  get<ArticlesResource.Slug> { slug ->
    articleService
      .getArticleBySlug(Slug(slug.slug))
      .map { SingleArticleResponse(it) }
      .respond(HttpStatusCode.OK)
  }

  post<ArticlesResource> {
    jwtAuth(jwtService) { (_, userId) ->
      either {
          val article = call.receive<ArticleWrapper<NewArticle>>().article.validate().bind()
          articleService
            .createArticle(
              CreateArticle(
                userId,
                article.title,
                article.description,
                article.body,
                article.tagList.toSet()
              )
            )
            .map {
              ArticleResponse(
                it.slug,
                it.title,
                it.description,
                it.body,
                it.author,
                it.favorited,
                it.favoritesCount,
                it.createdAt,
                it.updatedAt,
                it.tagList
              )
            }
            .bind()
        }
        .respond(HttpStatusCode.Created)
    }
  }
}

fun Route.commentRoutes(
  userService: UserService,
  articleService: ArticleService,
  jwtService: JwtService
) {
  post<ArticlesResource.Comments> { slug ->
    jwtAuth(jwtService) { (_, userId) ->
      either {
          val comments =
            articleService
              .insertCommentForArticleSlug(
                slug = Slug(slug.slug),
                userId = userId,
                comment = call.receive<NewComment>().validate().bind().body,
              )
              .bind()
          val userProfile = userService.getUser(UserId(comments.author)).bind()
          SingleCommentResponse(
            Comment(
              commentId = comments.id,
              createdAt = comments.createdAt,
              updatedAt = comments.updatedAt,
              body = comments.body,
              author =
                Profile(
                  username = userProfile.username,
                  bio = userProfile.bio,
                  image = userProfile.image,
                  following = false
                )
            )
          )
        }
        .respond(HttpStatusCode.OK)
    }
  }
}

fun Route.commentRoutes(articleService: ArticleService, jwtService: JwtService) {
  get<ArticlesResource.Comments> { slug ->
    jwtAuth(jwtService) { (_, _) ->
      val comments = articleService.getCommentsForSlug(Slug(slug.slug))
      call.respond(MultipleCommentsResponse(comments))
    }
  }
}

private object OffsetDateTimeIso8601Serializer : KSerializer<OffsetDateTime> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): OffsetDateTime =
    OffsetDateTime.parse(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: OffsetDateTime) {
    encoder.encodeString(value.toString())
  }
}
