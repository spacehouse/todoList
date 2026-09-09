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

// Forge 冻结在 1.21.1（官方发布链自 1.21.2 起停更）：本分支（1.21.9-1.21.11）
// 不承载 Forge，forge 子项目已移除，仅 Fabric + NeoForge 参与构建；
// 矩阵中的 forge_supported=false 字段保留，用于记录各 profile 的不支持状态。
include("common")
include("fabric")
include("neoforge")
