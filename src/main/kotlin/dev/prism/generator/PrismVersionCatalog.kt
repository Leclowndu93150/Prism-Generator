package dev.prism.generator

import com.intellij.openapi.application.ApplicationManager
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import javax.xml.parsers.DocumentBuilderFactory

object PrismVersionCatalog {

    data class LoaderDefaults(
        val fabricLoaderVersion: String = "",
        val fabricApiVersion: String = "",
        val neoforgeVersion: String = "",
        val forgeVersion: String = "",
        val lexForgeVersion: String = "",
        val legacyForgeVersion: String = "",
    )

    data class Catalog(
        val minecraftVersions: List<String>,
        val defaults: Map<String, LoaderDefaults>,
    )

    private data class CachedCatalog(
        val catalog: Catalog,
        val loadedAtMillis: Long,
    )

    private const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L
    private const val MINECRAFT_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val FABRIC_LOADER_METADATA_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml"
    private const val FABRIC_API_METADATA_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
    private const val NEOFORGE_METADATA_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
    private const val FORGE_METADATA_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"

    private val cache = AtomicReference<CachedCatalog?>()

    private val fallbackCatalog = Catalog(
        minecraftVersions = listOf(
            "26.1.1",
            "26.1",
            "1.21.11",
            "1.21.4",
            "1.21.1",
            "1.20.1",
            "1.19.2",
            "1.18.2",
            "1.12.2",
            "1.7.10",
        ),
        defaults = mapOf(
            "26.1.1" to LoaderDefaults(
                fabricLoaderVersion = "0.19.0",
                fabricApiVersion = "0.145.4+26.1.1",
                neoforgeVersion = "26.1.1.11-beta",
            ),
            "26.1" to LoaderDefaults(
                fabricLoaderVersion = "0.19.0",
                fabricApiVersion = "0.145.1+26.1",
                neoforgeVersion = "26.1.1.11-beta",
            ),
            "1.21.11" to LoaderDefaults(
                fabricLoaderVersion = "0.19.0",
                fabricApiVersion = "0.141.3+1.21.11",
                neoforgeVersion = "21.11.42",
            ),
            "1.21.4" to LoaderDefaults(
                fabricLoaderVersion = "0.19.0",
                fabricApiVersion = "0.119.4+1.21.4",
                neoforgeVersion = "21.4.157",
            ),
            "1.21.1" to LoaderDefaults(
                fabricLoaderVersion = "0.19.0",
                fabricApiVersion = "0.116.10+1.21.1",
                neoforgeVersion = "21.1.224",
                lexForgeVersion = "52.1.2",
            ),
            "1.20.1" to LoaderDefaults(
                fabricLoaderVersion = "0.19.0",
                fabricApiVersion = "0.92.7+1.20.1",
                forgeVersion = "47.4.18",
            ),
            "1.19.2" to LoaderDefaults(
                fabricLoaderVersion = "0.19.0",
                fabricApiVersion = "0.77.0+1.19.2",
                forgeVersion = "43.4.4",
            ),
            "1.18.2" to LoaderDefaults(
                fabricLoaderVersion = "0.19.0",
                fabricApiVersion = "0.77.0+1.18.2",
                forgeVersion = "40.3.12",
            ),
            "1.12.2" to LoaderDefaults(
                legacyForgeVersion = "14.23.5.2864",
            ),
            "1.7.10" to LoaderDefaults(
                legacyForgeVersion = "10.13.4.1614",
            ),
        ),
    )

    fun fallbackCatalog(): Catalog = fallbackCatalog

    fun defaultMinecraftVersion(): String = fallbackCatalog.minecraftVersions.first()

    fun loadCatalogAsync(onLoaded: (Catalog) -> Unit) {
        val cached = cache.get()
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.loadedAtMillis < CACHE_TTL_MILLIS) {
            onLoaded(cached.catalog)
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val catalog = runCatching { fetchCatalog() }.getOrElse { fallbackCatalog }
            cache.set(CachedCatalog(catalog, System.currentTimeMillis()))
            SwingUtilities.invokeLater {
                onLoaded(catalog)
            }
        }
    }

    fun validLoadersFor(mcVersion: String): List<String> = when {
        mcVersion == "1.7.10" || mcVersion == "1.12.2" -> listOf("Legacy Forge")
        supportsNeoForge(mcVersion) && supportsLexForge(mcVersion) -> listOf("Fabric", "NeoForge", "LexForge")
        supportsNeoForge(mcVersion) -> listOf("Fabric", "NeoForge")
        supportsForge(mcVersion) -> listOf("Fabric", "Forge")
        else -> listOf("Fabric")
    }

    private fun fetchCatalog(): Catalog {
        val manifest = fetchText(MINECRAFT_MANIFEST_URL)
        val releaseVersions = parseReleaseVersions(manifest)
            .filter(::isSupportedRelease)
            .take(12)

        val fabricLoaderVersion = readXmlTag(FABRIC_LOADER_METADATA_URL, "release")
            ?: readXmlTag(FABRIC_LOADER_METADATA_URL, "latest")
            ?: fallbackCatalog.defaults.values.firstOrNull()?.fabricLoaderVersion.orEmpty()
        val fabricApiVersions = readXmlVersions(FABRIC_API_METADATA_URL)
        val neoforgeVersions = readXmlVersions(NEOFORGE_METADATA_URL)
        val forgeVersions = readXmlVersions(FORGE_METADATA_URL)

        val minecraftVersions = linkedSetOf<String>()
        minecraftVersions.addAll(releaseVersions)
        minecraftVersions.addAll(fallbackCatalog.minecraftVersions)

        val defaults = minecraftVersions.associateWith { mcVersion ->
            LoaderDefaults(
                fabricLoaderVersion = if (mcVersion == "1.7.10" || mcVersion == "1.12.2") "" else fabricLoaderVersion,
                fabricApiVersion = latestSuffixMatch(fabricApiVersions, "+$mcVersion"),
                neoforgeVersion = if (supportsNeoForge(mcVersion)) {
                    latestPrefixMatch(neoforgeVersions, "${toNeoForgePrefix(mcVersion)}.")
                } else {
                    ""
                },
                forgeVersion = if (supportsForge(mcVersion)) {
                    latestForgeMatch(forgeVersions, mcVersion)
                } else {
                    ""
                },
                lexForgeVersion = if (supportsLexForge(mcVersion)) {
                    latestForgeMatch(forgeVersions, mcVersion)
                } else {
                    ""
                },
                legacyForgeVersion = if (mcVersion == "1.7.10" || mcVersion == "1.12.2") {
                    latestForgeMatch(forgeVersions, mcVersion)
                } else {
                    ""
                },
            )
        }.mapValues { (mcVersion, resolved) ->
            val fallback = fallbackCatalog.defaults[mcVersion] ?: LoaderDefaults()
            LoaderDefaults(
                fabricLoaderVersion = resolved.fabricLoaderVersion.ifBlank { fallback.fabricLoaderVersion },
                fabricApiVersion = resolved.fabricApiVersion.ifBlank { fallback.fabricApiVersion },
                neoforgeVersion = resolved.neoforgeVersion.ifBlank { fallback.neoforgeVersion },
                forgeVersion = resolved.forgeVersion.ifBlank { fallback.forgeVersion },
                lexForgeVersion = resolved.lexForgeVersion.ifBlank { fallback.lexForgeVersion },
                legacyForgeVersion = resolved.legacyForgeVersion.ifBlank { fallback.legacyForgeVersion },
            )
        }

        return Catalog(
            minecraftVersions = minecraftVersions.toList(),
            defaults = defaults,
        )
    }

    private fun fetchText(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("User-Agent", "Prism-Generator")
        return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun readXmlTag(url: String, tagName: String): String? {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(fetchText(url).toByteArray(StandardCharsets.UTF_8)))
        val nodes = document.getElementsByTagName(tagName)
        if (nodes.length == 0) {
            return null
        }
        return nodes.item(0)?.textContent?.trim()?.orEmpty()
    }

    private fun readXmlVersions(url: String): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(fetchText(url).toByteArray(StandardCharsets.UTF_8)))
        val nodes = document.getElementsByTagName("version")
        return buildList(nodes.length) {
            for (i in 0 until nodes.length) {
                val text = nodes.item(i)?.textContent?.trim().orEmpty()
                if (text.isNotBlank()) {
                    add(text)
                }
            }
        }
    }

    private fun parseReleaseVersions(json: String): List<String> {
        val pattern = Regex("""\{[^{}]*"id"\s*:\s*"([^"]+)"[^{}]*"type"\s*:\s*"release"[^{}]*}""")
        return pattern.findAll(json)
            .map { it.groupValues[1] }
            .filter { Regex("""^\d+(\.\d+)+$""").matches(it) }
            .toList()
    }

    private fun isSupportedRelease(version: String): Boolean {
        if (version == "1.7.10" || version == "1.12.2") {
            return true
        }
        return compareVersions(version, "1.14") >= 0 || version.startsWith("26.")
    }

    private fun supportsForge(version: String): Boolean =
        compareVersions(version, "1.17") >= 0 && compareVersions(version, "1.20.1") <= 0

    private fun supportsNeoForge(version: String): Boolean =
        compareVersions(version, "1.20.2") >= 0 || version.startsWith("26.")

    private fun supportsLexForge(version: String): Boolean =
        compareVersions(version, "1.21.1") >= 0 && !version.startsWith("26.")

    private fun toNeoForgePrefix(version: String): String =
        if (version.startsWith("1.")) version.removePrefix("1.") else version

    private fun latestSuffixMatch(versions: List<String>, suffix: String): String =
        versions.filter { it.endsWith(suffix) }.maxWithOrNull(::compareVersions).orEmpty()

    private fun latestPrefixMatch(versions: List<String>, prefix: String): String =
        versions.filter { it.startsWith(prefix) }.maxWithOrNull(::compareVersions).orEmpty()

    private fun latestForgeMatch(versions: List<String>, minecraftVersion: String): String {
        val matches = versions.filter { it.startsWith("$minecraftVersion-") }
        val latest = matches.maxWithOrNull(::compareVersions).orEmpty()
        return latest.substringAfter('-', "")
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftTokens = tokenize(left)
        val rightTokens = tokenize(right)
        val maxSize = maxOf(leftTokens.size, rightTokens.size)

        for (index in 0 until maxSize) {
            val leftToken = leftTokens.getOrNull(index)
            val rightToken = rightTokens.getOrNull(index)

            if (leftToken == rightToken) {
                continue
            }
            if (leftToken == null) {
                return if (hasOnlyQualifierTail(rightTokens, index)) 1 else -1
            }
            if (rightToken == null) {
                return if (hasOnlyQualifierTail(leftTokens, index)) -1 else 1
            }

            val leftNumber = leftToken.toIntOrNull()
            val rightNumber = rightToken.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> 1
                rightNumber != null -> -1
                else -> qualifierRank(leftToken).compareTo(qualifierRank(rightToken))
                    .takeIf { it != 0 }
                    ?: leftToken.compareTo(rightToken)
            }
            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    private fun tokenize(version: String): List<String> =
        Regex("""\d+|[A-Za-z]+""").findAll(version)
            .map { it.value.lowercase() }
            .toList()

    private fun hasOnlyQualifierTail(tokens: List<String>, startIndex: Int): Boolean =
        tokens.drop(startIndex).all { it.toIntOrNull() == null }

    private fun qualifierRank(token: String): Int = when (token.lowercase()) {
        "snapshot", "pre", "preview", "alpha" -> 0
        "beta" -> 1
        "rc" -> 2
        else -> 3
    }
}
