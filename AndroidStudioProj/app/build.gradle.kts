plugins {
    alias(libs.plugins.android.application)
}

// ── Auto-sync all JSON scenarios from data/ into assets/scenarios/ ──
val syncScenarios by tasks.registering(Sync::class) {
    from("${rootDir}/../data") {
        include("**/*.json")
        exclude("official_catalog.json")
    }
    into("${projectDir}/src/main/assets/scenarios")
}
tasks.named("preBuild") { dependsOn(syncScenarios) }

android {
    namespace = "com.danielharton.scamshieldcompanion"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.danielharton.scamshieldcompanion"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}