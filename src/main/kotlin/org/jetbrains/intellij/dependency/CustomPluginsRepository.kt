package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.model.Category
import org.jetbrains.intellij.model.PluginRepository
import org.jetbrains.intellij.model.Plugins
import org.jetbrains.intellij.parsePluginXml
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

class CustomPluginsRepository(val project: Project, repoUrl: String) : PluginsRepository {

    private var pluginsXmlUri: URI
    private val repoUrl: String

    init {
        val uri = URI(repoUrl)
        if (uri.path.endsWith(".xml")) {
            this.repoUrl = repoUrl.substring(0, repoUrl.lastIndexOf('/'))
            pluginsXmlUri = uri
        } else {
            this.repoUrl = repoUrl
            pluginsXmlUri = URI(uri.scheme, uri.userInfo, uri.host, uri.port, "${uri.path}/", uri.query, uri.fragment).resolve("updatePlugins.xml")
        }

        debug(project, "Loading list of plugins from: $pluginsXmlUri")
    }

    override fun resolve(plugin: PluginDependencyNotation): File? {
        var downloadUrl: String?

        // Try to parse file as <plugin-repository>
        val pluginRepository = parsePluginXml(pluginsXmlUri.toURL().openStream(), PluginRepository::class.java)
        downloadUrl = pluginRepository.categories.flatMap(Category::plugins).find {
            it.id.equals(plugin.id, true) && it.version.equals(plugin.version, true)
        }?.downloadUrl?.let { "$repoUrl/$it" }

        if (downloadUrl == null) {
            // Try to parse XML file as <plugins>
            val plugins = parsePluginXml(pluginsXmlUri.toURL().openStream(), Plugins::class.java)
            downloadUrl = plugins.items.find {
                it.id.equals(plugin.id, true) && it.version.equals(plugin.version, true)
            }?.url
        }

        if (downloadUrl == null) {
            return null
        }

        return downloadZipArtifact(downloadUrl, plugin)
    }

    private fun getCacheDirectoryPath(): String {
        // todo: a better way to define cache directory
        val gradleHomePath = project.gradle.gradleUserHomeDir.absolutePath
        val mavenCacheDirectoryPath = Paths.get(gradleHomePath, "caches/modules-2/files-2.1").toString()
        return Paths.get(mavenCacheDirectoryPath, "com.jetbrains.intellij.idea").toString()
    }

    private fun downloadZipArtifact(url: String, plugin: PluginDependencyNotation): File {
        val targetFile = Paths.get(getCacheDirectoryPath(), "com.jetbrains.plugins", "${plugin.id}-${plugin.version}.zip").toFile()
        if (!targetFile.isFile) {
            targetFile.parentFile.mkdirs()
            Files.copy(URI.create(url).toURL().openStream(), targetFile.toPath())
        }
        return targetFile
    }

    override fun postResolve() {
    }
}
