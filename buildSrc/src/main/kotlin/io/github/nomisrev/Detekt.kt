import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

fun Project.setupDetekt() {
  plugins.apply("io.gitlab.arturbosch.detekt")

  configure<DetektExtension> {
    parallel = true
    buildUponDefaultConfig = true
    allRules = true
  }

  tasks.withType<Detekt>().configureEach {
    exclude { "generated/sqldelight" in it.file.absolutePath }
    reports {
      html.required.set(true)
      sarif.required.set(true)
      txt.required.set(false)
      xml.required.set(false)
    }
  }

  tasks.configureEach {
    if (name == "build") dependsOn(tasks.withType<Detekt>())
  }
}