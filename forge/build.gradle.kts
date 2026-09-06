import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("dev.architectury.loom")
    id("maven-publish")
}

val archives_name: String by project
// 版本矩阵入口：build-local-121x.bat 注入 target_* 属性；未注入时回退 gradle.properties
val minecraftVersion = (findProperty("target_minecraft_version") as String?) ?: (property("minecraft_version") as String)
val loaderVersion = (findProperty("target_loader_version") as String?) ?: (property("loader_version") as String)
val commonProject = project(":common")
val forgeVersion = (findProperty("target_forge_version") as String?) ?: (property("forge_version") as String)
val forgeLoaderRange = (findProperty("target_forge_loader_range") as String?) ?: "[52,)"
val minecraftVersionRange = (findProperty("target_minecraft_version_range") as String?) ?: "[1.21.1]"
val h2Jar = rootProject.file("libs/h2-2.2.220.jar")

base {
    archivesName.set("$archives_name-forge-$minecraftVersion")
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
    forge("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")
    implementation(project(":common", configuration = "namedElements"))
    compileOnly("net.fabricmc:fabric-loader:$loaderVersion")
    compileOnly("net.minecraftforge:fmlloader:$minecraftVersion-$forgeVersion")
    compileOnly("net.minecraftforge:javafmllanguage:$minecraftVersion-$forgeVersion")
    compileOnly("net.minecraftforge:eventbus:6.0.5")

    compileOnly("org.slf4j:slf4j-api:2.0.7")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("forge_loader_range", forgeLoaderRange)
    inputs.property("minecraft_version_range", minecraftVersionRange)
    filesMatching("META-INF/mods.toml") {
        expand(mapOf(
            "version" to project.version,
            "forge_loader_range" to forgeLoaderRange,
            "minecraft_version_range" to minecraftVersionRange
        ))
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
    from(zipTree(h2Jar)) {
        exclude("META-INF/MANIFEST.MF")
    }
}
