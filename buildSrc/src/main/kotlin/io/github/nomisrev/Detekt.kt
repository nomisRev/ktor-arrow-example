import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

fun Project.setupDetekt() {
  plugins.apply("io.gitlab.arturbosch.detekt")

  configure<DetektExtension> {
    parallel = true
    buildUponDefaultConfig = true
    allRules = true
    source = files(file("src").listFiles()?.find { it.isDirectory } ?: emptyArray<Any>())
  }

  tasks.withType<Detekt>().configureEach {
    reports {
      html.required by true
      sarif.required by true
      txt.required by false
      xml.required by false
    }
  }

  tasks.register("detektAll") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.withType<Detekt>())
  }

  tasks.configureEach {
    if (name == "build") dependsOn("detektAll")
  }
}