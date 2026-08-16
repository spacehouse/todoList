import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.the

plugins {
    id("dev.architectury.loom") version "1.7.435"
}

val minecraftVersion = (findProperty("target_minecraft_version") as String?) ?: (property("minecraft_version") as String)
val loaderVersion = (findProperty("target_loader_version") as String?) ?: (property("loader_version") as String)
val needs1202TestShim = minecraftVersion in setOf("1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6")
val needs1205TestShim = minecraftVersion in setOf("1.20.5", "1.20.6")
val archives_name: String by project
base {
    archivesName.set("$archives_name-common")
}

dependencies {
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
val generatedTestSourcesDir = layout.buildDirectory.dir("generated/sources/versionedTest/java").get().asFile

val prepareVersionedTestSources = tasks.register("prepareVersionedTestSources") {
    group = "build setup"
    description = "Copy test sources and apply target-version constructor shims when needed."
    inputs.dir(layout.projectDirectory.dir("src/test/java"))
    inputs.property("minecraftVersion", minecraftVersion)
    inputs.property("needs1202TestShim", needs1202TestShim)
    inputs.property("needs1205TestShim", needs1205TestShim)
    outputs.dir(generatedTestSourcesDir)

    doLast {
        val sourceDir = file("src/test/java")
        delete(generatedTestSourcesDir)
        generatedTestSourcesDir.mkdirs()
        sourceDir.copyRecursively(generatedTestSourcesDir, overwrite = true)

        if (!needs1202TestShim) {
            return@doLast
        }

        val bootstrapTestFile = generatedTestSourcesDir.resolve("com/todolist/bootstrap/CommandBootstrapIntegrationTestMain.java")
        var bootstrapContent = bootstrapTestFile.readText(Charsets.UTF_8)
        if (!bootstrapContent.contains("import net.minecraft.server.level.ClientInformation;")) {
            bootstrapContent = bootstrapContent.replace(
                "import net.minecraft.server.level.ServerPlayer;",
                "import net.minecraft.server.level.ClientInformation;\nimport net.minecraft.server.level.ServerPlayer;"
            )
        }
        bootstrapContent = bootstrapContent.replace(
            "            super(null, null, null);",
            "            super(null, null, null, (ClientInformation) null);"
        )
        bootstrapTestFile.writeText(bootstrapContent, Charsets.UTF_8)

        val fakeClientConnectionFile = generatedTestSourcesDir.resolve("com/todolist/gui/testsupport/FakeClientConnection.java")
        var fakeClientConnectionContent = fakeClientConnectionFile.readText(Charsets.UTF_8)
        if (!fakeClientConnectionContent.contains("import net.minecraft.client.multiplayer.CommonListenerCookie;")) {
            fakeClientConnectionContent = fakeClientConnectionContent.replace(
                "import net.minecraft.client.multiplayer.ClientPacketListener;",
                "import net.minecraft.client.multiplayer.ClientPacketListener;\nimport net.minecraft.client.multiplayer.CommonListenerCookie;"
            )
        }
        fakeClientConnectionContent = fakeClientConnectionContent.replace(
            "        super((Minecraft) null, (Screen) null, (Connection) null, (ServerData) null, (GameProfile) null, (WorldSessionTelemetryManager) null);",
            "        super((Minecraft) null, (Connection) null, (CommonListenerCookie) null);"
        )
        fakeClientConnectionFile.writeText(fakeClientConnectionContent, Charsets.UTF_8)

        if (!needs1205TestShim) {
            return@doLast
        }

        // 1.20.5+ MinecraftServer 新增 getTickTimeLogger()/isTickTimeLoggingEnabled() 抽象方法，
        // 仅在生成阶段注入实现，保持 1.20.1~1.20.4 源码不受影响。
        val testServerAnchor = "    static class TestMinecraftServer extends MinecraftServer {"
        val testServerShim = """
            |    static class TestMinecraftServer extends MinecraftServer {
            |        @Override
            |        protected net.minecraft.util.debugchart.SampleLogger getTickTimeLogger() {
            |            return new net.minecraft.util.debugchart.SampleLogger() {
            |                @Override
            |                public void logFullSample(long[] values) {
            |                }
            |
            |                @Override
            |                public void logSample(long value) {
            |                }
            |
            |                @Override
            |                public void logPartialSample(long value, int percentile) {
            |                }
            |            };
            |        }
            |
            |        @Override
            |        public boolean isTickTimeLoggingEnabled() {
            |            return false;
            |        }
        """.trimMargin()
        if (!bootstrapContent.contains("isTickTimeLoggingEnabled")) {
            bootstrapContent = bootstrapContent.replace(testServerAnchor, testServerShim)
            bootstrapTestFile.writeText(bootstrapContent, Charsets.UTF_8)
        }

        // 1.20.5+ 按钮键盘激活会经 Minecraft.getInstance() 播放音效，
        // 在生成阶段把静态实例指向测试客户端，避免 1.20.1~1.20.4 测试行为变化。
        val guiSupportFile = generatedTestSourcesDir.resolve("com/todolist/gui/testsupport/GuiTestSupport.java")
        var guiSupportContent = guiSupportFile.readText(Charsets.UTF_8)
        val guiInstanceAnchor = "        minecraft.setTestOptions(options);\n        return minecraft;"
        val guiInstanceShim = """
            |        minecraft.setTestOptions(options);
            |        setStaticObjectField(Minecraft.class, "instance", minecraft);
            |        return minecraft;
        """.trimMargin()
        if (guiSupportContent.contains(guiInstanceAnchor)) {
            guiSupportContent = guiSupportContent.replace(guiInstanceAnchor, guiInstanceShim)
            guiSupportFile.writeText(guiSupportContent, Charsets.UTF_8)
        }
    }
}

testSourceSet.java.setSrcDirs(listOf(generatedTestSourcesDir))
tasks.named(testSourceSet.compileJavaTaskName) {
    dependsOn(prepareVersionedTestSources)
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
