plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.videospeedpitch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.videospeedpitch"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        viewBinding = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Lista de músicas dos catálogos (busca por cantor/música)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Necessário para ler/indexar arquivos da pasta de vídeos escolhida via SAF
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Media3 / ExoPlayer - suporta variar velocidade e tom (pitch) de forma independente
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
}
