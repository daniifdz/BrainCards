import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun signingProperty(propertyName: String, envName: String): String =
    (localProperties.getProperty(propertyName) ?: System.getenv(envName))
        ?: error("Missing $propertyName in local.properties or $envName environment variable")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    //---------------------------------------
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.serialization)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

// Genera SupabaseConfig.kt en build/ (ya gitignorado por **/build/) a partir de
// local.properties/env vars -- nunca hardcodeado, mismo principio que el keystore.
// Si no encuentra los valores, genera constantes vacias con un warning en vez de
// romper la build: hoy nada del proyecto depende todavia de Supabase para compilar.
val generatedSupabaseConfigDir = layout.buildDirectory.dir("generated/supabaseConfig/commonMain/kotlin")

val generateSupabaseConfig by tasks.registering {
    val outputDir = generatedSupabaseConfigDir
    val supabaseUrl = localProperties.getProperty("SUPABASE_URL")
        ?: System.getenv("BRAINCARDS_SUPABASE_URL")
    val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY")
        ?: System.getenv("BRAINCARDS_SUPABASE_ANON_KEY")

    inputs.property("supabaseUrl", supabaseUrl ?: "")
    inputs.property("supabaseAnonKey", supabaseAnonKey ?: "")
    outputs.dir(outputDir)

    doLast {
        if (supabaseUrl.isNullOrBlank() || supabaseAnonKey.isNullOrBlank()) {
            logger.warn(
                "SUPABASE_URL/SUPABASE_ANON_KEY no encontrados en local.properties ni en " +
                    "BRAINCARDS_SUPABASE_URL/BRAINCARDS_SUPABASE_ANON_KEY -- generando " +
                    "SupabaseConfig.kt con constantes vacias. El cliente de Supabase no " +
                    "funcionara hasta que se configuren."
            )
        }

        val packageDir = outputDir.get().asFile.resolve("org/braincards/app/config")
        packageDir.mkdirs()
        packageDir.resolve("SupabaseConfig.kt").writeText(
            """
            |package org.braincards.app.config
            |
            |internal const val SUPABASE_URL = "${supabaseUrl.orEmpty()}"
            |internal const val SUPABASE_ANON_KEY = "${supabaseAnonKey.orEmpty()}"
            |""".trimMargin()
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            // Sync automatica: ProcessLifecycleOwner (primer/segundo plano del PROCESO, no de la Activity).
            implementation(libs.androidx.lifecycle.process)
            //---------------------------------------------------------
            implementation(libs.koin.android)
            implementation(libs.ktor.client.android)
        }
        commonMain {
            kotlin.srcDir(generateSupabaseConfig)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            //---------------------------------------------------------
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(compose.materialIconsExtended)
            implementation(libs.androidx.savedstate)
            implementation(libs.kotlin.serialization)
            implementation(libs.navigation.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(platform(libs.supabase.bom.get()))
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
        }
    }
}

val jvmMainJar by tasks.registering(Jar::class) {
    group = "build"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "org.braincards.app.MainKt"
    }

    from(kotlin.targets["jvm"].compilations["main"].output.allOutputs)

    dependsOn(configurations["jvmRuntimeClasspath"])
    from({
        configurations["jvmRuntimeClasspath"].map { if (it.isDirectory) it else zipTree(it) }
    })
}

android {
    namespace = "org.braincards.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.braincards.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/braincards-key.jks")
            storePassword = signingProperty("KEYSTORE_PASSWORD", "BRAINCARDS_KEYSTORE_PASSWORD")
            keyAlias = "key0"
            keyPassword = signingProperty("KEY_PASSWORD", "BRAINCARDS_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")

            applicationVariants.all {
                outputs.all {
                    val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                    outputImpl.outputFileName = "BrainCards.apk"
                }
            }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
}

// El classpath del propio plugin de KSP (no el de Room como procesador, que ya resuelve bien)
// arrastra kotlinx-serialization-json 1.6.3, anterior a que GeneratedSerializer.typeParametersSerializers()
// tuviera implementacion "default". Choca con las clases de Room (FieldBundle$$serializer, etc.),
// compiladas esperando ese default -> AbstractMethodError al validar el esquema exportado.
configurations.matching { it.name == "kspPluginClasspath" || it.name == "kspPluginClasspathNonEmbeddable" }
    .configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization")) {
                useVersion(libs.versions.kotlinxSerialization.get())
            }
        }
    }

room {
    schemaDirectory("$projectDir/schemas")
}

compose.desktop {
    application {
        mainClass = "org.braincards.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "BrainCards"
            packageVersion = "1.0.0"

            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                menuGroup = "BrainCards"
                perUserInstall = true
            }
        }
    }
}