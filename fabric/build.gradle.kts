import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("dev.architectury.loom")
}

val archives_name: String by project
val commonProject = project(":common")
val enableModMenu = (findProperty("enable_modmenu") as String?)?.toBoolean()
    ?: !gradle.startParameter.isOffline
val effectiveModMenu = enableModMenu

base {
    val minecraftVersion = property("minecraft_version") as String
    archivesName.set("$archives_name-fabric-$minecraftVersion")
}

dependencies {
    val minecraftVersion = property("minecraft_version") as String
    val loaderVersion = property("loader_version") as String
    val fabricApiVersion = property("fabric_api_version") as String
    val modmenuVersion = property("modmenu_version") as String

    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":common", configuration = "namedElements"))

    if (effectiveModMenu) {
        modCompileOnly("com.terraformersmc:modmenu:$modmenuVersion")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    val modmenuEntrypoints = if (effectiveModMenu) {
        """["com.todolist.client.ModMenuIntegration"]"""
    } else {
        "[]"
    }
    inputs.property("modmenuEntrypoints", modmenuEntrypoints)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
        filter { line ->
            if (line.contains("\"modmenu\": []")) {
                line.replace("\"modmenu\": []", "\"modmenu\": $modmenuEntrypoints")
            } else {
                line
            }
        }
    }
}

sourceSets {
    named("main") {
        java {
            if (!effectiveModMenu) {
                exclude("com/todolist/client/ModMenuIntegration.java")
            }
        }
    }
}

val commonMainOutput = commonProject.extensions
    .getByType(JavaPluginExtension::class.java)
    .sourceSets
    .getByName("main")
    .output

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(commonMainOutput)
}
