import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("dev.architectury.loom") version "1.7.435"
    id("maven-publish")
}

val archives_name: String by project
val minecraftVersion = (findProperty("target_minecraft_version") as String?) ?: (property("minecraft_version") as String)
val loaderVersion = (findProperty("target_loader_version") as String?) ?: (property("loader_version") as String)
val forgeVersion = (findProperty("target_forge_version") as String?) ?: "47.2.0"
val forgeLoaderRange = (findProperty("target_forge_loader_range") as String?) ?: "[47,)"
val minecraftVersionRange = (findProperty("target_minecraft_version_range") as String?) ?: "[$minecraftVersion]"
val commonProject = project(":common")
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

// 版本覆盖源目录：与 common 的 prepareVersionedMainSources 同规则。
// tag 命中：mc1202 = 1.20.2~1.20.6，mc1205 = 1.20.5/1.20.6；按 tag 顺序依次覆盖同名文件。
val generatedVersionedSourcesDir = layout.buildDirectory.dir("generated/sources/versionedMain/java").get().asFile
val needs1202Overlay = minecraftVersion in setOf("1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6")
val needs1205Overlay = minecraftVersion in setOf("1.20.5", "1.20.6")

val prepareVersionedSources = tasks.register("prepareVersionedSources") {
    group = "build setup"
    description = "Copy platform main sources and apply per-version overlay files."
    inputs.dir(layout.projectDirectory.dir("src/main/java"))
    inputs.dir(layout.projectDirectory.dir("src/main-versioned"))
    inputs.property("needs1202Overlay", needs1202Overlay)
    inputs.property("needs1205Overlay", needs1205Overlay)
    outputs.dir(generatedVersionedSourcesDir)

    doLast {
        val sourceDir = file("src/main/java")
        delete(generatedVersionedSourcesDir)
        generatedVersionedSourcesDir.mkdirs()
        sourceDir.copyRecursively(generatedVersionedSourcesDir, overwrite = true)
        val overlayTags = buildList {
            if (needs1202Overlay) add("mc1202")
            if (needs1205Overlay) add("mc1205")
        }
        for (tag in overlayTags) {
            val overlayDir = file("src/main-versioned/$tag/java")
            if (overlayDir.exists()) {
                overlayDir.copyRecursively(generatedVersionedSourcesDir, overwrite = true)
            }
        }
    }
}

the<SourceSetContainer>()["main"].java.setSrcDirs(listOf(generatedVersionedSourcesDir))
tasks.named("compileJava") {
    dependsOn(prepareVersionedSources)
}
tasks.named("sourcesJar") {
    dependsOn(prepareVersionedSources)
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
        expand(
            mapOf(
                "version" to project.version,
                "forge_loader_range" to forgeLoaderRange,
                "minecraft_version_range" to minecraftVersionRange
            )
        )
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

// 网络桥冒烟测试：随 check 一起在所有目标版本上执行，
// 拦截 Forge 分代 API 适配（47/48+）、版本协商、连接字段解析等实机才暴露的回归
val networkingSmokeTest = tasks.register<JavaExec>("networkingCompatSmokeTest") {
    group = "verification"
    description = "Run the ForgeNetworkBridge smoke tests on the current Forge API generation."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.todolist.forge.network.ForgeNetworkSmokeTestMain")
}

tasks.named("check") {
    dependsOn(networkingSmokeTest)
}
