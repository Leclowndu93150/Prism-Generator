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
        val enableSharedCommon: Boolean
    )

    data class VersionConfig(
        val mcVersion: String,
        val multiLoader: Boolean,
        val loaders: List<String>
    )

    // Loader / API versions per Minecraft version
    private val LOADER_VERSIONS: Map<String, Map<String, String>> = mapOf(
        "1.7.10" to mapOf("legacyforge" to "10.13.4.1614"),
        "1.12.2" to mapOf("legacyforge" to "14.23.5.2847"),
        "1.16.5" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.42.0+1.16", "forge" to "36.2.39"),
        "1.18.2" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.77.0+1.18.2", "forge" to "40.2.21"),
        "1.19.4" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.87.0+1.19.4", "forge" to "45.2.0"),
        "1.20.1" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.92.7+1.20.1", "forge" to "47.4.18"),
        "1.20.4" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.97.0+1.20.4", "neoforge" to "20.4.237"),
        "1.21.1" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.116.9+1.21.1", "neoforge" to "21.1.222"),
        "1.21.4" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.119.2+1.21.4", "neoforge" to "21.4.86"),
        "26.1" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.145.2+26.1.1", "neoforge" to "26.1.1.0-beta"),
    )

    fun generate(config: PrismConfig) {
        val root = File(config.projectPath)
        root.mkdirs()

        writeGitignore(root)
        writeGradleProperties(root, config)
        writeSettingsGradle(root, config)
        writeBuildGradle(root, config)
        writeGradleWrapper(root)

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
            |.intellijPlatform/
            |.idea/
            |*.iml
            |*.ipr
            |*.iws
            |out/
            |run/
            |logs/
            |.DS_Store
            |Thumbs.db
            """.trimMargin() + "\n"
        )
    }

    // -------------------------------------------------------------------------
    // gradle.properties
    // -------------------------------------------------------------------------
    private fun writeGradleProperties(root: File, config: PrismConfig) {
        root.resolve("gradle.properties").writeText(
            buildString {
                appendLine("org.gradle.jvmargs=-Xmx2G")
                appendLine("org.gradle.daemon=false")
                appendLine()
                appendLine("mod_id=${config.modId}")
                appendLine("mod_name=${config.modName}")
                appendLine("mod_version=1.0.0")
                appendLine("mod_group=${config.groupId}")
                appendLine("mod_license=${config.license}")
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
                appendLine("        gradlePluginPortal()")
                appendLine("        mavenCentral()")
                appendLine("        maven(\"https://maven.architectury.dev\")")
                appendLine("        maven(\"https://maven.fabricmc.net\")")
                appendLine("        maven(\"https://maven.neoforged.net/releases\")")
                appendLine("        maven(\"https://maven.minecraftforge.net\")")
                if (needsGtnh) {
                    appendLine("        maven(\"https://maven.gtnewhorizons.com\")")
                }
                appendLine("    }")
                appendLine("}")
                appendLine()
                appendLine("plugins {")
                appendLine("    id(\"dev.deftu.gradle.multiversion-root\") version \"2.+\"")
                appendLine("}")
                appendLine()
                appendLine("rootProject.name = \"${config.projectName}\"")
                appendLine()

                for (version in config.versions) {
                    if (version.multiLoader) {
                        appendLine("include(\"versions:${version.mcVersion}:common\")")
                        for (loader in version.loaders) {
                            appendLine("include(\"versions:${version.mcVersion}:$loader\")")
                        }
                    } else {
                        val loader = version.loaders.firstOrNull() ?: continue
                        appendLine("include(\"versions:${version.mcVersion}:$loader\")")
                    }
                }
            }
        )
    }

    // -------------------------------------------------------------------------
    // build.gradle.kts (root)
    // -------------------------------------------------------------------------
    private fun writeBuildGradle(root: File, config: PrismConfig) {
        root.resolve("build.gradle.kts").writeText(
            buildString {
                appendLine("plugins {")
                appendLine("    java")
                if (config.enableKotlin) {
                    appendLine("    kotlin(\"jvm\") version \"2.1.20\" apply false")
                }
                appendLine("    id(\"dev.deftu.gradle.multiversion-root\")")
                appendLine("}")
                appendLine()
                appendLine("group = \"${config.groupId}\"")
                appendLine("version = property(\"mod_version\") as String")
                appendLine()
                appendLine("subprojects {")
                appendLine("    apply(plugin = \"java\")")
                if (config.enableKotlin) {
                    appendLine("    apply(plugin = \"org.jetbrains.kotlin.jvm\")")
                }
                appendLine()
                appendLine("    group = rootProject.group")
                appendLine("    version = rootProject.version")
                appendLine()
                appendLine("    repositories {")
                appendLine("        mavenCentral()")
                appendLine("        maven(\"https://maven.fabricmc.net\")")
                appendLine("        maven(\"https://maven.neoforged.net/releases\")")
                appendLine("        maven(\"https://maven.minecraftforge.net\")")
                val needsGtnh = config.versions.any { it.loaders.contains("legacyforge") }
                if (needsGtnh) {
                    appendLine("        maven(\"https://maven.gtnewhorizons.com\")")
                }
                appendLine("    }")
                appendLine()
                appendLine("    java {")
                appendLine("        toolchain.languageVersion.set(JavaLanguageVersion.of(21))")
                appendLine("    }")
                appendLine("}")
            }
        )
    }

    // -------------------------------------------------------------------------
    // Gradle wrapper (minimal, just properties)
    // -------------------------------------------------------------------------
    private fun writeGradleWrapper(root: File) {
        val wrapperDir = root.resolve("gradle/wrapper")
        wrapperDir.mkdirs()
        wrapperDir.resolve("gradle-wrapper.properties").writeText(
            buildString {
                appendLine("distributionBase=GRADLE_USER_HOME")
                appendLine("distributionPath=wrapper/dists")
                appendLine("distributionUrl=https\\://services.gradle.org/distributions/gradle-8.12-bin.zip")
                appendLine("networkTimeout=10000")
                appendLine("validateDistributionUrl=true")
                appendLine("zipStoreBase=GRADLE_USER_HOME")
                appendLine("zipStorePath=wrapper/dists")
            }
        )
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
            // -- Common module --
            val commonSrc = versionDir.resolve("common/src/main/java/$packagePath")
            commonSrc.mkdirs()
            writeCommonModClass(commonSrc, config, className)

            val commonBuild = versionDir.resolve("common")
            writeSubprojectBuildGradle(commonBuild, config, version, "common")

            // -- Each loader module --
            for (loader in version.loaders) {
                val loaderDir = versionDir.resolve(loader)
                loaderDir.mkdirs()

                val loaderSrc = loaderDir.resolve("src/main/java/$packagePath")
                loaderSrc.mkdirs()
                writeLoaderEntryPoint(loaderSrc, config, version, loader, className)
                writeMetadataFiles(loaderDir, config, version, loader)
                writeMixinConfig(loaderDir, config, version)
                writeAccessFile(loaderDir, loader)

                writeSubprojectBuildGradle(loaderDir, config, version, loader)
            }
        } else {
            val loader = version.loaders.firstOrNull() ?: return
            val loaderDir = versionDir.resolve(loader)
            loaderDir.mkdirs()

            val srcDir = loaderDir.resolve("src/main/java/$packagePath")
            srcDir.mkdirs()
            writeLoaderEntryPoint(srcDir, config, version, loader, className)
            writeMetadataFiles(loaderDir, config, version, loader)
            writeMixinConfig(loaderDir, config, version)
            writeAccessFile(loaderDir, loader)

            writeSubprojectBuildGradle(loaderDir, config, version, loader)
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
            dir.resolve("$entryName.java").writeText(
                buildString {
                    appendLine("package ${config.groupId}.${config.modId};")
                    appendLine()
                    appendLine("import net.minecraftforge.fml.common.Mod;")
                    appendLine("import net.minecraftforge.fml.common.event.FMLInitializationEvent;")
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
    }

    private fun writeFabricModJson(resourcesDir: File, config: PrismConfig, version: VersionConfig) {
        val className = config.modId.split('_', '-').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        val entryClass = "${config.groupId}.${config.modId}.${className}Fabric"

        resourcesDir.resolve("fabric.mod.json").writeText(
            buildString {
                appendLine("{")
                appendLine("  \"schemaVersion\": 1,")
                appendLine("  \"id\": \"${config.modId}\",")
                appendLine("  \"version\": \"\${version}\",")
                appendLine("  \"name\": \"${config.modName}\",")
                appendLine("  \"description\": \"A Prism multi-version mod.\",")
                appendLine("  \"license\": \"${config.license}\",")
                appendLine("  \"entrypoints\": {")
                appendLine("    \"main\": [")
                appendLine("      \"$entryClass\"")
                appendLine("    ]")
                appendLine("  },")
                appendLine("  \"mixins\": [")
                appendLine("    \"${config.modId}.mixins.json\"")
                appendLine("  ],")
                appendLine("  \"depends\": {")
                appendLine("    \"fabricloader\": \">=0.15.0\",")
                appendLine("    \"minecraft\": \"${version.mcVersion}\",")
                appendLine("    \"java\": \">=17\"")
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
                appendLine("loaderVersion = \"[1,)\"")
                appendLine("license = \"${config.license}\"")
                appendLine()
                appendLine("[[mods]]")
                appendLine("modId = \"${config.modId}\"")
                appendLine("version = \"\${version}\"")
                appendLine("displayName = \"${config.modName}\"")
                appendLine("description = \"A Prism multi-version mod.\"")
                appendLine()
                appendLine("[[dependencies.${config.modId}]]")
                appendLine("modId = \"neoforge\"")
                appendLine("type = \"required\"")
                appendLine("versionRange = \"[${LOADER_VERSIONS[version.mcVersion]?.get("neoforge") ?: "1.0"},)\"")
                appendLine("ordering = \"NONE\"")
                appendLine("side = \"BOTH\"")
                appendLine()
                appendLine("[[dependencies.${config.modId}]]")
                appendLine("modId = \"minecraft\"")
                appendLine("type = \"required\"")
                appendLine("versionRange = \"[${version.mcVersion},)\"")
                appendLine("ordering = \"NONE\"")
                appendLine("side = \"BOTH\"")
                appendLine()
                appendLine("[[mixins]]")
                appendLine("config = \"${config.modId}.mixins.json\"")
            }
        )
    }

    private fun writeForgeModsToml(resourcesDir: File, config: PrismConfig, version: VersionConfig) {
        val metaDir = resourcesDir.resolve("META-INF")
        metaDir.mkdirs()

        metaDir.resolve("mods.toml").writeText(
            buildString {
                appendLine("modLoader = \"javafml\"")
                appendLine("loaderVersion = \"[1,)\"")
                appendLine("license = \"${config.license}\"")
                appendLine()
                appendLine("[[mods]]")
                appendLine("modId = \"${config.modId}\"")
                appendLine("version = \"\${version}\"")
                appendLine("displayName = \"${config.modName}\"")
                appendLine("description = \"A Prism multi-version mod.\"")
                appendLine()
                appendLine("[[dependencies.${config.modId}]]")
                appendLine("modId = \"forge\"")
                appendLine("mandatory = true")
                appendLine("versionRange = \"[${LOADER_VERSIONS[version.mcVersion]?.get("forge") ?: "1.0"},)\"")
                appendLine("ordering = \"NONE\"")
                appendLine("side = \"BOTH\"")
                appendLine()
                appendLine("[[dependencies.${config.modId}]]")
                appendLine("modId = \"minecraft\"")
                appendLine("mandatory = true")
                appendLine("versionRange = \"[${version.mcVersion},)\"")
                appendLine("ordering = \"NONE\"")
                appendLine("side = \"BOTH\"")
                appendLine()
                appendLine("[[mixins]]")
                appendLine("config = \"${config.modId}.mixins.json\"")
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
    private fun writeMixinConfig(loaderDir: File, config: PrismConfig, version: VersionConfig) {
        val resourcesDir = loaderDir.resolve("src/main/resources")
        resourcesDir.mkdirs()

        val javaVersion = when {
            version.mcVersion == "1.7.10" -> 8
            version.mcVersion == "1.12.2" -> 8
            version.mcVersion == "1.16.5" -> 16
            version.mcVersion.startsWith("1.18") || version.mcVersion.startsWith("1.19") -> 17
            else -> 21
        }

        resourcesDir.resolve("${config.modId}.mixins.json").writeText(
            buildString {
                appendLine("{")
                appendLine("  \"required\": true,")
                appendLine("  \"minVersion\": \"0.8\",")
                appendLine("  \"package\": \"${config.groupId}.${config.modId}.mixin\",")
                appendLine("  \"compatibilityLevel\": \"JAVA_$javaVersion\",")
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
    private fun writeAccessFile(loaderDir: File, loader: String) {
        val resourcesDir = loaderDir.resolve("src/main/resources")
        resourcesDir.mkdirs()

        when (loader) {
            "fabric" -> {
                resourcesDir.resolve("mod.accesswidener").writeText(
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
                resourcesDir.resolve("accesstransformer.cfg").writeText(
                    "# Access Transformer file\n"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Sub-project build.gradle.kts
    // -------------------------------------------------------------------------
    private fun writeSubprojectBuildGradle(
        moduleDir: File,
        config: PrismConfig,
        version: VersionConfig,
        loaderOrCommon: String
    ) {
        moduleDir.resolve("build.gradle.kts").writeText(
            buildString {
                appendLine("plugins {")
                appendLine("    java")
                if (config.enableKotlin) {
                    appendLine("    kotlin(\"jvm\")")
                }
                appendLine("}")
                appendLine()
                appendLine("// Prism version: ${version.mcVersion}, module: $loaderOrCommon")
                appendLine("// Configure your loader-specific dependencies here.")
                appendLine()
                appendLine("group = rootProject.group")
                appendLine("version = rootProject.version")
                appendLine()

                when (loaderOrCommon) {
                    "common" -> {
                        appendLine("// Common module: add shared dependencies here.")
                        appendLine("dependencies {")
                        appendLine("    // Add common dependencies")
                        appendLine("}")
                    }
                    "fabric" -> {
                        val loaderVer = LOADER_VERSIONS[version.mcVersion]
                        appendLine("dependencies {")
                        if (version.multiLoader) {
                            appendLine("    implementation(project(\":versions:${version.mcVersion}:common\"))")
                        }
                        appendLine("    // Fabric Loader: ${loaderVer?.get("fabric") ?: "unknown"}")
                        appendLine("    // Fabric API: ${loaderVer?.get("fabricApi") ?: "unknown"}")
                        appendLine("}")
                    }
                    "neoforge" -> {
                        val loaderVer = LOADER_VERSIONS[version.mcVersion]
                        appendLine("dependencies {")
                        if (version.multiLoader) {
                            appendLine("    implementation(project(\":versions:${version.mcVersion}:common\"))")
                        }
                        appendLine("    // NeoForge: ${loaderVer?.get("neoforge") ?: "unknown"}")
                        appendLine("}")
                    }
                    "forge" -> {
                        val loaderVer = LOADER_VERSIONS[version.mcVersion]
                        appendLine("dependencies {")
                        if (version.multiLoader) {
                            appendLine("    implementation(project(\":versions:${version.mcVersion}:common\"))")
                        }
                        appendLine("    // Forge: ${loaderVer?.get("forge") ?: "unknown"}")
                        appendLine("}")
                    }
                    "legacyforge" -> {
                        val loaderVer = LOADER_VERSIONS[version.mcVersion]
                        appendLine("dependencies {")
                        if (version.multiLoader) {
                            appendLine("    implementation(project(\":versions:${version.mcVersion}:common\"))")
                        }
                        appendLine("    // Legacy Forge: ${loaderVer?.get("legacyforge") ?: "unknown"}")
                        appendLine("}")
                    }
                }
            }
        )
    }
}
