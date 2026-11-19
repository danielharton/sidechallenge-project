plugins {
    alias(libs.plugins.android.application)
}

// ── Test scenarios: every JSON in <repo>/data is packaged as assets/scenarios/** ──
// Generated into build/ on every build (and after Clean/Rebuild), so new or edited files in data/
// are always picked up. Nothing is copied into src/, so there is no stale duplicate to forget.
abstract class SyncScenariosTask : DefaultTask() {
    @get:Internal
    abstract val dataDir: DirectoryProperty

    /** Only the scenario files are tracked, not the large training .jsonl files. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scenarioFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun sync() {
        fs.sync {
            from(dataDir) {
                include("**/*.json")
                exclude("official_catalog.json")
            }
            into(outputDir.dir("scenarios"))
        }
    }
}

val scenariosData = rootProject.layout.projectDirectory.dir("../data")
val syncScenarios = tasks.register<SyncScenariosTask>("syncScenarios") {
    dataDir.set(scenariosData)
    scenarioFiles.from(fileTree(scenariosData) {
        include("**/*.json")
        exclude("official_catalog.json")
    })
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncScenarios, SyncScenariosTask::outputDir)
    }
}

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