import java.util.Properties
import java.io.File
import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application)
    id("com.google.android.gms.oss-licenses-plugin")
}

android {
    namespace = "com.kododake.aabrowser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kododake.aabrowser"
        minSdk = 35
        targetSdk = 37
        versionCode = 8
        versionName = "2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val localProps = Properties().apply {
                val file = rootProject.file("local.properties")
                if (file.exists()) load(file.inputStream())
            }

            fun getProp(key: String): String? =
                project.findProperty(key) as? String
                    ?: localProps.getProperty(key)
                    ?: System.getenv(key)

            val storeFilePath = getProp("RELEASE_STORE_FILE") ?: "../release.keystore"
            storeFile = rootProject.file(storeFilePath)

            getProp("RELEASE_STORE_PASSWORD")?.let { storePassword = it }
            getProp("RELEASE_KEY_ALIAS")?.let { keyAlias = it }
            getProp("RELEASE_KEY_PASSWORD")?.let { keyPassword = it }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidComponents {
        onVariants { variant ->
            val vNameStr = android.defaultConfig.versionName ?: "unknown"
            val appNameStr = "AABrowser"
            val isDebug = variant.buildType == "debug"
            val debugSuffixStr = if (isDebug) "_debug" else ""

            val renameTaskProvider = tasks.register<RenameApkTask>("${variant.name}RenameApk") {
                inputDir.set(variant.artifacts.get(SingleArtifact.APK))
                outputDir.set(layout.buildDirectory.dir("renamedApks/${variant.name}"))

                appName.set(appNameStr)
                versionNameProp.set(vNameStr)
                debugSuffixProp.set(debugSuffixStr)
            }

            afterEvaluate {
                val assembleTaskName = "assemble${variant.name.replaceFirstChar { it.uppercase() }}"
                if (tasks.findByName(assembleTaskName) != null) {
                    tasks.named(assembleTaskName).configure {
                        finalizedBy(renameTaskProvider)
                    }
                }
            }
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// Optional: checks the generated uBO scriptlet runtime against the vendored upstream registry.
// It needs Node.js, so it only runs when a `node` binary is available (or NODE_BINARY is set);
// plain Android Studio builds and `./gradlew test` keep working without Node.
val nodeBinary: String? = providers.environmentVariable("NODE_BINARY").orNull
    ?: System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
        .asSequence()
        .map { File(it, if (System.getProperty("os.name").startsWith("Windows")) "node.exe" else "node") }
        .firstOrNull { it.isFile && it.canExecute() }
        ?.absolutePath

val testUboScriptletCompatibility by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the generated scriptlet runtime against the pinned upstream uBO registry (requires Node.js)."
    workingDir(rootProject.projectDir)
    onlyIf { nodeBinary != null }
    commandLine(nodeBinary ?: "node", "scripts/test_ubo_scriptlet_compatibility.cjs")
    inputs.file(rootProject.file("scripts/test_ubo_scriptlet_compatibility.cjs"))
    inputs.file(rootProject.file("app/src/main/assets/adblock/ubo-scriptlets.js"))
    inputs.dir(rootProject.file("third_party/ublock/src/js/resources"))
    inputs.file(rootProject.file("third_party/ublock/README.md"))
}

if (nodeBinary != null) {
    tasks.withType<Test>().configureEach { dependsOn(testUboScriptletCompatibility) }
} else {
    logger.lifecycle("Node.js not found: skipping testUboScriptletCompatibility")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.google.material)
    implementation(libs.androidx.car.app)
    implementation(libs.zxing.core)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.google.oss.licenses)
    implementation("com.github.woheller69:FreeDroidWarn:V1.+")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

abstract class RenameApkTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val versionNameProp: Property<String>

    @get:Input
    abstract val debugSuffixProp: Property<String>

    @TaskAction
    fun run() {
        val inDir = inputDir.get().asFile
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        val app = appName.get()
        val vName = versionNameProp.get()
        val debugSuffix = debugSuffixProp.get()

        inDir.listFiles()?.filter { it.extension == "apk" }?.forEach { f ->
            val newName = "${app}-${vName}${debugSuffix}.apk"
            val dest = File(outDir, newName)
            f.copyTo(dest, overwrite = true)
            println("APK Renamed and Copied to: ${dest.absolutePath}")
        }
    }
}