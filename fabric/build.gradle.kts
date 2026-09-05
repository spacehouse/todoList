import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("dev.architectury.loom") version "1.7.435"
}

val minecraftVersion = (findProperty("target_minecraft_version") as String?) ?: (property("minecraft_version") as String)
val loaderVersion = (findProperty("target_loader_version") as String?) ?: (property("loader_version") as String)
val fabricApiVersion = (findProperty("target_fabric_api_version") as String?) ?: (property("fabric_api_version") as String)
val fabricLoaderDependency = (findProperty("target_fabric_loader_dependency") as String?) ?: ">=0.15.0"
val fabricMinecraftDependency = (findProperty("target_fabric_minecraft_dependency") as String?) ?: minecraftVersion
val javaDependency = (findProperty("target_java_version") as String?) ?: "17"
val archives_name: String by project
val commonProject = project(":common")
val h2Jar = rootProject.file("libs/h2-2.2.220.jar")

base {
    archivesName.set("$archives_name-fabric-$minecraftVersion")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":common", configuration = "namedElements"))
    
    modCompileOnly("com.terraformersmc:modmenu:7.2.2")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("fabric_loader_dependency", fabricLoaderDependency)
    inputs.property("fabric_minecraft_dependency", fabricMinecraftDependency)
    inputs.property("java_dependency", javaDependency)
    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "fabric_loader_dependency" to fabricLoaderDependency,
                "fabric_minecraft_dependency" to fabricMinecraftDependency,
                "java_dependency" to javaDependency
            )
        )
    }
}

val commonMainOutput = commonProject.extensions
    .getByType(JavaPluginExtension::class.java)
    .sourceSets
    .getByName("main")
    .output

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(commonMainOutput)
    from(zipTree(h2Jar)) {
        exclude("META-INF/MANIFEST.MF")
    }
}

// 网络兼容层冒烟测试：随 check 一起在所有目标版本上执行，
// 拦截签名发现、codec 注册（cast 语义）等发布环境才暴露的回归
val networkingSmokeTest = tasks.register<JavaExec>("networkingCompatSmokeTest") {
    group = "verification"
    description = "Run the FabricNetworkingCompat registration smoke tests on the current API generation."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.todolist.compat.FabricNetworkingCompatSmokeTestMain")
}

tasks.named("check") {
    dependsOn(networkingSmokeTest)
}
