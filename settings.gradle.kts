pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net/")
    }
}

// 1.20.5 没有 Forge 发布链（官方从 1.20.4 的 49.x 直接跳到 1.20.6 的 50.x）。
// 矩阵通过 target_forge_supported=false 声明该版本为 Fabric-only，
// 构建时排除 forge 子项目；未传属性时默认包含，保持 IDE 同步与旧版本行为不变。
val forgeEnabled = (startParameter.projectProperties["target_forge_supported"])?.trim()?.lowercase() != "false"

include("common")
include("fabric")
if (forgeEnabled) {
    include("forge")
}
