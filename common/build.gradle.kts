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
    // 版本矩阵入口：build-local-121x.bat 注入 target_* 属性；未注入时回退 gradle.properties
    val minecraftVersion = (findProperty("target_minecraft_version") as String?) ?: (property("minecraft_version") as String)
    val loaderVersion = (findProperty("target_loader_version") as String?) ?: (property("loader_version") as String)
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

// 兼容组覆盖源集：api_group != v1_21_1 时，src/<sourceSet>/<api_group>/java 中的同名类
// 覆盖基线源码（main 与 test 同步覆盖），用于隔离 1.21.2+ 的编译期 API 差异；
// 目录不存在时跳过，v1_21_1 走原生 srcDir，行为与现状完全一致。
val apiGroup = (findProperty("target_api_group") as String?) ?: "v1_21_1"
if (apiGroup != "v1_21_1") {
    fun applyOverrideSourceSet(sourceSetName: String) {
        val overrideDir = layout.projectDirectory.dir("src/$sourceSetName/$apiGroup/java")
        if (!overrideDir.asFile.exists()) return
        val baselineDir = file("src/$sourceSetName/java")
        val overriddenPaths = overrideDir.asFileTree.files.map {
            it.relativeTo(overrideDir.asFile).path.replace('\\', '/')
        }.toSet()
        sourceSets.named(sourceSetName) {
            // srcDirs 只放目录（loom 会校验 srcDirs 必须为目录），基线中被覆盖的同名文件改由 filter 按绝对路径排除
            java.setSrcDirs(listOf(baselineDir, overrideDir))
            java.filter.exclude { element ->
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
    applyOverrideSourceSet("main")
    applyOverrideSourceSet("test")
}

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

// GUI 场景测试走真实事件路径：EditBox.onClick/keyPressed → Screen.hasShiftDown →
// InputConstants/GLFW 会加载 LWJGL native 库（glfwGetKey 在 GLFW 未初始化时安全返回
// RELEASE，不会创建窗口）。LWJGL 3.3 支持从 classpath 上的 natives jar 提取共享库，
// 因此把 loom 提供的 minecraftNatives 配置追加进测试 JVM classpath 即可离线通过；
// 配置不存在时（loom 版本变化）跳过，测试用例会显式失败提醒。
val guiTestNatives = configurations.findByName("minecraftNatives")
if (guiTestNatives != null) {
    tasks.named<JavaExec>("guiSystemTest") {
        classpath(guiTestNatives)
    }
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

tasks.register<JavaExec>("h2TaskQueryServiceTest") {
    group = "verification"
    description = "Run the M4-A H2 task query service self-tests."
    classpath = files(mainSourceSet.output, testSourceSet.output, mainSourceSet.compileClasspath, testSourceSet.compileClasspath)
    mainClass.set("com.todolist.storage.H2TaskQueryServiceTestMain")
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
    dependsOn("h2TaskQueryServiceTest")
}

tasks.withType<Test>().configureEach {
    enabled = false
}
