// This file was automatically generated from knit-testing.md by Knit tool. Do not edit.
package io.github.nomisrev.knit

import kotlinx.knit.test.*
import org.junit.Test

class KnitDocumentationTest {
    @Test
    fun testExampleDocumentation01() {
        captureOutput("ExampleDocumentation01") {
                io.github.nomisrev.knit.exampleDocumentation01.main()
            }
            .verifyOutputLines("Hello, Ktor")
    }
}
