# Prism Project Generator

IntelliJ IDEA plugin that adds a "Prism Mod" option to the New Project wizard. Generates a complete [Prism](https://github.com/Leclowndu93150/Prism) multi-version Minecraft mod project.

## What it does

Pick your Minecraft versions, choose loaders (Fabric, NeoForge, Forge, Legacy Forge), and the plugin generates the full project structure with:

- `settings.gradle.kts` and `build.gradle.kts` with Prism DSL
- Latest Minecraft and loader defaults fetched online when available, with offline fallbacks
- Source directories for each version and loader
- Entry point classes (ModInitializer, @Mod, etc.)
- Metadata files aligned with Prism template expansion (`fabric.mod.json`, `neoforge.mods.toml`, etc.)
- Optional mixin and access widener / access transformer scaffolds
- Full Gradle wrapper (`gradlew`, `gradlew.bat`, and `gradle-wrapper.jar`) and `.gitignore`

## Install

Download the latest release from [Releases](https://github.com/Leclowndu93150/Prism-Generator/releases) and install via Settings > Plugins > Install Plugin from Disk.

## Usage

1. File > New > Project
2. Select "Prism Mod" from the list
3. Configure project name and location
4. Set mod ID, name, group ID, and license
5. Add Minecraft versions with loader selections
6. Click Create

## Supported configurations

| Mode | Description |
|------|-------------|
| Single Loader | One loader per version, no common/loader split |
| Multi Loader | Common code + multiple loaders per version |
| Shared Common | Cross-version pure Java code |

| Loader | Minecraft versions |
|--------|-------------------|
| Fabric | Any |
| NeoForge | 1.20.2+ |
| Forge | 1.17 - 1.20.1 |
| Legacy Forge | 1.7.10 - 1.12.2 |

## License

MIT
