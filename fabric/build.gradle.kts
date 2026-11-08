import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("dev.architectury.loom") version "1.7.435"
}

val minecraftVersion = (findProperty("target_minecraft_version") as String?) ?: (property("minecraft_version") as String)
val loaderVersion = (findProperty("target_loader_version") as String?) ?: (property("loader_version") as String)
val fabricApiVersion = (findProperty("target_fabric_api_version") as String?) ?: (property("fabric_api_version") as String)
val fabricLoaderDependency = (findProperty("target_fabric_loader_dependency") as String?) ?: ">=0.15.0"
val fabricMinecraftDependency = (findProperty("target_fabric_minecraft_dependency") as String?) ?: minecraftVersion
val javaDependency = (findProperty("target_java_version") as String?) ?: "17"
val archives_name: String by project
val commonProject = project(":common")

loom {
    mixin {
        defaultRefmapName.set("todolist.mixins.refmap.json")
    }
}

base {
    archivesName.set("$archives_name-fabric-$minecraftVersion")
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
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":common", configuration = "namedElements"))
    // H2 引擎走 Jar-in-Jar：dev classpath 用 Maven Central 坐标（离线走 Gradle 缓存），
    // 打包经 loom include 以嵌套 jar 交付，避免千余个散装类触发 CurseForge 自动扫描误报
    implementation("com.h2database:h2:2.2.220")
    include("com.h2database:h2:2.2.220")
    
    modCompileOnly("com.terraformersmc:modmenu:7.2.2")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("fabric_loader_dependency", fabricLoaderDependency)
    inputs.property("fabric_minecraft_dependency", fabricMinecraftDependency)
    inputs.property("java_dependency", javaDependency)
    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "fabric_loader_dependency" to fabricLoaderDependency,
                "fabric_minecraft_dependency" to fabricMinecraftDependency,
                "java_dependency" to javaDependency
            )
        )
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

// 网络兼容层冒烟测试：随 check 一起在所有目标版本上执行，
// 拦截签名发现、codec 注册（cast 语义）等发布环境才暴露的回归
val networkingSmokeTest = tasks.register<JavaExec>("networkingCompatSmokeTest") {
    group = "verification"
    description = "Run the FabricNetworkingCompat registration smoke tests on the current API generation."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.todolist.compat.FabricNetworkingCompatSmokeTestMain")
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

tasks.named("check") {
    dependsOn(networkingSmokeTest)
    dependsOn("mixinVisibilityTest")
}
