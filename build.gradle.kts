plugins {
    id("fabric-loom") version "1.2.7"
    id("maven-publish")
}

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

group = property("maven_group") as String
version = property("mod_version") as String

base {
    archivesName.set(property("archives_name") as String)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

dependencies {
    val minecraftVersion = property("minecraft_version") as String
    val yarnMappings = property("yarn_mappings") as String
    val loaderVersion = property("loader_version") as String
    val fabricApiVersion = property("fabric_api_version") as String

    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // modImplementation("maven.modrinth:malilib:malilib-fabric-1.20.1:0.16.0")
    modCompileOnly("com.terraformersmc:modmenu:7.2.2")
}

loom {
    accessWidenerPath.set(file("src/main/resources/todolist.accesswidener"))
}

configurations.configureEach {
    exclude(group = "io.netty", module = "netty-transport-native-epoll")
    exclude(group = "io.netty", module = "netty-transport-native-kqueue")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to version))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = group.toString()
            artifactId = base.archivesName.get()
            version = version.toString()
            from(components["java"])
        }
    }
}

val clientModsDir = file("E:/MC/cloudSave/mc-mss/version/.minecraft/versions/1.20.1-Fabric 0.15.11/mods")
val serverModsDir = file("E:/MC/server/fabric-0.15.11-server/mods")

tasks.register<Copy>("copyToClientMods") {
    from(layout.buildDirectory.dir("libs"))
    include("*.jar")
    exclude("*-sources.jar")
    into(clientModsDir)
}

tasks.register<Copy>("copyToServerMods") {
    from(layout.buildDirectory.dir("libs"))
    include("*.jar")
    exclude("*-sources.jar")
    into(serverModsDir)
}
