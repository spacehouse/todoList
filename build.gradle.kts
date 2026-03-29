import org.gradle.internal.os.OperatingSystem

plugins {
    id("maven-publish")
}

val tripletCompareScript = layout.projectDirectory.file("tools/triplet-compare/compare-command-result-triplet.ps1")
val tripletBaselineSample = layout.projectDirectory.file("tools/triplet-compare/baseline-sample.json")
val tripletLogSample = layout.projectDirectory.file("tools/triplet-compare/log-sample.txt")
val tripletReport = layout.buildDirectory.file("reports/triplet-sample-diff.json")

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
        include("todolist-fabric-*.jar")
        exclude("*-sources.jar", "*-dev.jar")
    }
    from(project(":forge").layout.buildDirectory.dir("libs")) {
        include("todolist-forge-*.jar")
        exclude("*-sources.jar", "*-dev.jar")
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
    dependsOn("distReleaseJars", "tripletSampleCheck")
}

// 注册 clean 任务，用于清理根目录 build 文件夹
tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
