plugins {
    id("dev.architectury.loom") version "1.6-SNAPSHOT"
    id("maven-publish")
}

val archives_name: String by project
val minecraftVersion = property("minecraft_version") as String
val yarnMappings = property("yarn_mappings") as String

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
    mappings("net.fabricmc:yarn:$yarnMappings")
    compileOnly("net.minecraftforge:forge:$minecraftVersion-47.2.0:universal")
    compileOnly("net.minecraftforge:fmlloader:$minecraftVersion-47.2.0")
    compileOnly("net.minecraftforge:javafmllanguage:$minecraftVersion-47.2.0")
    compileOnly("net.minecraftforge:eventbus:6.0.5")

    implementation(project(":common", configuration = "namedElements"))
    compileOnly("org.slf4j:slf4j-api:2.0.7")
}
