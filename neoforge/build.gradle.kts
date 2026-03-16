import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("dev.architectury.loom")
    id("maven-publish")
}

val archives_name: String by project
val minecraftVersion = property("minecraft_version") as String
val loaderVersion = property("loader_version") as String
val neoforgeVersion = property("neoforge_version") as String
val commonProject = project(":common")

base {
    archivesName.set("$archives_name-neoforge-$minecraftVersion")
}

repositories {
    maven("https://maven.architectury.dev/")
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    val neoForgeConfig = configurations.findByName("neoForge")
        ?: configurations.findByName("forge")
        ?: configurations.maybeCreate("forge")
    add(neoForgeConfig.name, "net.neoforged:neoforge:$neoforgeVersion")
    implementation(project(":common", configuration = "namedElements"))
    compileOnly("net.fabricmc:fabric-loader:$loaderVersion")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(mapOf("version" to project.version))
    }
}

tasks.named("build") {
    dependsOn("remapJar")
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
