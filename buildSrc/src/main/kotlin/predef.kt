import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency
import org.gradle.api.provider.Property

val Provider<PluginDependency>.pluginId: String
  get() = get().pluginId
