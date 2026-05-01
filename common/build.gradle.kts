import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.the

plugins {
    id("dev.architectury.loom")
}

val archives_name: String by project
base {
    archivesName.set("$archives_name-common")
}

dependencies {
    val minecraftVersion = property("minecraft_version") as String
    val loaderVersion = property("loader_version") as String
    val h2Jar = rootProject.file("libs/h2-2.2.220.jar")

    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    compileOnly("net.fabricmc:fabric-loader:$loaderVersion")
    testCompileOnly("net.fabricmc:fabric-loader:$loaderVersion")

    compileOnly("org.slf4j:slf4j-api:2.0.7")
    testImplementation(files(h2Jar))
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

tasks.register<JavaExec>("h2DiagnosticTest") {
    group = "verification"
    description = "Run the H2 M1-0 driver, temporary database, DDL, backup, script, and TCP API diagnostics."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.storage.H2DiagnosticTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.register<JavaExec>("storageBackendSelectionTest") {
    group = "verification"
    description = "Run the M1-A storage backend selection self-tests."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.storage.StorageBackendSelectionTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.register<JavaExec>("h2SchemaInitializerTest") {
    group = "verification"
    description = "Run the M1-B H2 schema initializer self-tests."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.storage.H2SchemaInitializerTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.register<JavaExec>("h2LegacyMigrationTest") {
    group = "verification"
    description = "Run the M1-C legacy NBT to H2 migration self-tests."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.storage.H2LegacyMigrationTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.register<JavaExec>("h2StorageBackendIntegrationTest") {
    group = "verification"
    description = "Run the M1-D H2 storage backend integration self-tests."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.storage.H2StorageBackendIntegrationTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.register<JavaExec>("h2StorageAvailabilityTest") {
    group = "verification"
    description = "Run the M1-E H2 storage availability self-tests."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.storage.H2StorageAvailabilityTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.register<JavaExec>("h2TcpAccessTest") {
    group = "verification"
    description = "Run the M2 H2 TCP access self-tests."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.storage.H2TcpAccessTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.register<JavaExec>("h2CommandIntegrationTest") {
    group = "verification"
    description = "Run the M2 H2 command integration self-tests."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.bootstrap.CommandBootstrapH2IntegrationTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.register<JavaExec>("h2MaintenanceBackupTest") {
    group = "verification"
    description = "Run the M3-A H2 maintenance lock and backup self-tests."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.storage.H2MaintenanceBackupTestMain")
    dependsOn(tasks.named(testSourceSet.classesTaskName))
}

tasks.named("check").configure {
    dependsOn("commandSystemTest")
    dependsOn("guiSystemTest")
    dependsOn("h2DiagnosticTest")
    dependsOn("storageBackendSelectionTest")
    dependsOn("h2SchemaInitializerTest")
    dependsOn("h2LegacyMigrationTest")
    dependsOn("h2StorageBackendIntegrationTest")
    dependsOn("h2StorageAvailabilityTest")
    dependsOn("h2TcpAccessTest")
    dependsOn("h2CommandIntegrationTest")
    dependsOn("h2MaintenanceBackupTest")
}

tasks.withType<Test>().configureEach {
    enabled = false
}
