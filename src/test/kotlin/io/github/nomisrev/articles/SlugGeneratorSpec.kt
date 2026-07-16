package io.github.nomisrev.articles

import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.CannotGenerateSlug
import io.github.nomisrev.articles.Title
import io.github.nomisrev.assertRaised
import io.github.nomisrev.testRaise
import kotlin.random.Random
import org.junit.Assert.assertEquals

@Suppress("RETURN_VALUE_NOT_USED_COERCION")
val SlugGeneratorSuite by testSuite {
    val seed = Random(42)

    testRaise("should generate a slug from a title") {
        val slugGenerator = slugifyGenerator(seed)

        val title = Title("Test Title")

        assertEquals(
            "test_title",
            slugGenerator.generateSlug(title) { true }.value,
        )
    }

    testRaise("should add a random suffix when the first attempt is not unique") {
        val slugGenerator = slugifyGenerator(seed)

        val title = Title("Test Title")

        val slug =
            slugGenerator.generateSlug(title) { slug ->
                slug.value != title.value.lowercase().replace(' ', '_')
            }

        assertEquals("test_title_142", slug.value)
    }

    testRaise("should return CannotGenerateSlug when all attempts fail") {
        val slugGenerator = slugifyGenerator(seed, defaultMaxAttempts = 3)

        val title = Title("Test Title")

        assertEquals(
            CannotGenerateSlug("Failed to generate unique slug from $title"),
            assertRaised { slugGenerator.generateSlug(title) { false } },
        )
    }

    testRaise("should handle special characters in title") {
        val slugGenerator = slugifyGenerator(seed)

        val title = Title("Special @#$%^&*() Title")
        val slug = slugGenerator.generateSlug(title) { true }

        assertEquals("special_title", slug.value)
    }

    testRaise("should handle very long title") {
        val slugGenerator = slugifyGenerator(seed)

        val title = Title("Very Long Title " + "x".repeat(200))
        val slug = slugGenerator.generateSlug(title) { true }

        assertEquals("very_long_title_" + "x".repeat(200), slug.value)
    }
}
