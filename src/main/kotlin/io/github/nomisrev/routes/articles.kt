package io.github.nomisrev.routes

import arrow.core.Either
import arrow.core.raise.either
import io.github.nomisrev.IncorrectJson
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.service.ArticleService
import io.github.nomisrev.service.GetFeed
import io.github.nomisrev.service.JwtService
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import java.time.OffsetDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
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
  val favoritesCount: Int,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
  val tagList: List<String>
)

@Serializable
data class MultipleArticlesResponse(
  val articles: List<Article>,
  val articlesCount: Int,
)

@Serializable
data class UserFeed(
  val limit: Long,
  val offset: Long,
)

@Serializable
data class Profile(
  val username: String,
  val bio: String,
  val image: String,
  val following: Boolean
)

@Serializable
data class Comment(
  val commentId: Long,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
  @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
  val body: String,
  val author: Profile
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

fun Route.articleRoutes(
  articleService: ArticleService,
  jwtService: JwtService,
) {

  get<ArticleResource.Feed> {
    jwtAuth(jwtService) { (_, userId) ->
      either {
          val (limit, offset) = receiveCatching<ArticleWrapper<UserFeed>>().bind().article
          val articlesFeed =
            articleService.getUserFeed(input = GetFeed(userId, limit, offset)).bind()
          ArticleWrapper(articlesFeed)
        }
        .respond(HttpStatusCode.OK)
    }
  }
}

// TODO improve how we receive models with validation
@OptIn(ExperimentalSerializationApi::class)
private suspend inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>
  .receiveCatching(): Either<IncorrectJson, A> =
  Either.catchOrThrow<MissingFieldException, A> { call.receive() }.mapLeft { IncorrectJson(it) }

private object OffsetDateTimeIso8601Serializer : KSerializer<OffsetDateTime> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): OffsetDateTime =
    OffsetDateTime.parse(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: OffsetDateTime) {
    encoder.encodeString(value.toString())
  }
}
