package io.github.nomisrev.service

import arrow.core.raise.Raise
import arrow.core.raise.ensure
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
  context(Raise<CannotGenerateSlug>)
  suspend fun generateSlug(
    title: String,
    verifyUnique: suspend (Slug) -> Boolean
  ): Slug
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

    context(Raise<CannotGenerateSlug>)
    private tailrec suspend fun recursiveGen(
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

    context(Raise<CannotGenerateSlug>)
    override suspend fun generateSlug(
      title: String,
      verifyUnique: suspend (Slug) -> Boolean
    ): Slug = recursiveGen(title, verifyUnique, defaultMaxAttempts, true)
  }
