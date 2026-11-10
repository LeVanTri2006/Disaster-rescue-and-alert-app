import java.util.Properties
plugins {
    alias(libs.plugins.secrets.gradle.plugin)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.bai1"
    compileSdk {
        version = release(37)
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.example.bai1"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        val mapsApiKey = localProperties.getProperty("MAPS_API_KEY") ?: "PLACEHOLDER"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        noCompress += "gz"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildTypes {
        debug {
            val sosApiBaseUrl = project.findProperty("sosApiBaseUrl")?.toString()
                ?: localProperties.getProperty("sosApiBaseUrl")
                ?: "http://192.168.2.228:3000"
            buildConfigField("String", "SOS_API_BASE_URL", "\"$sosApiBaseUrl\"")
            buildConfigField("String", "FIREBASE_DATABASE_URL", "\"https://canhbao-34075-default-rtdb.asia-southeast1.firebasedatabase.app\"")
        }
        release {
            val sosApiBaseUrl = project.findProperty("sosApiBaseUrl")?.toString() ?: "https://api.example.invalid"
            buildConfigField("String", "SOS_API_BASE_URL", "\"$sosApiBaseUrl\"")
            buildConfigField("String", "FIREBASE_DATABASE_URL", "\"https://canhbao-34075-default-rtdb.asia-southeast1.firebasedatabase.app\"")
        }
    }
}

dependencies {
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.firebase.ai)
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-auth")
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation("com.google.firebase:firebase-database")
    implementation(libs.play.services.location)
    implementation(libs.navigation.compose)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("io.coil-kt:coil-compose:2.6.0")
}
