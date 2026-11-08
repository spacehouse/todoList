import org.gradle.internal.os.OperatingSystem
import java.util.zip.ZipFile

plugins {
    id("maven-publish")
}

val tripletCompareScript = layout.projectDirectory.file("tools/triplet-compare/compare-command-result-triplet.ps1")
val tripletBaselineSample = layout.projectDirectory.file("tools/triplet-compare/baseline-sample.json")
val tripletLogSample = layout.projectDirectory.file("tools/triplet-compare/log-sample.txt")
val tripletReport = layout.buildDirectory.file("reports/triplet-sample-diff.json")
val h2JarContentReport = layout.buildDirectory.file("reports/h2-driver-content-check.txt")
val releaseMinecraftVersion = property("minecraft_version") as String
val releaseModVersion = property("mod_version") as String

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    group = property("maven_group") as String
    version = property("mod_version") as String

    repositories {
        mavenCentral()
        maven {
            name = "Modrinth"
            url = uri("https://maven.modrinth.com")
        }
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Terraformers"
            url = uri("https://maven.terraformersmc.com/releases/")
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
    }
}

tasks.register<Copy>("distReleaseJars") {
    group = "distribution"
    description = "Collect release-ready loader jars into root build/dist (exclude sources/dev)."

    dependsOn(":fabric:build", ":forge:build")

    into(layout.buildDirectory.dir("libs"))

    from(project(":fabric").layout.buildDirectory.dir("libs")) {
        include("todolist-fabric-$releaseMinecraftVersion-$releaseModVersion.jar")
        exclude("*-sources.jar", "*-dev.jar")
    }
    from(project(":forge").layout.buildDirectory.dir("libs")) {
        include("todolist-forge-$releaseMinecraftVersion-$releaseModVersion.jar")
        exclude("*-sources.jar", "*-dev.jar")
    }
}

tasks.register("h2JarContentCheck") {
    group = "verification"
    description = "Check release jars deliver H2 as exactly one nested jar with zero raw org/h2 entries."

    dependsOn("distReleaseJars")
    outputs.file(h2JarContentReport)

    doLast {
        val distDir = layout.buildDirectory.dir("libs").get().asFile
        val releaseJars = listOf(
            "fabric" to distDir.resolve("todolist-fabric-$releaseMinecraftVersion-$releaseModVersion.jar"),
            "forge" to distDir.resolve("todolist-forge-$releaseMinecraftVersion-$releaseModVersion.jar")
        )

        val reportLines = mutableListOf<String>()
        releaseJars.forEach { (loader, jarFile) ->
            if (!jarFile.isFile) {
                throw GradleException("Missing $loader release jar: ${jarFile.absolutePath}")
            }
            val rawH2Count: Int
            val nestedH2Count: Int
            ZipFile(jarFile).use { zipFile ->
                val entries = zipFile.entries().asSequence().map { it.name }.toList()
                rawH2Count = entries.count { it.startsWith("org/h2/") }
                nestedH2Count = entries.count { it.startsWith("META-INF/jars/") && it.endsWith(".jar") && it.contains("h2-") }
                val jarsDeclared = if (loader == "fabric") {
                    entries.contains("fabric.mod.json") &&
                        zipFile.getInputStream(zipFile.getEntry("fabric.mod.json"))
                            .readBytes().decodeToString().contains("META-INF/jars/")
                } else {
                    entries.any { it == "META-INF/jarjar/metadata.json" }
                }
                if (!jarsDeclared) {
                    throw GradleException("$loader release jar must declare the nested H2 jar (fabric.mod.json jars section / jarjar metadata)")
                }
            }
            reportLines += "$loader=${jarFile.name}, rawOrgH2=$rawH2Count, nestedH2Jar=$nestedH2Count"
            if (rawH2Count != 0 || nestedH2Count != 1) {
                throw GradleException("$loader release jar must contain zero raw org/h2 entries and exactly one nested H2 jar, actual: raw=$rawH2Count nested=$nestedH2Count")
            }
        }

        val reportFile = h2JarContentReport.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(reportLines.joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
    }
}

tasks.register<Exec>("tripletSampleCheck") {
    group = "verification"
    description = "Validate COMMAND_RESULT_TRIPLET sample baseline against the sample log."

    inputs.file(tripletCompareScript)
    inputs.file(tripletBaselineSample)
    inputs.file(tripletLogSample)
    outputs.file(tripletReport)

    val powerShellExecutable = if (OperatingSystem.current().isWindows) {
        "powershell"
    } else {
        "pwsh"
    }

    commandLine(
        powerShellExecutable,
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        tripletCompareScript.asFile.absolutePath,
        "-LogPath",
        tripletLogSample.asFile.absolutePath,
        "-BaselinePath",
        tripletBaselineSample.asFile.absolutePath,
        "-ReportPath",
        tripletReport.get().asFile.absolutePath
    )
}

// 注册根目录 build 任务，并让其触发 distReleaseJars
tasks.register("build") {
    dependsOn("distReleaseJars", "h2JarContentCheck", "tripletSampleCheck")
}

// 注册 clean 任务，用于清理根目录 build 文件夹
tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
