package io.github.nomisrev

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import org.junit.Test

class ApplicationTest {
  @Test
  fun testHappyBirthday() {
    testApplication {
      application { setup()  }
      val response = client.get("/happy-birthday/Santa/999")
      assertEquals(HttpStatusCode.OK, response.status)
      assertEquals("{\"age\":999,\"name\":\"Santa\"}", response.bodyAsText())
    }
  }
}
