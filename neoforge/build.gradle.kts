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
val neoforgeVersion = (findProperty("target_neoforge_version") as String?) ?: (property("neoforge_version") as String)
val neoforgeLoaderRange = (findProperty("target_neoforge_loader_range") as String?) ?: "[21.1,)"
val minecraftVersionRange = (findProperty("target_minecraft_version_range") as String?) ?: "[1.21.1]"
val apiGroup = (findProperty("target_api_group") as String?) ?: "v1_21_1"
val commonProject = project(":common")
val h2Jar = rootProject.file("libs/h2-2.2.220.jar")

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
    inputs.property("neoforge_loader_range", neoforgeLoaderRange)
    inputs.property("minecraft_version_range", minecraftVersionRange)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(mapOf(
            "version" to project.version,
            "neoforge_loader_range" to neoforgeLoaderRange,
            "minecraft_version_range" to minecraftVersionRange
        ))
    }
}

sourceSets {
    named("main") {
        java {
            // 兼容组覆盖源集：api_group != v1_21_1 时，src/main/<api_group>/java
            // 中的同名类覆盖基线源码，用于隔离 1.21.2+ 编译期 API 差异
            if (apiGroup != "v1_21_1") {
                val overrideDir = layout.projectDirectory.dir("src/main/$apiGroup/java")
                if (overrideDir.asFile.exists()) {
                    val overriddenPaths = overrideDir.asFileTree.files.map {
                        it.relativeTo(overrideDir.asFile).path.replace('\\', '/')
                    }
                    setSrcDirs(listOf(
                        fileTree("src/main/java") { exclude(overriddenPaths) },
                        overrideDir
                    ))
                }
            }
        }
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
