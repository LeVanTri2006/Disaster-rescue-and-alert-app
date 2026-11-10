import java.util.Properties

plugins {
 id("com.android.application") version "9.4.0"
 id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}
val mapsProperties = Properties().apply {
 rootProject.file("../../local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val authorizedMapsKey = providers.environmentVariable("GMAPS01_API_KEY").orNull
 ?: mapsProperties.getProperty("MAPS_API_KEY")
require(!authorizedMapsKey.isNullOrBlank() && authorizedMapsKey != "PLACEHOLDER") { "Authorized Maps API key is required (value redacted)" }

android {
 namespace = "com.example.bai1.p5e10m02.demo"
 compileSdk = 37
 defaultConfig {
  applicationId = "com.example.bai1.p5e10m02.demo"
  minSdk = 30
  targetSdk = 37
  versionCode = 1
  versionName = "M02-SHARED-SOS"
  manifestPlaceholders["GMAPS01_MAPS_KEY"] = authorizedMapsKey
  testInstrumentationRunner = "com.example.bai1.fixture.IsolatedInstrumentationRunner"
 }
 buildFeatures { compose = true }
 sourceSets.getByName("main").kotlin.directories.add("build/generated/exact-source")
 compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
// Cached Maps/Compose versions plus exact production HTTP/domain copies.
// No google-services plugin or production DI. Key reuse is user-authorized.
dependencies {
 implementation("com.google.maps.android:maps-compose:4.4.1")
 implementation("com.google.android.gms:play-services-maps:19.0.0")
 implementation(platform("androidx.compose:compose-bom:2026.02.01"))
 implementation("androidx.compose.ui:ui-android:1.11.2")
 implementation("androidx.compose.material3:material3-android:1.4.0")
 implementation("com.google.guava:guava:31.1-android") // Existing E10A/main cached graph supplies ListenableFuture.
 implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.10")
 implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.10")
}

// Align only this standalone module to artifacts already cached by the main app.
dependencies {
 constraints {
  implementation("androidx.activity:activity:1.13.0")
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.activity:activity-ktx:1.13.0")
  implementation("androidx.compose.animation:animation-android:1.10.5")
  implementation("androidx.compose.animation:animation-core-android:1.10.5")
  implementation("androidx.compose.foundation:foundation-android:1.10.5")
  implementation("androidx.compose.foundation:foundation-layout-android:1.10.5")
  implementation("androidx.core:core:1.18.0")
  implementation("androidx.core:core-ktx:1.18.0")
  implementation("androidx.lifecycle:lifecycle-livedata:2.11.0")
  implementation("androidx.lifecycle:lifecycle-livedata-core:2.11.0")
  implementation("androidx.lifecycle:lifecycle-livedata-core-ktx:2.11.0")
  implementation("androidx.lifecycle:lifecycle-process:2.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-android:2.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx-android:2.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-android:2.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.11.0")
  implementation("androidx.savedstate:savedstate-android:1.5.0")
  implementation("androidx.savedstate:savedstate-compose-android:1.5.0")
  implementation("androidx.savedstate:savedstate-ktx:1.5.0")
  implementation("com.google.android.gms:play-services-base:18.9.0")
  implementation("com.google.android.gms:play-services-basement:18.9.0")
  implementation("com.google.android.gms:play-services-tasks:18.4.0")
 }
}

dependencies {
 implementation("androidx.activity:activity-compose:1.13.0")
 androidTestImplementation("androidx.test.ext:junit:1.1.5")
 androidTestImplementation("androidx.test:runner:1.5.2")
 androidTestImplementation("androidx.annotation:annotation-experimental:1.4.1")
 androidTestImplementation("com.google.code.findbugs:jsr305:3.0.2")
}
configurations.configureEach {
 resolutionStrategy.dependencySubstitution {
  substitute(module("androidx.lifecycle:lifecycle-common")).using(module("androidx.lifecycle:lifecycle-common-jvm:2.11.0"))
 }
}

val appSource=file("../../app/src/main/java")
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
 implementation("com.squareup.okio:okio:3.9.1")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.google.code.gson:gson:2.10.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
