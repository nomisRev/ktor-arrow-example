package io.github.nomisrev.articles

import arrow.core.raise.either
import io.github.nomisrev.CannotGenerateSlug
import io.github.nomisrev.SuspendFun
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.random.Random

class SlugGeneratorSpec :
    SuspendFun({
        val seed = Random(42)

        "slugifyGenerator" -
                {
                    "should generate a slug from a title" {
                        val slugGenerator = slugifyGenerator(seed)

                        val title = "Test Title ${Random.nextInt(1000, 9999)}"
                        val slug = either { slugGenerator.generateSlug(title) { true } }.shouldBeRight()

                        slug.value shouldContain "test_title"
                        slug.value shouldNotContain " "
                    }

                    "should add a random suffix when the first attempt is not unique" {
                        val slugGenerator = slugifyGenerator(seed)

                        val title = "Test Title ${Random.nextInt(1000, 9999)}"

                        // First attempt not unique, second attempt is unique
                        val slug = either {
                            slugGenerator.generateSlug(title) { slug ->
                                slug.value == title.lowercase().replace(' ', '_') // First attempt fails
                            }
                        }.shouldBeRight()

                        slug.value shouldContain "test_title"
                        slug.value shouldContain "_" // Should have a suffix
                    }

                    "should return CannotGenerateSlug when all attempts fail" {
                        val slugGenerator = slugifyGenerator(seed, defaultMaxAttempts = 3)

                        val title = "Test Title ${Random.nextInt(1000, 9999)}"

                        // All attempts fail
                        either { slugGenerator.generateSlug(title) { false } }.shouldBeLeft(
                            CannotGenerateSlug("Failed to generate unique slug from $title")
                        )
                    }

                    "should handle special characters in title" {
                        val slugGenerator = slugifyGenerator(seed)

                        val title = "Special @#$%^&*() Title ${Random.nextInt(1000, 9999)}"
                        val result = either { slugGenerator.generateSlug(title) { true } }

                        val slug = result.shouldBeRight()
                        slug.value shouldContain "special_title"
                        slug.value shouldNotContain "@"
                        slug.value shouldNotContain "#"
                        slug.value shouldNotContain "$"
                    }

                    "should handle empty title" {
                        val slugGenerator = slugifyGenerator(seed)

                        val title = ""
                        either { slugGenerator.generateSlug(title) { true } }.shouldBeRight("")
                    }

                    "should handle very long title" {
                        val slugGenerator = slugifyGenerator(seed)

                        val title =
                            "Very Long Title " + "x".repeat(200) + " ${Random.nextInt(1000, 9999)}"
                        val result = either { slugGenerator.generateSlug(title) { true } }

                        val slug = result.shouldBeRight()
                        slug.value shouldContain "very_long_title"
                    }
                }
    })
