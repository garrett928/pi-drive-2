plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ghart.space.pi_drive"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ghart.space.pi_drive"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing placeholder — configure a signingConfig before publishing.
            // See SIGNING.md for instructions on creating and wiring a keystore.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.material)

    // Compose BOM — all compose-* versions come from here
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room (needed for DatabaseModule to call Room.databaseBuilder)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    // WorkManager (needed for Configuration.Provider + DelegatingWorkerFactory in Application)
    implementation(libs.work.runtime.ktx)

    implementation(libs.androidx.app.projected)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
}

// ── Release smoke test ────────────────────────────────────────────────────────
// Run with: ./gradlew :mobile:releaseSmoke
// Verifies the release APK assembles cleanly (minify + R8 rules are correct).
// Does not sign the APK — configure a signingConfig first for a signed build.
tasks.register("releaseSmoke") {
    dependsOn("assembleRelease")
    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apks = apkDir.listFiles { f -> f.extension == "apk" } ?: emptyArray()
        if (apks.isEmpty()) {
            throw GradleException("releaseSmoke: no APK found in $apkDir")
        }
        val apk = apks.first()
        val sizeMb = apk.length() / (1024.0 * 1024.0)
        println("✓ Release APK: ${apk.name}  (${String.format("%.1f", sizeMb)} MB)")
        if (sizeMb > 50.0) {
            logger.warn("WARNING: release APK is ${String.format("%.1f", sizeMb)} MB — consider investigating size")
        }
    }
}
