plugins {
 id("com.android.application") version "9.4.0"
 id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}
val appSource = file("../../app/src/main/java")
android {
 namespace = "com.example.bai1"
 compileSdk = 37
 defaultConfig {
  applicationId = "com.example.bai1.p5e10a.demo"
  minSdk = 30
  targetSdk = 37
  versionCode = 1
  versionName = "P5-E10A-DEMO"
  testInstrumentationRunner = "com.example.bai1.fixture.IsolatedInstrumentationRunner"
 }
 buildFeatures { compose = true; buildConfig = true }
 sourceSets.getByName("main").apply {
  listOf("algorithm","model","parser","loader","network","download","repository").forEach {
   kotlin.directories.add(File(appSource,"com/example/bai1/rescue/routing/$it").path)
  }
  kotlin.directories.add("build/generated/exact-source")
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    implementation("com.squareup.okio:okio:3.9.1")
    implementation("com.google.guava:guava:31.1-android")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.10")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.annotation:annotation-experimental:1.4.1")
    androidTestImplementation("com.google.guava:guava:31.1-android")
    androidTestImplementation("com.google.code.findbugs:jsr305:3.0.2")
}
// Existing AndroidX Test 1.5 references old lifecycle-common; use the cached JVM variant
// already present in the application graph, without downloading obsolete artifacts.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.lifecycle:lifecycle-common"))
            .using(module("androidx.lifecycle:lifecycle-common-jvm:2.11.0"))
    }
}


val exactSource = tasks.register<Copy>("exactSource") {
 from(appSource) {
  include("com/example/bai1/ui/theme/*.kt")
  include("com/example/bai1/auth/session/AuthModels.kt")
  include("com/example/bai1/auth/session/TokenStorage.kt")
  include("com/example/bai1/auth/network/AuthApiClient.kt")
  include("com/example/bai1/sos/network/SosApiClient.kt")
  include("com/example/bai1/sos/network/SosApiModels.kt")
  include("com/example/bai1/sos/domain/*.kt")
  include("com/example/bai1/sos/transport/SosTransport.kt")
  include("com/example/bai1/sos/transport/ApiSosTransport.kt")
 }
 into(layout.buildDirectory.dir("generated/exact-source"))
}
tasks.named("preBuild").configure { dependsOn(exactSource) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3-android:1.4.0")
    implementation("androidx.compose.material:material-icons-extended-android:1.7.8")
    implementation("androidx.compose.ui:ui-android:1.11.2")
    implementation("androidx.navigation:navigation-compose-android:2.10.1")
}
dependencies {
}

