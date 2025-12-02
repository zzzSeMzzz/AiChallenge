import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*
import org.gradle.jvm.toolchain.JavaLanguageVersion // Вам может понадобиться этот импорт


plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "ru.sem.ai.challenge"
version = "1.0-SNAPSHOT"

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    } else {
        throw GradleException("Файл local.properties не найден в корне проекта")
    }
}

val yaApiKey: String = localProperties.getProperty("YA_API_KEY")
    ?: throw GradleException("API_KEY не найден в local.properties")
val cloudFolder: String = localProperties.getProperty("CLOUD_FOLDER")
    ?: throw GradleException("API_KEY не найден в local.properties")

// Генерация BuildConfig
val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/src/main/kotlin")
    //val outputFile = outputDir.get().file("core/BuildConfig.kt").asFile
    val outputFile = outputDir.get().file("BuildConfig.kt").asFile

    inputs.property("yaApiKey", yaApiKey)
    inputs.property("cloudFolder", cloudFolder)
    outputs.file(outputFile)

    doFirst {
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """package core

                  object BuildConfig {
                      const val YA_API_KEY: String = "$yaApiKey"
                      const val CLOUD_FOLDER: String = "$cloudFolder"
                  }
            """.trimIndent()
        )
    }
}

// ✅ Важно: объявить sourceSets ПОСЛЕ generateBuildConfig
sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/src/main/kotlin"))
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // Корутины
    /*implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")*/

    implementation("io.ktor:ktor-client-core:3.0.2")
    implementation("io.ktor:ktor-client-cio:3.0.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")

    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}

// 👇 НОВЫЙ БЛОК: Правильное место для jvmToolchain
kotlin {
    jvmToolchain(17)
}

tasks.withType<KotlinCompile> {
    dependsOn(generateBuildConfig)
    //kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}