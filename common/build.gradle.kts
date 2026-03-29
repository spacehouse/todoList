import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.the

plugins {
    id("dev.architectury.loom") version "1.7.435"
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
    testCompileOnly("net.fabricmc:fabric-loader:$loaderVersion")

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

val sourceSets = the<SourceSetContainer>()
val mainSourceSet = sourceSets["main"]
val testSourceSet = sourceSets["test"]

tasks.register<JavaExec>("commandSystemTest") {
    group = "verification"
    description = "Run the offline command system self-tests without external test frameworks."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.bootstrap.CommandSystemTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.register<JavaExec>("guiSystemTest") {
    group = "verification"
    description = "Run the offline GUI self-tests without launching a real client."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.gui.GuiSystemTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.named("check").configure {
    dependsOn("commandSystemTest")
    dependsOn("guiSystemTest")
}

tasks.withType<Test>().configureEach {
    enabled = false
}
