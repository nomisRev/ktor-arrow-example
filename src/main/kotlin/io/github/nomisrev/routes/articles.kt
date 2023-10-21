package io.github.nomisrev.routes

import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import io.github.nomisrev.service.ArticleService
import io.github.nomisrev.service.Slug
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
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

@Serializable data class SingleArticleResponse(val article: Article)

private object OffsetDateTimeIso8601Serializer : KSerializer<OffsetDateTime> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): OffsetDateTime =
    OffsetDateTime.parse(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: OffsetDateTime) {
    encoder.encodeString(value.toString())
  }
}

fun Route.articleRoutes(articleService: ArticleService) =
  get("/articles/{slug}") {
    either {
        val slug = ensureNotNull(call.parameters["slug"]) { TODO() }
        val article = articleService.getArticleBySlug(Slug(slug)).bind()
        SingleArticleResponse(article)
      }
      .respond(HttpStatusCode.OK)
  }
