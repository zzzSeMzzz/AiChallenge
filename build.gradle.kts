import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    kotlin("jvm") version "1.9.22"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // GSON (Retrofit использует встроенный Gson, но можно указать явно)
    implementation("com.google.code.gson:gson:2.10.1")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    dependsOn(generateBuildConfig)
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}