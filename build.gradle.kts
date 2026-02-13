plugins {
    id("fabric-loom") version "1.3.8"
    id("maven-publish")
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:1.20.1")
    mappings("net.fabricmc:yarn:1.20.1+build.10:v2")
    modImplementation("net.fabricmc:fabric-loader:0.14.21")

    // Fabric API - deprecated but works
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.86.1+1.20.1")
}

base {
    archivesName.set(properties["archives_name"] as String)
}

version = properties["mod_version"] as String
group = properties["maven_group"] as String

repositories {
    mavenCentral()
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${base.archivesName.get()}" }
        }
    }

    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to project.version))
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        withSourcesJar()
    }
}
