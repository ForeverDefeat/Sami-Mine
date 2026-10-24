import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val signingComplete = signingKeys.all { !releaseSigning.getProperty(it).isNullOrBlank() } &&
    rootProject.file(releaseSigning.getProperty("storeFile", "__missing__")).isFile
val validateReleaseCredentials = tasks.register("validateReleaseCredentials") {
    val credentialsAvailable = signingComplete
    doLast {
        check(credentialsAvailable) {
            "Firma release incompleta: configure storeFile, storePassword, keyAlias y keyPassword en keystore.properties. Consulte docs/FIREBASE_PILOTO.md."
        }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild" || name == "validateSigningRelease") dependsOn(validateReleaseCredentials)
}

android {
    namespace = "dev.studios.samimine"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.studios.samimine"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("versionCode").map {
            it.toIntOrNull()?.takeIf { code -> code in 1..2100000000 }
                ?: error("versionCode debe ser un entero entre 1 y 2100000000")
        }.getOrElse(1)
        versionName = providers.gradleProperty("versionName").map {
            it.takeIf { value -> value.isNotBlank() } ?: error("versionName no puede estar vacío")
        }.getOrElse("1.0")

        testInstrumentationRunner = "dev.studios.samimine.SamiMineTestRunner"
    }

    signingConfigs {
        if (signingComplete) create("distribution") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            if (signingComplete) signingConfig = signingConfigs.getByName("distribution")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.gson)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation("com.google.firebase:firebase-appdistribution-api:16.0.0-beta21")
    // This release variant is distributed exclusively to the Firebase pilot, never Google Play.
    releaseImplementation("com.google.firebase:firebase-appdistribution:16.0.0-beta21")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
