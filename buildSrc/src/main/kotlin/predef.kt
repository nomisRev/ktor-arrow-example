import org.gradle.api.provider.Provider;
import org.gradle.plugin.use.PluginDependency
import org.gradle.api.provider.Property

@Suppress("UnstableApiUsage")
val Provider<PluginDependency>.pluginId: String
    get() = get().pluginId

infix fun <T> Property<T>.by(value: T) {
    set(value)
}
