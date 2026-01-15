import java.util.Properties
import java.io.File
import com.android.build.api.artifact.SingleArtifact
import org.gradle.configurationcache.extensions.capitalized

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kododake.aabrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kododake.aabrowser"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val localPropsFile = rootProject.file("local.properties")
            val localProps = Properties()
            if (localPropsFile.exists()) {
                localProps.load(localPropsFile.inputStream())
            }

            val storeFilePath = (project.findProperty("RELEASE_STORE_FILE") as String?)
                ?: localProps.getProperty("RELEASE_STORE_FILE")
                ?: System.getenv("RELEASE_STORE_FILE")
                ?: "../release.keystore"
            
            val sp = (project.findProperty("RELEASE_STORE_PASSWORD") as String?)
                ?: localProps.getProperty("RELEASE_STORE_PASSWORD")
                ?: System.getenv("RELEASE_STORE_PASSWORD")
            
            val ka = (project.findProperty("RELEASE_KEY_ALIAS") as String?)
                ?: localProps.getProperty("RELEASE_KEY_ALIAS")
                ?: System.getenv("RELEASE_KEY_ALIAS")
            
            val kp = (project.findProperty("RELEASE_KEY_PASSWORD") as String?)
                ?: localProps.getProperty("RELEASE_KEY_PASSWORD")
                ?: System.getenv("RELEASE_KEY_PASSWORD")

            storeFile = rootProject.file(storeFilePath)
            if (sp != null) storePassword = sp
            if (ka != null) keyAlias = ka
            if (kp != null) keyPassword = kp
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.google.material)
    implementation(libs.androidx.car.app)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}