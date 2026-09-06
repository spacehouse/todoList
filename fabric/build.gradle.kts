import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("dev.architectury.loom")
}

val archives_name: String by project
val commonProject = project(":common")
// 版本矩阵入口：build-local-121x.bat 注入 target_* 属性；未注入时回退 gradle.properties
val minecraftVersion = (findProperty("target_minecraft_version") as String?) ?: (property("minecraft_version") as String)
val loaderVersion = (findProperty("target_loader_version") as String?) ?: (property("loader_version") as String)
val fabricApiVersion = (findProperty("target_fabric_api_version") as String?) ?: (property("fabric_api_version") as String)
val modmenuVersion = (findProperty("target_modmenu_version") as String?) ?: (property("modmenu_version") as String)
val fabricLoaderDependency = (findProperty("target_fabric_loader_dependency") as String?) ?: ">=0.18.1"
val fabricMinecraftDependency = (findProperty("target_fabric_minecraft_dependency") as String?) ?: minecraftVersion
val apiGroup = (findProperty("target_api_group") as String?) ?: "v1_21_1"
val enableModMenu = (findProperty("enable_modmenu") as String?)?.toBoolean()
    ?: !gradle.startParameter.isOffline
val effectiveModMenu = enableModMenu
val h2Jar = rootProject.file("libs/h2-2.2.220.jar")

base {
    archivesName.set("$archives_name-fabric-$minecraftVersion")
}

dependencies {
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
    inputs.property("fabric_loader_dependency", fabricLoaderDependency)
    inputs.property("fabric_minecraft_dependency", fabricMinecraftDependency)
    val modmenuEntrypoints = if (effectiveModMenu) {
        """["com.todolist.client.ModMenuIntegration"]"""
    } else {
        "[]"
    }
    inputs.property("modmenuEntrypoints", modmenuEntrypoints)
    filesMatching("fabric.mod.json") {
        expand(mapOf(
            "version" to project.version,
            "fabric_loader_dependency" to fabricLoaderDependency,
            "fabric_minecraft_dependency" to fabricMinecraftDependency
        ))
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
            // 兼容组覆盖源集：api_group != v1_21_1 时，src/main/<api_group>/java
            // 中的同名类覆盖基线源码，用于隔离 1.21.2+ 编译期 API 差异
            if (apiGroup != "v1_21_1") {
                val overrideDir = layout.projectDirectory.dir("src/main/$apiGroup/java")
                if (overrideDir.asFile.exists()) {
                    val baselineDir = file("src/main/java")
                    val overriddenPaths = overrideDir.asFileTree.files.map {
                        it.relativeTo(overrideDir.asFile).path.replace('\\', '/')
                    }.toSet()
                    // srcDirs 只放目录（loom 会校验 srcDirs 必须为目录），基线中被覆盖的同名文件改由 filter 按绝对路径排除
                    setSrcDirs(listOf(baselineDir, overrideDir))
                    filter.exclude { element ->
                        val f = element.file
                        f.absolutePath.startsWith(baselineDir.absolutePath + File.separator) &&
                            overriddenPaths.contains(
                                baselineDir.toPath().relativize(f.toPath()).toString().replace('\\', '/')
                            )
                    }
                    // allSource 的 filter 独立于 java（source() 仅复制 srcDirs 不复制 filter），
                    // sourcesJar 等消费 allSource 的任务需重复同样排除，避免同名文件重复打包
                    allSource.filter.exclude { element ->
                        val f = element.file
                        f.absolutePath.startsWith(baselineDir.absolutePath + File.separator) &&
                            overriddenPaths.contains(
                                baselineDir.toPath().relativize(f.toPath()).toString().replace('\\', '/')
                            )
                    }
                }
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
