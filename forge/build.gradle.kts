plugins {
    id("dev.architectury.loom") version "1.6-SNAPSHOT"
    id("maven-publish")
}

val archives_name: String by project
val minecraftVersion = property("minecraft_version") as String
val loaderVersion = property("loader_version") as String

base {
    archivesName.set("$archives_name-forge")
}

repositories {
    maven("https://maven.architectury.dev/")
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    forge("net.minecraftforge:forge:$minecraftVersion-47.2.0")
    compileOnly("net.fabricmc:fabric-loader:$loaderVersion")
    compileOnly("net.minecraftforge:fmlloader:$minecraftVersion-47.2.0")
    compileOnly("net.minecraftforge:javafmllanguage:$minecraftVersion-47.2.0")
    compileOnly("net.minecraftforge:eventbus:6.0.5")

    compileOnly("org.slf4j:slf4j-api:2.0.7")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to project.version))
    }
}

tasks.named("build") {
    dependsOn("remapJar")
}

sourceSets {
    named("main") {
        java {
            srcDir(project(":common").file("src/main/java"))
        }
        resources.srcDir(project(":common").file("src/main/resources"))
    }
}
