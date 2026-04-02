plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.prism"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")
        instrumentationTools()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "dev.prism.generator"
        name = "Prism Project Generator"
        version = project.version.toString()
        description = "Project generator for Prism multi-version Minecraft mod development."
        vendor {
            name = "Leclowndu93150"
            url = "https://github.com/Leclowndu93150"
        }
        ideaVersion {
            sinceBuild = "243"
        }
    }
}

kotlin {
    jvmToolchain(21)
}
