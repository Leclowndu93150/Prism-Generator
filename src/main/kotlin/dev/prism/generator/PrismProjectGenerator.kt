package dev.prism.generator

import java.io.File

/**
 * Generates the full Prism multi-version Minecraft mod project on disk.
 */
object PrismProjectGenerator {

    data class PrismConfig(
        val projectPath: String,
        val projectName: String,
        val modId: String,
        val modName: String,
        val groupId: String,
        val license: String,
        val versions: List<VersionConfig>,
        val enableKotlin: Boolean,
        val enableSharedCommon: Boolean,
        val enableMixins: Boolean,
        val enableAccessFiles: Boolean,
    )

    data class VersionConfig(
        val mcVersion: String,
        val multiLoader: Boolean,
        val loaders: List<String>,
        val fabricLoaderVersion: String = "",
        val fabricApiVersion: String = "",
        val neoforgeVersion: String = "",
        val forgeVersion: String = "",
        val legacyForgeVersion: String = "",
    )

    fun generate(config: PrismConfig) {
        val root = File(config.projectPath)
        root.mkdirs()

        writeGitignore(root)
        writeGradleProperties(root, config)
        writeSettingsGradle(root, config)
        writeBuildGradle(root, config)
        writeGradleWrapper(root)
        if (config.enableSharedCommon) {
            generateSharedCommonDir(root, config)
        }

        for (version in config.versions) {
            generateVersionDir(root, config, version)
        }
    }

    // -------------------------------------------------------------------------
    // .gitignore
    // -------------------------------------------------------------------------
    private fun writeGitignore(root: File) {
        root.resolve(".gitignore").writeText(
            """
            |.gradle/
            |build/
            |!gradle/wrapper/gradle-wrapper.jar
            |.kotlin
            |.idea/
            |*.iml
            |*.ipr
            |*.iws
            |out/
            |runs/
            |run/
            |**/run/
            |.DS_Store
            """.trimMargin() + "\n"
        )
    }

    // -------------------------------------------------------------------------
    // gradle.properties
    // -------------------------------------------------------------------------
    private fun writeGradleProperties(root: File, config: PrismConfig) {
        root.resolve("gradle.properties").writeText(
            buildString {
                appendLine("org.gradle.jvmargs=-Xmx3G")
                appendLine("org.gradle.daemon=false")
            }
        )
    }

    // -------------------------------------------------------------------------
    // settings.gradle.kts
    // -------------------------------------------------------------------------
    private fun writeSettingsGradle(root: File, config: PrismConfig) {
        val needsGtnh = config.versions.any { vc -> vc.loaders.contains("legacyforge") }

        root.resolve("settings.gradle.kts").writeText(
            buildString {
                appendLine("pluginManagement {")
                appendLine("    repositories {")
                appendLine("        maven { url = uri(\"https://maven.leclowndu93150.dev/releases\") }")
                appendLine("        gradlePluginPortal()")
                appendLine("        mavenCentral()")
                appendLine("        maven { url = uri(\"https://maven.fabricmc.net/\") }")
                appendLine("        maven { url = uri(\"https://maven.neoforged.net/releases\") }")
                appendLine("        maven { url = uri(\"https://repo.spongepowered.org/repository/maven-public/\") }")
                if (needsGtnh) {
                    appendLine("        maven { url = uri(\"https://nexus.gtnewhorizons.com/repository/public/\") }")
                }
                appendLine("    }")
                appendLine("}")
                appendLine()
                appendLine("plugins {")
                appendLine("    id(\"org.gradle.toolchains.foojay-resolver-convention\") version \"0.9.0\"")
                appendLine("    id(\"dev.prism.settings\") version \"+\"")
                appendLine("}")
                appendLine()
                appendLine("rootProject.name = \"${config.projectName}\"")
                appendLine()
                appendLine("prism {")

                if (config.enableSharedCommon) {
                    appendLine("    sharedCommon()")
                    appendLine()
                }

                for (version in config.versions) {
                    appendLine("    version(\"${version.mcVersion}\") {")
                    if (version.multiLoader) {
                        appendLine("        common()")
                        for (loader in version.loaders) {
                            appendLine("        ${loaderSettingsName(loader)}()")
                        }
                    } else {
                        val loader = version.loaders.firstOrNull() ?: continue
                        appendLine("        ${loaderSettingsName(loader)}()")
                    }
                    appendLine("    }")
                }

                appendLine("}")
            }
        )
    }

    private fun loaderSettingsName(loader: String): String = when (loader) {
        "fabric" -> "fabric"
        "neoforge" -> "neoforge"
        "forge" -> "forge"
        "legacyforge" -> "legacyForge"
        else -> loader
    }

    // -------------------------------------------------------------------------
    // build.gradle.kts (root)
    // -------------------------------------------------------------------------
    private fun writeBuildGradle(root: File, config: PrismConfig) {
        root.resolve("build.gradle.kts").writeText(
            buildString {
                appendLine("plugins {")
                appendLine("    id(\"dev.prism\")")
                appendLine("}")
                appendLine()
                appendLine("group = \"${config.groupId}\"")
                appendLine("version = \"1.0.0\"")
                appendLine()
                appendLine("prism {")
                appendLine("    metadata {")
                appendLine("        modId = \"${config.modId}\"")
                appendLine("        name = \"${config.modName}\"")
                appendLine("        description = \"A Minecraft mod.\"")
                appendLine("        license = \"${config.license}\"")
                appendLine("    }")
                appendLine()

                for (version in config.versions) {
                    appendLine("    version(\"${version.mcVersion}\") {")

                    if (config.enableKotlin) {
                        appendLine("        kotlin()")
                    }

                    for (loader in version.loaders) {
                        val defaults = PrismVersionCatalog.fallbackCatalog().defaults[version.mcVersion]
                        when (loader) {
                            "fabric" -> {
                                val fl = version.fabricLoaderVersion.ifBlank { defaults?.fabricLoaderVersion.orEmpty() }
                                val fa = version.fabricApiVersion.ifBlank { defaults?.fabricApiVersion.orEmpty() }
                                appendLine("        fabric {")
                                appendLine("            loaderVersion = \"$fl\"")
                                if (fa.isNotBlank()) {
                                    appendLine("            fabricApi(\"$fa\")")
                                }
                                appendLine("        }")
                            }
                            "neoforge" -> {
                                val nv = version.neoforgeVersion.ifBlank { defaults?.neoforgeVersion.orEmpty() }
                                appendLine("        neoforge {")
                                appendLine("            loaderVersion = \"$nv\"")
                                appendLine("            loaderVersionRange = \"[4,)\"")
                                appendLine("        }")
                            }
                            "forge" -> {
                                val fv = version.forgeVersion.ifBlank { defaults?.forgeVersion.orEmpty() }
                                appendLine("        forge {")
                                appendLine("            loaderVersion = \"$fv\"")
                                appendLine("            loaderVersionRange = \"[${fv.split(".").firstOrNull() ?: "47"},)\"")
                                appendLine("        }")
                            }
                            "legacyforge" -> {
                                val lfv = version.legacyForgeVersion.ifBlank { defaults?.legacyForgeVersion.orEmpty() }
                                appendLine("        legacyForge {")
                                appendLine("            mcVersion = \"${version.mcVersion}\"")
                                appendLine("            forgeVersion = \"$lfv\"")
                                appendLine("        }")
                            }
                        }
                    }

                    appendLine("    }")
                    appendLine()
                }

                appendLine("}")
            }
        )
    }

    // -------------------------------------------------------------------------
    // Gradle wrapper
    // -------------------------------------------------------------------------
    private fun writeGradleWrapper(root: File) {
        val wrapperDir = root.resolve("gradle/wrapper")
        wrapperDir.mkdirs()
        wrapperDir.resolve("gradle-wrapper.properties").writeText(
            buildString {
                appendLine("distributionBase=GRADLE_USER_HOME")
                appendLine("distributionPath=wrapper/dists")
                appendLine("distributionUrl=https\\://services.gradle.org/distributions/gradle-9.2.0-bin.zip")
                appendLine("networkTimeout=10000")
                appendLine("validateDistributionUrl=true")
                appendLine("zipStoreBase=GRADLE_USER_HOME")
                appendLine("zipStorePath=wrapper/dists")
            }
        )

        copyBundledResource("/gradle-wrapper/gradlew", root.resolve("gradlew"))
        copyBundledResource("/gradle-wrapper/gradlew.bat", root.resolve("gradlew.bat"))
        copyBundledResource("/gradle-wrapper/gradle-wrapper.jar", wrapperDir.resolve("gradle-wrapper.jar"))

        root.resolve("gradlew").setExecutable(true, false)
    }

    private fun copyBundledResource(resourcePath: String, target: File) {
        val resourceStream = PrismProjectGenerator::class.java.getResourceAsStream(resourcePath)
            ?: error("Missing bundled resource: $resourcePath")

        target.parentFile?.mkdirs()
        resourceStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun generateSharedCommonDir(root: File, config: PrismConfig) {
        val packagePath = config.groupId.replace('.', '/') + "/" + config.modId
        root.resolve("common/src/main/java/$packagePath").mkdirs()
        root.resolve("common/src/main/resources").mkdirs()
    }

    // -------------------------------------------------------------------------
    // Per-version directory generation
    // -------------------------------------------------------------------------
    private fun generateVersionDir(root: File, config: PrismConfig, version: VersionConfig) {
        val versionDir = root.resolve("versions/${version.mcVersion}")
        versionDir.mkdirs()

        val packagePath = config.groupId.replace('.', '/') + "/" + config.modId
        val className = config.modId.split('_', '-').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

        if (version.multiLoader) {
            val commonSrc = versionDir.resolve("common/src/main/java/$packagePath")
            commonSrc.mkdirs()
            versionDir.resolve("common/src/main/resources").mkdirs()
            writeCommonModClass(commonSrc, config, className)

            for (loader in version.loaders) {
                val loaderDir = versionDir.resolve(loader)
                val loaderSrc = loaderDir.resolve("src/main/java/$packagePath")
                loaderSrc.mkdirs()
                writeLoaderEntryPoint(loaderSrc, config, version, loader, className)
                writeMetadataFiles(loaderDir, config, version, loader)
                if (config.enableMixins) {
                    writeMixinConfig(loaderDir, config)
                }
                if (config.enableAccessFiles) {
                    writeAccessFile(loaderDir, loader, config.modId)
                }
            }
        } else {
            val loader = version.loaders.firstOrNull() ?: return
            val srcDir = versionDir.resolve("src/main/java/$packagePath")
            srcDir.mkdirs()
            writeLoaderEntryPoint(srcDir, config, version, loader, className)
            writeMetadataFiles(versionDir, config, version, loader)
            if (config.enableMixins) {
                writeMixinConfig(versionDir, config)
            }
            if (config.enableAccessFiles) {
                writeAccessFile(versionDir, loader, config.modId)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Common mod class (for multi-loader)
    // -------------------------------------------------------------------------
    private fun writeCommonModClass(dir: File, config: PrismConfig, className: String) {
        dir.resolve("$className.java").writeText(
            buildString {
                appendLine("package ${config.groupId}.${config.modId};")
                appendLine()
                appendLine("import org.slf4j.Logger;")
                appendLine("import org.slf4j.LoggerFactory;")
                appendLine()
                appendLine("public class $className {")
                appendLine("    public static final String MOD_ID = \"${config.modId}\";")
                appendLine("    public static final String MOD_NAME = \"${config.modName}\";")
                appendLine("    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);")
                appendLine()
                appendLine("    public static void init() {")
                appendLine("        LOGGER.info(\"{} initializing!\", MOD_NAME);")
                appendLine("    }")
                appendLine("}")
            }
        )
    }

    // -------------------------------------------------------------------------
    // Loader entry points
    // -------------------------------------------------------------------------
    private fun writeLoaderEntryPoint(
        dir: File,
        config: PrismConfig,
        version: VersionConfig,
        loader: String,
        className: String
    ) {
        when (loader) {
            "fabric" -> writeFabricEntryPoint(dir, config, version, className)
            "neoforge" -> writeNeoForgeEntryPoint(dir, config, version, className)
            "forge" -> writeForgeEntryPoint(dir, config, version, className)
            "legacyforge" -> writeLegacyForgeEntryPoint(dir, config, version, className)
        }
    }

    private fun writeFabricEntryPoint(dir: File, config: PrismConfig, version: VersionConfig, className: String) {
        val entryName = "${className}Fabric"
        dir.resolve("$entryName.java").writeText(
            buildString {
                appendLine("package ${config.groupId}.${config.modId};")
                appendLine()
                appendLine("import net.fabricmc.api.ModInitializer;")
                appendLine()
                appendLine("public class $entryName implements ModInitializer {")
                appendLine()
                appendLine("    @Override")
                appendLine("    public void onInitialize() {")
                if (version.multiLoader) {
                    appendLine("        ${className}.init();")
                } else {
                    appendLine("        System.out.println(\"${config.modName} initializing!\");")
                }
                appendLine("    }")
                appendLine("}")
            }
        )
    }

    private fun writeNeoForgeEntryPoint(dir: File, config: PrismConfig, version: VersionConfig, className: String) {
        val entryName = "${className}NeoForge"
        dir.resolve("$entryName.java").writeText(
            buildString {
                appendLine("package ${config.groupId}.${config.modId};")
                appendLine()
                appendLine("import net.neoforged.fml.common.Mod;")
                appendLine()
                appendLine("@Mod(\"${config.modId}\")")
                appendLine("public class $entryName {")
                appendLine()
                appendLine("    public $entryName() {")
                if (version.multiLoader) {
                    appendLine("        ${className}.init();")
                } else {
                    appendLine("        System.out.println(\"${config.modName} initializing!\");")
                }
                appendLine("    }")
                appendLine("}")
            }
        )
    }

    private fun writeForgeEntryPoint(dir: File, config: PrismConfig, version: VersionConfig, className: String) {
        val entryName = "${className}Forge"
        dir.resolve("$entryName.java").writeText(
            buildString {
                appendLine("package ${config.groupId}.${config.modId};")
                appendLine()
                appendLine("import net.minecraftforge.fml.common.Mod;")
                appendLine()
                appendLine("@Mod(\"${config.modId}\")")
                appendLine("public class $entryName {")
                appendLine()
                appendLine("    public $entryName() {")
                if (version.multiLoader) {
                    appendLine("        ${className}.init();")
                } else {
                    appendLine("        System.out.println(\"${config.modName} initializing!\");")
                }
                appendLine("    }")
                appendLine("}")
            }
        )
    }

    private fun writeLegacyForgeEntryPoint(dir: File, config: PrismConfig, version: VersionConfig, className: String) {
        val entryName = "${className}LegacyForge"
        if (version.mcVersion == "1.7.10" || version.mcVersion == "1.12.2") {
            val modPackage = if (version.mcVersion == "1.7.10") "cpw.mods.fml.common" else "net.minecraftforge.fml.common"
            val eventPackage = if (version.mcVersion == "1.7.10") "cpw.mods.fml.common.event" else "net.minecraftforge.fml.common.event"
            dir.resolve("$entryName.java").writeText(
                buildString {
                    appendLine("package ${config.groupId}.${config.modId};")
                    appendLine()
                    appendLine("import $modPackage.Mod;")
                    appendLine("import $eventPackage.FMLInitializationEvent;")
                    appendLine()
                    appendLine("@Mod(modid = \"${config.modId}\", name = \"${config.modName}\", version = \"1.0.0\")")
                    appendLine("public class $entryName {")
                    appendLine()
                    appendLine("    @Mod.EventHandler")
                    appendLine("    public void init(FMLInitializationEvent event) {")
                    if (version.multiLoader) {
                        appendLine("        ${className}.init();")
                    } else {
                        appendLine("        System.out.println(\"${config.modName} initializing!\");")
                    }
                    appendLine("    }")
                    appendLine("}")
                }
            )
        } else {
            // Fallback: same as forge
            writeForgeEntryPoint(dir, config, version, className)
        }
    }

    // -------------------------------------------------------------------------
    // Metadata files
    // -------------------------------------------------------------------------
    private fun writeMetadataFiles(loaderDir: File, config: PrismConfig, version: VersionConfig, loader: String) {
        val resourcesDir = loaderDir.resolve("src/main/resources")
        resourcesDir.mkdirs()

        when (loader) {
            "fabric" -> writeFabricModJson(resourcesDir, config, version)
            "neoforge" -> writeNeoForgeModsToml(resourcesDir, config, version)
            "forge" -> writeForgeModsToml(resourcesDir, config, version)
            "legacyforge" -> writeMcmodInfo(resourcesDir, config, version)
        }

        if (loader != "legacyforge") {
            writePackMcmeta(resourcesDir, config, version)
        }
    }

    private fun writePackMcmeta(resourcesDir: File, config: PrismConfig, version: VersionConfig) {
        val packFormat = getPackFormat(version.mcVersion)
        resourcesDir.resolve("pack.mcmeta").writeText(
            buildString {
                appendLine("{")
                appendLine("  \"pack\": {")
                appendLine("    \"description\": \"${config.modName}\",")
                appendLine("    \"pack_format\": $packFormat")
                appendLine("  }")
                appendLine("}")
            }
        )
    }

    private fun getPackFormat(mcVersion: String): Int {
        val parts = mcVersion.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

        return when {
            major >= 26 -> 55
            minor >= 21 && patch >= 4 -> 46
            minor >= 21 && patch >= 1 -> 34
            minor >= 21 -> 34
            minor >= 20 && patch >= 4 -> 26
            minor >= 20 && patch >= 2 -> 18
            minor >= 20 -> 15
            minor >= 19 && patch >= 4 -> 13
            minor >= 19 -> 9
            minor >= 18 -> 8
            minor >= 17 -> 7
            minor >= 16 && patch >= 2 -> 6
            else -> 6
        }
    }

    private fun writeFabricModJson(resourcesDir: File, config: PrismConfig, version: VersionConfig) {
        val className = config.modId.split('_', '-').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        val entryClass = "${config.groupId}.${config.modId}.${className}Fabric"
        val modId = placeholder("mod_id")
        val versionPlaceholder = placeholder("version")
        val modName = placeholder("mod_name")
        val description = placeholder("description")
        val license = placeholder("license")
        val fabricLoaderVersion = placeholder("fabric_loader_version")
        val minecraftVersion = placeholder("minecraft_version")
        val javaVersion = placeholder("java_version")

        resourcesDir.resolve("fabric.mod.json").writeText(
            buildString {
                appendLine("{")
                appendLine("  \"schemaVersion\": 1,")
                appendLine("  \"id\": \"$modId\",")
                appendLine("  \"version\": \"$versionPlaceholder\",")
                appendLine("  \"name\": \"$modName\",")
                appendLine("  \"description\": \"$description\",")
                appendLine("  \"license\": \"$license\",")
                appendLine("  \"entrypoints\": {")
                appendLine("    \"main\": [")
                appendLine("      \"$entryClass\"")
                appendLine("    ]")
                appendLine("  },")
                if (config.enableAccessFiles) {
                    appendLine("  \"accessWidener\": \"$modId.accesswidener\",")
                }
                if (config.enableMixins) {
                    appendLine("  \"mixins\": [")
                    appendLine("    \"$modId.mixins.json\"")
                    appendLine("  ],")
                }
                appendLine("  \"depends\": {")
                appendLine("    \"fabricloader\": \">=$fabricLoaderVersion\",")
                appendLine("    \"minecraft\": \"$minecraftVersion\",")
                appendLine("    \"java\": \">=$javaVersion\"")
                appendLine("  }")
                appendLine("}")
            }
        )
    }

    private fun writeNeoForgeModsToml(resourcesDir: File, config: PrismConfig, version: VersionConfig) {
        val metaDir = resourcesDir.resolve("META-INF")
        metaDir.mkdirs()

        metaDir.resolve("neoforge.mods.toml").writeText(
            buildString {
                appendLine("modLoader = \"javafml\"")
                appendLine("loaderVersion = \"${placeholder("neoforge_loader_version_range")}\"")
                appendLine("license = \"${placeholder("license")}\"")
                appendLine()
                appendLine("[[mods]]")
                appendLine("modId = \"${placeholder("mod_id")}\"")
                appendLine("version = \"${placeholder("version")}\"")
                appendLine("displayName = \"${placeholder("mod_name")}\"")
                appendLine("description = \"${placeholder("description")}\"")
                appendLine()
                appendLine("[[dependencies.${placeholder("mod_id")}]]")
                appendLine("modId = \"neoforge\"")
                appendLine("type = \"required\"")
                appendLine("versionRange = \"[${placeholder("neoforge_version")},)\"")
                appendLine("ordering = \"NONE\"")
                appendLine("side = \"BOTH\"")
                appendLine()
                appendLine("[[dependencies.${placeholder("mod_id")}]]")
                appendLine("modId = \"minecraft\"")
                appendLine("type = \"required\"")
                appendLine("versionRange = \"[${placeholder("minecraft_version")},)\"")
                appendLine("ordering = \"NONE\"")
                appendLine("side = \"BOTH\"")
                if (config.enableMixins) {
                    appendLine()
                    appendLine("[[mixins]]")
                    appendLine("config = \"${placeholder("mod_id")}.mixins.json\"")
                }
            }
        )
    }

    private fun writeForgeModsToml(resourcesDir: File, config: PrismConfig, version: VersionConfig) {
        val metaDir = resourcesDir.resolve("META-INF")
        metaDir.mkdirs()

        metaDir.resolve("mods.toml").writeText(
            buildString {
                appendLine("modLoader = \"javafml\"")
                appendLine("loaderVersion = \"${placeholder("forge_loader_version_range")}\"")
                appendLine("license = \"${placeholder("license")}\"")
                appendLine()
                appendLine("[[mods]]")
                appendLine("modId = \"${placeholder("mod_id")}\"")
                appendLine("version = \"${placeholder("version")}\"")
                appendLine("displayName = \"${placeholder("mod_name")}\"")
                appendLine("description = \"${placeholder("description")}\"")
                appendLine()
                appendLine("[[dependencies.${placeholder("mod_id")}]]")
                appendLine("modId = \"forge\"")
                appendLine("mandatory = true")
                appendLine("versionRange = \"[${placeholder("forge_version")},)\"")
                appendLine("ordering = \"NONE\"")
                appendLine("side = \"BOTH\"")
                appendLine()
                appendLine("[[dependencies.${placeholder("mod_id")}]]")
                appendLine("modId = \"minecraft\"")
                appendLine("mandatory = true")
                appendLine("versionRange = \"[${placeholder("minecraft_version")},)\"")
                appendLine("ordering = \"NONE\"")
                appendLine("side = \"BOTH\"")
                if (config.enableMixins) {
                    appendLine()
                    appendLine("[[mixins]]")
                    appendLine("config = \"${placeholder("mod_id")}.mixins.json\"")
                }
            }
        )
    }

    private fun writeMcmodInfo(resourcesDir: File, config: PrismConfig, version: VersionConfig) {
        resourcesDir.resolve("mcmod.info").writeText(
            buildString {
                appendLine("[{")
                appendLine("  \"modid\": \"${config.modId}\",")
                appendLine("  \"name\": \"${config.modName}\",")
                appendLine("  \"description\": \"A Prism multi-version mod.\",")
                appendLine("  \"version\": \"1.0.0\",")
                appendLine("  \"mcversion\": \"${version.mcVersion}\",")
                appendLine("  \"authorList\": [],")
                appendLine("  \"credits\": \"\",")
                appendLine("  \"logoFile\": \"\",")
                appendLine("  \"url\": \"\",")
                appendLine("  \"updateUrl\": \"\"")
                appendLine("}]")
            }
        )
    }

    // -------------------------------------------------------------------------
    // Mixin config
    // -------------------------------------------------------------------------
    private fun writeMixinConfig(loaderDir: File, config: PrismConfig) {
        val resourcesDir = loaderDir.resolve("src/main/resources")
        resourcesDir.mkdirs()

        resourcesDir.resolve("${config.modId}.mixins.json").writeText(
            buildString {
                appendLine("{")
                appendLine("  \"required\": true,")
                appendLine("  \"minVersion\": \"0.8\",")
                appendLine("  \"package\": \"${config.groupId}.${config.modId}.mixin\",")
                appendLine("  \"compatibilityLevel\": \"JAVA_${placeholder("java_version")}\",")
                appendLine("  \"mixins\": [],")
                appendLine("  \"client\": [],")
                appendLine("  \"server\": [],")
                appendLine("  \"injectors\": {")
                appendLine("    \"defaultRequire\": 1")
                appendLine("  }")
                appendLine("}")
            }
        )
    }

    // -------------------------------------------------------------------------
    // Access widener / Access transformer
    // -------------------------------------------------------------------------
    private fun writeAccessFile(loaderDir: File, loader: String, modId: String) {
        val resourcesDir = loaderDir.resolve("src/main/resources")
        resourcesDir.mkdirs()

        when (loader) {
            "fabric" -> {
                resourcesDir.resolve("$modId.accesswidener").writeText(
                    "accessWidener\tv2\tnamed\n"
                )
            }
            "neoforge", "forge" -> {
                val metaDir = resourcesDir.resolve("META-INF")
                metaDir.mkdirs()
                metaDir.resolve("accesstransformer.cfg").writeText(
                    "# Access Transformer file\n"
                )
            }
            "legacyforge" -> {
                val metaDir = resourcesDir.resolve("META-INF")
                metaDir.mkdirs()
                metaDir.resolve("accesstransformer.cfg").writeText(
                    "# Access Transformer file\n"
                )
            }
        }
    }

    private fun placeholder(name: String): String = "${'$'}{$name}"

    // Prism handles all subproject configuration from root.
    // No subproject build.gradle.kts files are generated.
}
