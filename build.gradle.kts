plugins {
    id("maven-publish")
}

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

    into(layout.buildDirectory.dir("dist"))

    from(project(":fabric").layout.buildDirectory.dir("libs")) {
        include("todolist-fabric-*.jar")
        exclude("*-sources.jar", "*-dev.jar")
    }
    from(project(":forge").layout.buildDirectory.dir("libs")) {
        include("todolist-forge-*.jar")
        exclude("*-sources.jar", "*-dev.jar")
    }
}
