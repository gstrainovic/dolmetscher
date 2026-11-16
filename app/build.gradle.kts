import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 署名情報は keystore.properties（gitignore 済み）から読む。無ければ未署名でビルド。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) load(FileInputStream(secretsFile))
}

android {
    namespace = "jp.kyoto.sync.parley"
    compileSdk = 35

    defaultConfig {
        // Eigene Kennung, damit die App neben dem Original-Parley installierbar ist.
        applicationId = "ch.strainovic.dolmetscher"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.5.4"

        // sherpa-onnx (Silero VAD) bringt Bibliotheken für vier Prozessoren mit; Handys und Emulator brauchen zwei.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        // Schlüssel fest in der App, aber nicht im Repo: aus der ignorierten
        // secrets.properties, die werkzeuge/schluessel.sh aus ~/.config erzeugt.
        for (name in listOf(
            "GEMINI_KEY", "SUISSE_KEY", "MISTRAL_KEY", "OPENAI_KEY", "AZURE_KEY", "AZURE_REGION",
            "CLAUDE_SERVER_URL", "CLAUDE_SERVER_TOKEN",
        )) {
            buildConfigField("String", name, "\"${secrets.getProperty(name, "")}\"")
        }

        // Nur Entwickler arbeiten ohne Kopfhörer oder Brille (Emulator, Lautsprecher):
        // ./gradlew installDebug -Pentwickler=true. Benutzer können den Assistenten nicht überspringen,
        // deshalb nicht an BuildConfig.DEBUG gekoppelt (auch Benutzer-Versionen sind Debug-Builds).
        buildConfigField("boolean", "ENTWICKLER", (findProperty("entwickler") == "true").toString())
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Die Sprache der Oberfläche wählt die App selbst (Oberflaeche.kt): alle Sprachen gehören in jede Auslieferung.
    bundle {
        language { enableSplit = false }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.okhttp)
    // Silero VAD für die Sprecherkennung; werkzeuge/sherpa.sh lädt das Paket.
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
