pluginManagement {
    val loomVersion = providers.gradleProperty("loom_version").get()
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.architectury.loom") {
                useModule("dev.architectury:architectury-loom:$loomVersion")
            }
        }
    }
    plugins {
        id("dev.architectury.loom") version loomVersion
    }
}

// Forge 冻结在 1.21.1：1.21.2+ 官方发布链停更，矩阵通过 target_forge_supported=false
// 声明该版本为 Fabric + NeoForge-only，构建时排除 forge 子项目；
// 未传属性时默认包含，保持 IDE 同步与旧行为不变。
val forgeEnabled = (startParameter.projectProperties["target_forge_supported"])?.trim()?.lowercase() != "false"

include("common")
include("fabric")
if (forgeEnabled) {
    include("forge")
}
include("neoforge")
