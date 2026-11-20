import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Credenciales de Supabase: se leen de supabase.properties (fuera de git, RNF-SEG-03).
val supabaseProps = Properties().apply {
    val archivo = rootProject.file("supabase.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

// Entrega 7: Firebase Cloud Messaging (push). Identificadores PÚBLICOS de la app en
// firebase.properties (fuera de git). Sin ese archivo la app funciona y revisa los avisos
// periódicamente; el envío lo hace servicios/notificador desde un servidor, nunca la app.
val firebaseProps = Properties().apply {
    val archivo = rootProject.file("firebase.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
    namespace = "com.example.trabajodea10"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.company.ultrapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.5-entrega7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${supabaseProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseProps.getProperty("supabase.anonKey", "")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseProps.getProperty("firebase.appId", "")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseProps.getProperty("firebase.apiKey", "")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseProps.getProperty("firebase.projectId", "")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${firebaseProps.getProperty("firebase.senderId", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)

    // Backend Supabase (Auth, base de datos y Storage) por su API HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Biometrics + Camera + QR + Coil
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.zxing:core:3.5.3")

    // Entrega 6: mapas OpenStreetMap sin clave de API (RF-MAP-01 a 04)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Entrega 7: avisos (revisión periódica y push con Firebase Cloud Messaging)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.firebase:firebase-messaging:24.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
