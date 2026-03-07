import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("dev.architectury.loom") version "1.6-SNAPSHOT"
}

val archives_name: String by project
base {
    archivesName.set("$archives_name-common")
}

dependencies {
    val minecraftVersion = property("minecraft_version") as String
    val loaderVersion = property("loader_version") as String

    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    compileOnly("net.fabricmc:fabric-loader:$loaderVersion")

    compileOnly("org.slf4j:slf4j-api:2.0.7")
}

loom {
    accessWidenerPath.set(file("src/main/resources/todolist.accesswidener"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    doFirst {
        source.files
            .filter { it.extension.equals("java", ignoreCase = true) && it.exists() }
            .forEach { javaFile ->
                val content = javaFile.readText(Charsets.UTF_8)
                if (content.startsWith('\uFEFF')) {
                    javaFile.writeText(content.substring(1), Charsets.UTF_8)
                }
            }
    }
}
