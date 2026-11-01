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
val h2Jar = rootProject.file("libs/h2-2.2.220.jar")

loom {
    mixin {
        defaultRefmapName.set("todolist.mixins.refmap.json")
    }
}

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
    // dev 运行时（runServer/runClient）需要 H2 驱动在 classpath；打包时由 tasks.jar 的
    // zipTree 嵌入 mod jar，二者不冲突（implementation 只影响 dev classpath 与发布 pom 排除）
    implementation(files(h2Jar))

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
    from(zipTree(h2Jar)) {
        exclude("META-INF/MANIFEST.MF")
    }
}

val fabricSourceSets = the<SourceSetContainer>()
val fabricMainSourceSet = fabricSourceSets["main"]
val fabricTestSourceSet = fabricSourceSets["test"]

tasks.register<JavaExec>("mixinVisibilityTest") {
    group = "verification"
    description = "Run the offline Mixin visibility self-tests guarding against InvalidMixinException regressions."
    classpath = files(
        fabricMainSourceSet.output,
        fabricTestSourceSet.output,
        fabricMainSourceSet.compileClasspath,
        fabricTestSourceSet.compileClasspath
    )
    mainClass.set("com.todolist.mixin.test.MixinVisibilityTestMain")
    dependsOn(fabricTestSourceSet.classesTaskName)
}

tasks.register<JavaExec>("mixinConfigTest") {
    group = "verification"
    description = "Run the mixin registry guard self-tests (registered mixins <-> mixin package classes)."
    classpath = files(
        fabricMainSourceSet.output,
        fabricTestSourceSet.output,
        fabricMainSourceSet.compileClasspath,
        fabricTestSourceSet.compileClasspath
    )
    mainClass.set("com.todolist.mixin.test.MixinConfigTestMain")
    dependsOn(fabricTestSourceSet.classesTaskName)
}

tasks.named("check") {
    dependsOn("mixinVisibilityTest")
    dependsOn("mixinConfigTest")
}
