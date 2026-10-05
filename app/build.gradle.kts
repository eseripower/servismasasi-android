plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Sürüm ve imza bilgisi derleme sırasında dışarıdan verilir; depoda hiçbir anahtar ya da parola tutulmaz.
val surumAd = (findProperty("surumAd") as String?) ?: "1.0.0"
val surumKod = ((findProperty("surumKod") as String?) ?: "1").toInt()
val imzaOzet = (findProperty("imzaOzet") as String?) ?: ""
// Deneme: sunucuda SSL yokken kavissoft.com adreslerine şifresiz bağlantıya izin verir (SSL kurulunca kapatılır)
val sslsiz = ((findProperty("sslsiz") as String?) ?: "0").trim() == "1"
val imzaDosya = System.getenv("IMZA_DOSYA") ?: ""
val panelAdres = (findProperty("panelAdres") as String?) ?: "https://kavissoft.com/cloudsoft,https://cloudsoft.kavissoft.com,https://kavissoft.com"

android {
    namespace = "com.kavissoft.servismasasi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kavissoft.servismasasi"
        minSdk = 26
        targetSdk = 35
        versionCode = surumKod
        versionName = surumAd
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }   // gerçek telefonlar; emülatör mimarileri pakete girmez
        buildConfigField("String", "PANEL", "\"$panelAdres\"")
        buildConfigField("String", "IMZA_OZET", "\"$imzaOzet\"")
        buildConfigField("boolean", "SSLSIZ", sslsiz.toString())
        manifestPlaceholders["agGuvenlik"] = if (sslsiz) "@xml/network_security_config_sslsiz" else "@xml/network_security_config"
    }

    signingConfigs {
        if (imzaDosya.isNotEmpty()) {
            create("yayin") {
                storeFile = file(imzaDosya)
                storePassword = System.getenv("IMZA_PAROLA")
                keyAlias = System.getenv("IMZA_TAKMA_AD") ?: "servismasasi"
                keyPassword = System.getenv("IMZA_PAROLA")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (imzaDosya.isNotEmpty()) signingConfig = signingConfigs.getByName("yayin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Etiket okuma (barkod + yazı): modeller uygulamanın içindedir, okuma telefonda yapılır
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Canlı kamera önizlemesi (etiket okutma penceresi)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
}
