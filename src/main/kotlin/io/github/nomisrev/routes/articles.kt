package io.github.nomisrev.routes

import arrow.core.raise.either
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.service.ArticleService
import io.github.nomisrev.service.CreateArticle
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.validate
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.OffsetDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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

@Serializable data class ArticleWrapper<T : Any>(val article: T)

@Serializable
data class NewArticle(
  val title: String,
  val description: String,
  val body: String,
  val tagList: List<String>? = ArrayList()
)

@Serializable
data class ArticleResponse(
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

private object OffsetDateTimeIso8601Serializer : KSerializer<OffsetDateTime> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): OffsetDateTime =
    OffsetDateTime.parse(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: OffsetDateTime) {
    encoder.encodeString(value.toString())
  }
}

fun Route.articleRoutes(articleService: ArticleService, jwtService: JwtService) {
  route("/articles") {
    post {
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
                  article.tagList?.toSet() ?: emptySet()
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
}
