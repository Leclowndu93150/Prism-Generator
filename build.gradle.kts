plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    `maven-publish`
}

group = "dev.prism"
version = "1.0.3"

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.prism"
            artifactId = "prism-generator"
            version = project.version.toString()
            artifact(tasks.named("buildPlugin").map { File(project.layout.buildDirectory.asFile.get(), "distributions/prism-generator-${project.version}.zip") })
        }
    }
    repositories {
        maven {
            name = "Leclown"
            url = uri("https://maven.leclowndu93150.dev/releases")
            credentials {
                username = System.getenv("MAVEN_USER") ?: ""
                password = System.getenv("MAVEN_PASS") ?: ""
            }
        }
    }
}
