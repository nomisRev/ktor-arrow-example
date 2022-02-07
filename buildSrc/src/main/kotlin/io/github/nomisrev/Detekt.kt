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
      html.required by true
      sarif.required by true
      txt.required by false
      xml.required by false
    }
  }

  tasks.configureEach {
    if (name == "build") dependsOn(tasks.withType<Detekt>())
  }
}