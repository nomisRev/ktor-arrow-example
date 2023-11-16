package io.github.nomisrev.routes

import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.env.Env
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.FavouritePersistence
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.CreateArticle
import io.github.nomisrev.service.Slug
import io.github.nomisrev.service.SlugGenerator
import io.github.nomisrev.service.articleBySlug
import io.github.nomisrev.service.createArticle
import io.github.nomisrev.service.getUserFeed
import io.github.nomisrev.validate
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import java.time.OffsetDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class ArticleWrapper<T : Any>(val article: T)

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

@Serializable
data class SingleArticleResponse(val article: Article)

@Serializable
data class MultipleArticlesResponse(
  val articles: List<Article>,
  val articlesCount: Int,
)

@JvmInline
@Serializable
value class FeedOffset(val offset: Int)

@JvmInline
@Serializable
value class FeedLimit(val limit: Int)

@Serializable
data class Comment(
  val commentId: Long,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
  val body: String,
  val author: Profile
)

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
}

context (
  Env.Auth,
  SlugGenerator,
  ArticlePersistence,
  UserPersistence,
  TagPersistence,
  FavouritePersistence
)
fun Route.articleRoutes() {

  get<ArticleResource.Feed> { feed ->
    jwtAuth { _, userId ->
      conduit(HttpStatusCode.OK) {
        val getFeed = feed.validate(userId).also(::println).bind()

        val articlesFeed = getUserFeed(input = getFeed)
        ArticleWrapper(articlesFeed)
      }
    }
  }

  get<ArticlesResource.Slug> { slug ->
    conduit(HttpStatusCode.OK) {
      SingleArticleResponse(articleBySlug(Slug(slug.slug)))
    }
  }

  post<ArticlesResource> {
    jwtAuth { _, userId ->
      conduit(HttpStatusCode.Created) {
        val newArticle = call.receive<ArticleWrapper<NewArticle>>().article.validate().bind()
        val article = createArticle(
          CreateArticle(
            userId,
            newArticle.title,
            newArticle.description,
            newArticle.body,
            newArticle.tagList.toSet()
          )
        )
        with(article) {
          ArticleResponse(
            slug,
            title,
            description,
            body,
            author,
            favorited,
            favoritesCount,
            createdAt,
            updatedAt,
            tagList
          )
        }
      }
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
