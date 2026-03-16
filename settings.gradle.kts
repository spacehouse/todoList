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

include("common")
include("fabric")
include("forge")
include("neoforge")
