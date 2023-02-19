package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.continuations.EffectScope
import arrow.core.continuations.either
import com.github.slugify.Slugify
import io.github.nomisrev.CannotGenerateSlug
import kotlin.random.Random

@JvmInline value class Slug(val value: String)

fun interface SlugGenerator {
  /**
   * Generates a unique slug by title and a uniqueness check. If a unique slug could not be
   * generated then [CannotGenerateSlug] is returned
   *
   * @param verifyUnique Allows checking uniqueness with some business rules. i.e. check database
   *   that slug is actually unique for domain.
   */
  suspend fun generateSlug(
    title: String,
    verifyUnique: suspend (Slug) -> Boolean
  ): Either<CannotGenerateSlug, Slug>
}

fun slugifyGenerator(
  random: Random = Random.Default,
  defaultMaxAttempts: Int = 5,
  minRandomSuffix: Int = 2,
  maxRandomSuffix: Int = 255
): SlugGenerator =
  object : SlugGenerator {
    private val slg = Slugify.builder().lowerCase(true).underscoreSeparator(true).build()

    private fun makeUnique(slug: String): String =
      "${slug}_${random.nextInt(minRandomSuffix, maxRandomSuffix)}"

    private tailrec suspend fun EffectScope<CannotGenerateSlug>.recursiveGen(
      title: String,
      verifyUnique: suspend (Slug) -> Boolean,
      maxAttempts: Int,
      isFirst: Boolean
    ): Slug {
      ensure(maxAttempts != 0) { CannotGenerateSlug("Failed to generate unique slug from $title") }

      val slug = Slug(if (isFirst) slg.slugify(title) else makeUnique(slg.slugify(title)))

      val isUnique = verifyUnique(slug)
      return if (isUnique) slug else recursiveGen(title, verifyUnique, maxAttempts - 1, false)
    }

    override suspend fun generateSlug(
      title: String,
      verifyUnique: suspend (Slug) -> Boolean
    ): Either<CannotGenerateSlug, Slug> = either {
      recursiveGen(title, verifyUnique, defaultMaxAttempts, true)
    }
  }
