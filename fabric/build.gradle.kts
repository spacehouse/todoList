import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("dev.architectury.loom") version "1.6-SNAPSHOT"
}

val archives_name: String by project
val commonProject = project(":common")

base {
    archivesName.set("$archives_name-fabric")
}

dependencies {
    val minecraftVersion = property("minecraft_version") as String
    val loaderVersion = property("loader_version") as String
    val fabricApiVersion = property("fabric_api_version") as String

    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":common", configuration = "namedElements"))
    
    modCompileOnly("com.terraformersmc:modmenu:7.2.2")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
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

val clientModsDir = file("E:/MC/cloudSave/mc-mss/version/.minecraft/versions/1.20.1-Fabric 0.15.11/mods")
val serverModsDir = file("E:/MC/server/fabric-0.15.11-server/mods")

tasks.register<Copy>("copyToClientMods") {
    from(layout.buildDirectory.dir("libs"))
    include("*.jar")
    exclude("*-sources.jar")
    into(clientModsDir)
}

tasks.register<Copy>("copyToServerMods") {
    from(layout.buildDirectory.dir("libs"))
    include("*.jar")
    exclude("*-sources.jar")
    into(serverModsDir)
}
